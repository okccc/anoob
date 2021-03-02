package app

import java.sql.Timestamp
import java.util
import java.util.Properties

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.api.java.tuple.{Tuple, Tuple1}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.kafka.common.serialization.StringDeserializer

import scala.collection.mutable.ListBuffer

/**
 * Author: okccc
 * Date: 2021/2/23 11:16 上午
 * Desc: 实时统计一小时内的热门商品,5分钟刷新一次
 */

// 定义输入数据的样例类
case class UserBehavior(userId: Long, itemId: Long, categoryId: Int, behavior: String, timestamp: Long)
// 定义窗口聚合结果的样例类
case class ItemViewCount(itemId: Long, windowEnd: Long, count: Long)

object HotItems {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度
    env.setParallelism(1)
    // 时间语义：EventTime事件创建时间(常用) | IngestionTime数据进入flink时间 | ProcessingTime执行基于时间操作的算子的机器的本地时间
    // 乱序：流处理过程是event - source - operator,由于网络和分布式等原因会导致乱序,即flink接收到的event并不是严格按照EventTime顺序排列
    // watermark：表示数据流中timestamp小于watermark的数据都已经达到了,从而触发window执行,是一种延迟触发机制,专门处理乱序数据,用来指示当前事件时间
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.读取kafka数据
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")              // kafka地址
    prop.put("group.id", "consumer-group")                       // 消费者组
    prop.put("key.deserializer", classOf[StringDeserializer])    // key反序列化器
    prop.put("value.deserializer", classOf[StringDeserializer])  // value反序列化器
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.转换处理
    // 1).将流数据封装成样例类对象,并提取时间戳生成watermark,方便后续窗口操作
    val dataStream: DataStream[UserBehavior] = inputStream
      .map((line: String) => {
        val words: Array[String] = line.split(",")
        UserBehavior(words(0).toLong, words(1).toLong, words(2).toInt, words(3), words(4).toLong)
      })
      // 数据本身有序：不用设置watermark
      .assignAscendingTimestamps((_: UserBehavior).timestamp * 1000L)
      // 数据本身无序(通用情况)：要设置watermark,创建有界的乱序时间戳提取器,传入时间参数(毫秒)控制乱序程度
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[UserBehavior](Time.milliseconds(30)) {
        override def extractTimestamp(element: UserBehavior): Long = element.timestamp * 1000L
      })
    dataStream.print()  // UserBehavior(543462,1715,1464116,pv,1511658000)

    // 2).按照商品分组聚合
    // 过滤pv行为
    val filterStream: DataStream[UserBehavior] = dataStream.filter((_: UserBehavior).behavior == "pv")
    // 按照商品id分组
    val itemIdStream: KeyedStream[UserBehavior, Tuple] = filterStream.keyBy("itemId")
    itemIdStream.print("keyed")
    // 设置滑动窗口,窗口长度1hour,滑动间隔5min
    val windowedStream: WindowedStream[UserBehavior, Tuple, TimeWindow] = itemIdStream.timeWindow(Time.hours(1), Time.minutes(5))
    println("aaa = " + windowedStream)
    // AggregateFunction是一个底层通用聚合函数(itemId, count)无法判断属于哪个窗口,需结合WindowFunction使用,先来一条处理一条做增量聚合
    // 窗口关闭时再调用全窗口函数,拿到预聚合状态并添加窗口信息封装成ItemViewCount(itemId, windowEnd, count)得到每个商品在每个窗口的点击量
    val aggStream: DataStream[ItemViewCount] = windowedStream.aggregate(new CountAggregateFunction(), new ItemViewWindowFunction())
    aggStream.print("agg")

    // 3).按照窗口分组聚合
    val windowEndStream: KeyedStream[ItemViewCount, Tuple] = aggStream.keyBy("windowEnd")
    // 此处要用到状态编程和定时器,ProcessFunction是flink最底层api,属于终极大招,可以自定义功能实现所有需求,最常用的是KeyedProcessFunction
    windowEndStream.process(new TopNHotItems(5))

    // 4.启动任务
    env.execute("hot items")
  }
}

// 自定义增量聚合函数
class CountAggregateFunction() extends AggregateFunction[UserBehavior, Long, Long] {
  /*
   * interface AggregateFunction<IN, ACC, OUT>
   * IN:  The type of the values that are aggregated (input values)
   * ACC: The type of the accumulator (intermediate aggregate state)
   * OUT: The type of the aggregated result
   */
  override def createAccumulator(): Long = 0L
  // 聚合状态就是当前商品的count值,来一条数据调用一次add方法,count值+1
  override def add(value: UserBehavior, accumulator: Long): Long = accumulator + 1

