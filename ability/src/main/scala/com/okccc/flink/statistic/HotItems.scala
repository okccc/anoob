package com.okccc.flink.statistic

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
 * Desc: 实时统计1小时内的热门商品,5分钟刷新一次
 */

// 定义输入数据样例类
case class UserBehavior(userId: Long, itemId: Long, categoryId: Int, behavior: String, timestamp: Long)
// 定义输出窗口聚合结果的样例类
case class HotItemCount(itemId: Long, windowEnd: Long, count: Int)

object HotItems {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度
    env.setParallelism(1)
    // 时间语义：EventTime事件创建时间(常用) | IngestionTime数据进入flink时间 | ProcessingTime执行基于时间操作的算子的机器的本地时间
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.Source操作
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")              // kafka地址
    prop.put("group.id", "consumer-group")                       // 消费者组
    prop.put("key.deserializer", classOf[StringDeserializer])    // key反序列化器
    prop.put("value.deserializer", classOf[StringDeserializer])  // value反序列化器
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.Transform操作
    // 1).将流数据封装成样例类对象,并提取时间戳生成watermark,方便后续窗口操作
    val dataStream: DataStream[UserBehavior] = inputStream
      .map((line: String) => {
        // 662708,1177318,4756105,pv,1511690399
        val words: Array[String] = line.split(",")
        UserBehavior(words(0).toLong, words(1).toLong, words(2).toInt, words(3), words(4).toLong)
      })
      // 数据本身有序：不用设置watermark
      .assignAscendingTimestamps((_: UserBehavior).timestamp * 1000L)
      // 数据本身无序(通用情况)：要设置watermark,创建有界的乱序时间戳提取器,传入时间参数(毫秒)控制乱序程度,具体时间根据数据的实际乱序程度设置
//      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[UserBehavior](Time.milliseconds(30)) {
//        override def extractTimestamp(element: UserBehavior): Long = element.timestamp * 1000L  // 注意如果单位是秒要*1000
//      })
//    dataStream.print("data")  // data> UserBehavior(543462,1715,1464116,pv,1511658000)

    // 2).将DataStream[UserBehavior]按照itemId分组聚合
    val filterStream: DataStream[UserBehavior] = dataStream.filter((_: UserBehavior).behavior == "pv")
    val itemIdStream: KeyedStream[UserBehavior, Tuple] = filterStream.keyBy("itemId")
//    itemIdStream.print("keyed")
    // 分配窗口tumbling window/sliding window,有刷新频率的就是滑动窗口,一个EventTime可以属于窗口大小(1hour)/滑动间隔(5min)=12个窗口
    val windowedStream: WindowedStream[UserBehavior, Tuple, TimeWindow] = itemIdStream.timeWindow(Time.hours(1), Time.minutes(5))
    // AggregateFunction是一个底层通用函数,直接聚合(itemId,count)无法判断属于哪个窗口,需结合WindowFunction使用,先来一条处理一条做增量聚合
    // 等窗口关闭时再调用窗口函数,拿到预聚合状态并添加窗口信息封装成ItemViewCount(itemId, windowEnd, count)得到每个商品在每个窗口的点击量
    val aggStream: DataStream[HotItemCount] = windowedStream.aggregate(new HotItemCountAgg(), new HotItemCountWindow())
//    aggStream.print("agg")

    // 3).将DataStream[HotItemCount]按照windowEnd分组聚合
    val windowEndStream: KeyedStream[HotItemCount, Tuple] = aggStream.keyBy("windowEnd")
    // 涉及状态编程和定时器,ProcessFunction是flink最底层api,属于终极大招,可以自定义功能实现所有需求,最常用的是KeyedProcessFunction
    val resultDataStream: DataStream[String] = windowEndStream.process(new TopNHotItems(5))

    // 4.sink操作
    resultDataStream.print("topN")

    // 5.启动任务
    env.execute("hot items")
  }
}

