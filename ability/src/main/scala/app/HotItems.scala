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
case class ItemViewCount(itemId: Long, windowEnd: Long, count: Int)

object HotItems {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度
    env.setParallelism(1)
    // 时间语义：EventTime事件创建时间(常用) | IngestionTime数据进入flink时间 | ProcessingTime执行基于时间操作的算子的机器的本地时间
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
      // 乱序：流处理过程是event - source - operator,由于网络和分布式等原因会导致乱序,即flink接收到的event并不是严格按照EventTime顺序排列
      // watermark：表示数据流中timestamp小于watermark的数据都已到达从而触发window执行,是一种延迟触发机制,专门处理乱序数据,用来指示当前事件时间
      // 数据本身有序：不用设置watermark
      .assignAscendingTimestamps((_: UserBehavior).timestamp * 1000L)
      // 数据本身无序(通用情况)：要设置watermark,创建有界的乱序时间戳提取器,传入时间参数(毫秒)控制乱序程度,具体时间根据数据的实际乱序程度设置
//      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[UserBehavior](Time.milliseconds(30)) {
//        override def extractTimestamp(element: UserBehavior): Long = element.timestamp * 1000L  // 注意如果单位是秒要*1000
//      })
    dataStream.print("data")  // UserBehavior(543462,1715,1464116,pv,1511658000)

    // 2).按照商品分组聚合
    // 过滤pv行为
    val filterStream: DataStream[UserBehavior] = dataStream.filter((_: UserBehavior).behavior == "pv")
    // 按照商品id分组
    val itemIdStream: KeyedStream[UserBehavior, Tuple] = filterStream.keyBy("itemId")
    itemIdStream.print("keyed")
    // 设置滑动窗口,窗口长度1hour,滑动间隔5min
    val windowedStream: WindowedStream[UserBehavior, Tuple, TimeWindow] = itemIdStream.timeWindow(Time.hours(1), Time.minutes(5))
    // AggregateFunction是一个底层通用函数,直接聚合(itemId,count)无法判断属于哪个窗口,需结合WindowFunction使用,先来一条处理一条做增量聚合
    // 等窗口关闭时再调用全窗口函数,拿到预聚合状态并添加窗口信息封装成ItemViewCount(itemId, windowEnd, count)得到每个商品在每个窗口的点击量
    val aggStream: DataStream[ItemViewCount] = windowedStream.aggregate(new ItemCountAgg(), new ItemViewCountWindow())
    aggStream.print("agg")

    // 3).按照窗口分组聚合
    val windowEndStream: KeyedStream[ItemViewCount, Tuple] = aggStream.keyBy("windowEnd")
    // 因为要用到状态编程和定时器,ProcessFunction是flink最底层api,属于终极大招,可以自定义功能实现所有需求,最常用的是KeyedProcessFunction
    val resultDataStream: DataStream[String] = windowEndStream.process(new TopNHotItems(5))
    resultDataStream.print("topN")

    // 4.启动任务
    env.execute("hot items")
  }
}

// 自定义增量聚合函数
class ItemCountAgg() extends AggregateFunction[UserBehavior, Int, Int] {
  override def createAccumulator(): Int = 0
  // 聚合状态就是当前商品的count值,来一条数据调用一次add方法,count值+1
  override def add(value: UserBehavior, accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = a + b
}

/*
 * 自定义全窗口函数
 * trait WindowFunction[IN, OUT, KEY, W <: Window]
 * IN:  输入的是前面的预聚合结果
 * OUT: 输出的是窗口聚合的结果样例类ItemViewCount(itemId, windowEnd, count)
 * KEY: 这里的key就是分组字段itemId,得到的是JavaTuple类型
 * W:   时间窗口,W <: Window表示上边界,即W类型必须是Window类型或其子类
 */
class ItemViewCountWindow() extends WindowFunction[Int, ItemViewCount, Tuple, TimeWindow] {
  override def apply(key: Tuple, window: TimeWindow, input: Iterable[Int], out: Collector[ItemViewCount]): Unit = {
    println("key = " + key)
    // Tuple是一个java抽象类,并且这里的key只有itemId一个元素,可通过其子类Tuple1实现
    val itemId: Long = key.asInstanceOf[Tuple1[Long]].f0
    // 获取时间窗口,windowEnd是窗口的结束时间,也是窗口的唯一标识
    val windowEnd: Long = window.getEnd
    // 迭代器里只有一个元素,就是count值
    val count: Int = input.iterator.next()
    // 收集结果封装成样例类对象
    out.collect(ItemViewCount(itemId, windowEnd, count))
  }
}

// 自定义处理函数
// 所有ProcessFunction都继承自RichFunction接口,都有open/close/getRuntimeContext方法,KeyedProcessFunction还提供了processElement和onTimer方法
// KeyedState常用数据结构：ValueState(单值状态)/ListState(列表状态)/MapState(键值对状态)/ReducingState & AggregatingState(聚合状态)
// ListState接口体系包含add/addAll/update/get/clear等方法
class TopNHotItems(i: Int) extends KeyedProcessFunction[Tuple, ItemViewCount, String] {
  // 先定义状态：每个窗口都应该有一个ListState来保存当前窗口内所有商品对应的count值
  var itemViewCountListState: ListState[ItemViewCount] = _

