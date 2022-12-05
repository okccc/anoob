package com.okccc.demo

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.kafka.common.serialization.StringDeserializer

import java.sql.Timestamp
import java.time.Duration
import java.util
import java.util.Properties
import scala.collection.mutable.ListBuffer

/**
 * Author: okccc
 * Date: 2021/2/23 11:16 上午
 * Desc: 实时统计1小时内的热门商品排名,5分钟刷新一次(有序数据)
 */

// 输入数据样例类
case class UserBehavior(userId: String, itemId: String, categoryId: String, behavior: String, timestamp: Long)
// 输出结果样例类
case class ItemViewCount(itemId: String, windowStart: Long, windowEnd: Long, count: Int)

object HotItems {
  def main(args: Array[String]): Unit = {
    // 创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度
    env.setParallelism(1)

    // 获取数据源
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")              // kafka地址
    prop.put("group.id", "consumer-group")                       // 消费者组
    prop.put("key.deserializer", classOf[StringDeserializer])    // key反序列化器
    prop.put("value.deserializer", classOf[StringDeserializer])  // value反序列化器
    env
      .addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))
      // 将流数据封装成样例类
      .map((line: String) => {
        // 662708,1177318,4756105,pv,1511690399
        val words: Array[String] = line.split(",")
        UserBehavior(words(0), words(1), words(2), words(3), words(4).toLong * 1000)
      })
      .filter((u: UserBehavior) => u.behavior == "pv")
      // 提取时间戳生成水位线
      .assignTimestampsAndWatermarks(
        // 有序数据不用设置延迟时间
        WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(0))
          .withTimestampAssigner(new SerializableTimestampAssigner[UserBehavior] {
            override def extractTimestamp(element: UserBehavior, recordTimestamp: Long): Long =
              element.timestamp
          })
      )
      // 按照商品分组
      .keyBy((u: UserBehavior) => u.itemId)
      // 开窗,有刷新频率就是滑动窗口
      .window(SlidingEventTimeWindows.of(Time.hours(1), Time.minutes(5)))
      // 统计每个商品在每个窗口的访问量
      .aggregate(new ItemCountAgg(), new ItemWindowResult())
      // 按照窗口分组
      .keyBy((i: ItemViewCount) => i.windowStart)
      // 对窗口内所有商品的访问量排序取前3
      .process(new ItemTopN(3))
      .print("topN")

    // 启动任务
    env.execute()
  }
}

// 自定义预聚合函数
class ItemCountAgg() extends AggregateFunction[UserBehavior, Int, Int] {
  override def createAccumulator(): Int = 0
  override def add(value: UserBehavior, accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = 0
}

// 自定义平均数函数
class AvgAgg extends AggregateFunction[UserBehavior, (Long, Int), Long]{
  override def add(value: UserBehavior, accumulator: (Long, Int)): (Long, Int) = (accumulator._1 + value.timestamp, accumulator._2 + 1)
  override def createAccumulator(): (Long, Int) = (0L, 0)
  override def getResult(accumulator: (Long, Int)): Long = accumulator._1 / accumulator._2
  override def merge(a: (Long, Int), b: (Long, Int)): (Long, Int) = (a._1 + b._1, a._2 + b._2)
}

// 自定义窗口处理函数
class ItemWindowResult() extends ProcessWindowFunction[Int, ItemViewCount, String, TimeWindow] {
  override def process(key: String, context: Context, elements: Iterable[Int], out: Collector[ItemViewCount]): Unit = {
    out.collect(ItemViewCount(key, context.window.getStart, context.window.getEnd, elements.iterator.next()))
  }
}

// 自定义处理函数
class ItemTopN(n: Int) extends KeyedProcessFunction[Long, ItemViewCount, String] {
  // 因为涉及排序操作,所以要收集流中所有元素
  var listState: ListState[ItemViewCount] = _
  // flink状态是从运行时上下文中获取,在类中直接调用getRuntimeContext是不生效的,要等到类初始化时才能调用,所以获取状态的操作要放在open方法中
  // 或者采用懒加载模式,lazy只是先声明变量,等到使用这个状态时才会做赋值操作,processElement方法使用状态的时候肯定已经能getRuntimeContext了
  override def open(parameters: Configuration): Unit = {
    listState = getRuntimeContext.getListState(
      new ListStateDescriptor[ItemViewCount]("listState", classOf[ItemViewCount])
    )
  }

  override def processElement(value: ItemViewCount, ctx: KeyedProcessFunction[Long, ItemViewCount, String]#Context, out: Collector[String]): Unit = {
    // 每来一条数据都添加到状态
    listState.add(value)
    // 注册一个windowEnd + 1(ms)之后触发的定时器
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 1)
  }

  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[Long, ItemViewCount, String]#OnTimerContext, out: Collector[String]): Unit = {
    // ListState本身没有排序功能,需额外定义ListBuffer存放ListState中的数据
    val itemViewCounts: ListBuffer[ItemViewCount] = new ListBuffer[ItemViewCount]
    // 获取ListState的迭代器
    val iterator: util.Iterator[ItemViewCount] = listState.get().iterator()
    // 遍历迭代器
    while (iterator.hasNext) {
      // 将ListState中的数据添加到ListBuffer
      itemViewCounts += iterator.next()
    }
    // 此时状态已经不需要了,提前清除状态,节约内存空间
    listState.clear()
    // 按count值排序取前n个,默认升序,sortBy().reverse相当于排序两次,sortBy()(Ordering.Long.reverse)使用柯里化方式隐式转换,只排序一次
    val topN: ListBuffer[ItemViewCount] = itemViewCounts.sortBy((i: ItemViewCount) => i.count)(Ordering.Int.reverse).take(n)
    // 窗口信息
    val windowEnd: Long = timestamp - 1
    val windowStart: Long = windowEnd - 3600 * 1000
    // 输出结果
//    for (i <- topN.indices) {
//      val ivc: ItemViewCount = topN(i)
//      out.collect("窗口区间：" + new Timestamp(windowStart) + " ~ " + new Timestamp(windowEnd) +
//        " NO " + (i + 1) + " 的商品 " + ivc.itemId + " 访问次数 " + ivc.count)
//    }

    // 将结果拼接成字符串输出展示
    val sb: StringBuilder = new StringBuilder
    sb.append("========================================\n")
    sb.append("窗口区间：" + new Timestamp(windowStart) + " ~ " + new Timestamp(windowEnd) + "\n")
    for (i <- topN.indices) {
      val ivc: ItemViewCount = topN(i)
      sb.append("NO " + (i + 1) + " 的商品 " + ivc.itemId + " 访问次数 " + ivc.count + "\n")
    }
    // 收集结果往下游发送
    out.collect(sb.toString())
  }
}