// 自定义预聚合函数
class HotItemCountAgg() extends AggregateFunction[UserBehavior, Int, Int] {
  override def createAccumulator(): Int = 0
  // 聚合状态就是当前商品的count值,来一条数据调用一次add方法,count值+1
  override def add(value: UserBehavior, accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = a + b
}

// 自定义平均数函数
class AvgAgg extends AggregateFunction[UserBehavior, (Long, Int), Long]{
  override def add(value: UserBehavior, accumulator: (Long, Int)): (Long, Int) = (accumulator._1 + value.timestamp, accumulator._2 + 1)
  override def createAccumulator(): (Long, Int) = (0L, 0)
  override def getResult(accumulator: (Long, Int)): Long = accumulator._1 / accumulator._2
  override def merge(a: (Long, Int), b: (Long, Int)): (Long, Int) = (a._1 + b._1, a._2 + b._2)
}

/*
 * 自定义窗口函数
 * trait WindowFunction[IN, OUT, KEY, W <: Window]
 * IN:  输入的是前面的预聚合结果
 * OUT: 输出的是窗口聚合的结果样例类ItemViewCount(itemId, windowEnd, count)
 * KEY: 这里的key就是分组字段itemId,得到的是JavaTuple类型
 * W:   时间窗口,W <: Window表示上边界,即W类型必须是Window类型或其子类
 */
class HotItemCountWindow() extends WindowFunction[Int, HotItemCount, Tuple, TimeWindow] {
  override def apply(key: Tuple, window: TimeWindow, input: Iterable[Int], out: Collector[HotItemCount]): Unit = {
    // Tuple是一个java抽象类,并且这里的key只有itemId一个元素,可通过其子类Tuple1实现
    val itemId: Long = key.asInstanceOf[Tuple1[Long]].f0
    // 获取时间窗口,windowEnd是窗口的结束时间,也是窗口的唯一标识
    val windowEnd: Long = window.getEnd
    // 迭代器里只有一个元素,就是count值
    val count: Int = input.iterator.next()
    // 收集结果封装成样例类对象
    out.collect(HotItemCount(itemId, windowEnd, count))
  }
}

/*
 * 自定义处理函数
 * abstract class KeyedProcessFunction<K, I, O>
 * K: 分组字段类型,windowEnd
 * I: 输入元素类型,HotPageCount
 * O: 输出元素类型,这里拼接成字符串展示
 */
class TopNHotItems(i: Int) extends KeyedProcessFunction[Tuple, HotItemCount, String] {
  /*
   * flink所有函数都有其对应的Rich版本,Rich函数和Process函数都继承自RichFunction接口,提供了三个特有方法
   * getRuntimeContext(运行环境上下文)/open(生命周期初始化,比如创建数据库连接)/close(生命周期结束,比如关闭数据库连接、清空状态等)
   * KeyedState常用数据结构：ValueState(单值状态)/ListState(列表状态)/MapState(键值对状态)/ReducingState & AggregatingState(聚合状态)
   */

  // 先定义状态：每个窗口都应该有一个ListState来保存当前窗口内所有商品对应的count值,ListState接口体系包含add/update/get/clear等方法
  var itemViewCountListState: ListState[HotItemCount] = _
  // flink状态是从运行时上下文中获取,在类中直接调用getRuntimeContext是不生效的,要等到类初始化时才能调用,所以获取状态的操作要放在open方法中
  // 或者采用懒加载模式,lazy只是先声明变量,等到使用这个状态时才会做赋值操作,processElement方法使用状态的时候肯定已经能getRuntimeContext了
  override def open(parameters: Configuration): Unit = {
    itemViewCountListState = getRuntimeContext.getListState(new ListStateDescriptor[HotItemCount]("itemViewCount-list", classOf[HotItemCount]))
  }

  /**
   * 处理所有ItemViewCount
   * @param value 输入元素
   * @param ctx KeyedProcessFunction的内部类Context,提供了流的一些上下文信息,包括当前处理元素的时间戳和key、注册定时服务、写数据到侧输出流
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素
   */
  override def processElement(value: HotItemCount, ctx: KeyedProcessFunction[Tuple, HotItemCount, String]#Context, out: Collector[String]): Unit = {
    // 每来一条数据都添加到状态
    itemViewCountListState.add(value)
    // 注册一个windowEnd + 1(ms)之后触发的定时器
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 1)
  }

  /**
   * 触发定时器时调用
   * @param timestamp 定时器设定的触发时间戳,就是processElement方法里设定的windowEnd + 1
   * @param ctx KeyedProcessFunction的内部类OnTimerContext,提供了流的一些上下文信息,包括当前定时器的时间域和key
   * @param out Collector接口提供了collect方法收集输出结果,可以输出0个或多个元素
   */
  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[Tuple, HotItemCount, String]#OnTimerContext, out: Collector[String]): Unit = {
    // ListState本身没有排序功能,需额外定义ListBuffer存放ListState中的数据
    val itemViewCounts: ListBuffer[HotItemCount] = new ListBuffer[HotItemCount]
    // 获取ListState的迭代器
    val iterator: util.Iterator[HotItemCount] = itemViewCountListState.get().iterator()
    // 遍历迭代器
    while (iterator.hasNext) {
      // 将ListState中的数据添加到ListBuffer
      itemViewCounts += iterator.next()
    }
    // 此时状态已经不需要了,提前清除状态,节约内存空间
    itemViewCountListState.clear()

    // 按count值排序取前n个,默认升序,sortBy().reverse相当于排序两次,sortBy()(Ordering.Long.reverse)使用柯里化方式隐式转换,只排序一次
    val sortedItemViewCounts: ListBuffer[HotItemCount] = itemViewCounts.sortBy((i: HotItemCount) => i.count)(Ordering.Int.reverse).take(i)

    // 将排名信息拼接成字符串输出展示
    val sb: StringBuilder = new StringBuilder
    sb.append("窗口结束时间：").append(new Timestamp(timestamp - 1)).append("\n")
    // 遍历当前窗口的结果列表中的每个ItemViewCount,输出到一行
    for (i <- sortedItemViewCounts.indices) {
      val currentItemViewCount: HotItemCount = sortedItemViewCounts(i)
      sb.append("NO").append(i + 1).append(": ")
        .append("商品ID=").append(currentItemViewCount.itemId).append("\t")
        .append("热度=").append(currentItemViewCount.count).append("\n")
    }
    sb.append("\n==================================\n")
    // 输出一个窗口后停1秒
    Thread.sleep(1000)

    // 收集结果
    out.collect(sb.toString())
  }
}