  override def getResult(accumulator: Long): Long = accumulator

  override def merge(a: Long, b: Long): Long = a + b

}

// 自定义全窗口函数
class ItemViewWindowFunction() extends WindowFunction[Long, ItemViewCount, Tuple, TimeWindow] {
  /*
   * trait WindowFunction[IN, OUT, KEY, W <: Window]
   * IN:  输入的是前面的预聚合结果,累加器类型Long
   * OUT: 输出的是窗口聚合的结果样例类ItemViewCount(itemId, windowEnd, count),windowEnd是窗口的结束时间,也是窗口的唯一标识
   * KEY: 这里的key就是itemId,是Tuple类型(itemId)
   * W:   时间窗口
   */
  override def apply(key: Tuple, window: TimeWindow, input: Iterable[Long], out: Collector[ItemViewCount]): Unit = {
    println("key = " + key)
    // Tuple是一个java抽象类,并且这里的key只有itemId一个元素,可通过其子类Tuple1实现
    val itemId: Long = key.asInstanceOf[Tuple1[Long]].f0
    // 获取时间窗口
    val windowEnd: Long = window.getEnd
    // 迭代器里只有一个元素,就是count值
    val count: Long = input.iterator.next()
    // 收集结果封装成样例类对象
    out.collect(ItemViewCount(itemId, windowEnd, count))
  }
}

// 自定义处理函数
// 所有ProcessFunction类都继承自RichFunction接口,都有open/close/getRuntimeContext方法
// KeyedProcessFunction还提供了processElement和onTimer方法
class TopNHotItems(i: Int) extends KeyedProcessFunction[Tuple, ItemViewCount, String] {
  // 先定义状态：每一个窗口都应该有一个ListState保存当前窗口内所有商品对应的count值
  var itemViewCountListState: ListState[ItemViewCount] = _

  // 在open生命周期里面定义
  override def open(parameters: Configuration): Unit = {
    itemViewCountListState = getRuntimeContext.getListState(new ListStateDescriptor[ItemViewCount]("itemViewCount-list", classOf[ItemViewCount]))
  }

  // 处理数据流中的所有元素
  override def processElement(value: ItemViewCount, ctx: KeyedProcessFunction[Tuple, ItemViewCount, String]#Context, out: Collector[String]): Unit = {
    // 每来一条数据直接加上ListState
    itemViewCountListState.add(value)
    // 注册一个windowEnd + 1(ms)之后触发的定时器,数据按照windowEnd分组,所以相同的windowEnd使用的是同一个定时器
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 1)
  }

  // 当触发定时器时,可以认为所有窗口统计结果都已到齐,可以排序输出了
  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[Tuple, ItemViewCount, String]#OnTimerContext, out: Collector[String]): Unit = {
    // ListState本身无法排序,可以定义一个ListBuffer保存ListState的所有数据
    val allItemViewCounts: ListBuffer[ItemViewCount] = new ListBuffer[ItemViewCount]
    // 获取ListState的迭代器
    val iterator: util.Iterator[ItemViewCount] = itemViewCountListState.get().iterator()
    // 遍历迭代器
    while (iterator.hasNext) {
      // 将ListState中的数据添加到ListBuffer
      allItemViewCounts += iterator.next()
    }
    // 清除状态,节约内存空间
    itemViewCountListState.clear()
    // 按照count值排序,取前n个
    val sortedItemViewCounts: ListBuffer[ItemViewCount] = allItemViewCounts.sortBy((i: ItemViewCount) => i.count)(Ordering.Long.reverse).take(i)
    // 将排名信息格式化成字符串输出展示,timestamp就是上面windowEnd + 1那个时间,用Timestamp将Long类型的时间戳包装一下
    val sb: StringBuilder = new StringBuilder
    sb.append("窗口结束时间：").append(new Timestamp(timestamp - 1)).append("\n")
    // 遍历当前窗口的结果列表中的每个ItemViewCount,输出到一行
    for (i <- sortedItemViewCounts.indices) {
      val itemViewCount: ItemViewCount = sortedItemViewCounts(i)
      sb.append("NO").append(i + 1).append(":\t")
        .append("商品ID = ").append(itemViewCount.itemId).append("\t")
        .append("热度 = ").append(itemViewCount.count).append("\n")
    }
    sb.append("\n==================================\n\n")
    // 窗口关闭会输出统计结果,输出一次窗口后停一下
    Thread.sleep(1000)
    out.collect(sb.toString())
  }
}