  // 初始化方法,从运行上下文获取当前流的状态
  override def open(parameters: Configuration): Unit = {
    itemViewCountListState = getRuntimeContext.getListState(new ListStateDescriptor[ItemViewCount]("itemViewCount-list", classOf[ItemViewCount]))
  }

  /**
   * 处理输入流中的每个元素
   * @param value 输入元素
   * @param ctx KeyedProcessFunction的内部类Context,提供了流的一些上下文信息,包括当前处理元素的时间戳和key、注册定时服务、侧输出流
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素
   */
  override def processElement(value: ItemViewCount, ctx: KeyedProcessFunction[Tuple, ItemViewCount, String]#Context, out: Collector[String]): Unit = {
    // 每来一条数据都添加到状态中
    itemViewCountListState.add(value)
    // 注册一个windowEnd + 1(ms)之后触发的定时器,比如9点延迟1毫秒后到达watermark,9点的数据肯定都到齐了,9点之前的窗口也都关闭了
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 1)
  }

  /**
   * 触发定时器时调用
   * @param timestamp 定时器设定的触发时间戳,就是processElement方法里设定的windowEnd + 1
   * @param ctx KeyedProcessFunction的内部类OnTimerContext,提供了流的一些上下文信息,包括当前定时器的时间域和key
   * @param out Collector接口提供了collect方法收集输出结果
   */
  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[Tuple, ItemViewCount, String]#OnTimerContext, out: Collector[String]): Unit = {
    // ListState本身没有排序功能,需额外定义一个ListBuffer保存ListState的所有数据
    val itemViewCounts: ListBuffer[ItemViewCount] = new ListBuffer[ItemViewCount]
    // 获取ListState的迭代器
    val iterator: util.Iterator[ItemViewCount] = itemViewCountListState.get().iterator()
    // 遍历迭代器
    while (iterator.hasNext) {
      // 将ListState中的数据添加到ListBuffer
      itemViewCounts += iterator.next()
    }
    // 清除状态,节约内存空间
    itemViewCountListState.clear()
    // 按count值排序取前n个,默认升序,sortBy().reverse相当于排序两次,sortBy()(Ordering.Long.reverse)使用柯里化方式隐式转换,只排序一次
    val sortedItemViewCounts: ListBuffer[ItemViewCount] = itemViewCounts.sortBy((i: ItemViewCount) => i.count)(Ordering.Int.reverse).take(i)

    // 将排名信息拼接成字符串输出展示
    val sb: StringBuilder = new StringBuilder
    sb.append("窗口结束时间：").append(new Timestamp(timestamp - 1)).append("\n")
    // 遍历当前窗口的结果列表中的每个ItemViewCount,输出到一行
    for (i <- sortedItemViewCounts.indices) {
      val itemViewCount: ItemViewCount = sortedItemViewCounts(i)
      sb.append("NO").append(i + 1).append(":\t")
        .append("商品ID = ").append(itemViewCount.itemId).append("\t")
        .append("热度 = ").append(itemViewCount.count).append("\n")
    }
    sb.append("\n==================================\n")
    // 输出一个窗口后停一下
    Thread.sleep(1000)

    // 收集结果
    out.collect(sb.toString())
  }
}