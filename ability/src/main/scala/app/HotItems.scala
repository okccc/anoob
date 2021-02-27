package app

import java.util.Properties

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.tuple.{Tuple, Tuple1}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.kafka.common.serialization.StringDeserializer

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
    // watermark：表示数据流中timestamp小于watermark的数据都已经达到了,从而触发window执行,是一种延迟触发机制,专门处理乱序数据,用来指示当前处理到什么时刻的数据了
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.读取kafka数据
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")              // kafka地址
    prop.put("group.id", "consumer-group")                       // 消费者组
    prop.put("key.deserializer", classOf[StringDeserializer])    // key反序列化器
    prop.put("value.deserializer", classOf[StringDeserializer])  // value反序列化器
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.转换处理
    // 1).将流数据封装成样例类对象,并提取时间戳生成watermark
    val dataStream: DataStream[UserBehavior] = inputStream
      .map((line: String) => {
        val words: Array[String] = line.split(",")
        UserBehavior(words(0).toLong, words(1).toLong, words(2).toInt, words(3), words(4).toLong)
      })
      // assignAscendingTimestamps(有序)/assignTimestampsAndWatermarks(无序),这样就得到了带有时间标记的数据流,方便后续的窗口操作
      .assignAscendingTimestamps((_: UserBehavior).timestamp * 1000L)
    dataStream.print()  // UserBehavior(543462,1715,1464116,pv,1511658000)

    // 2).按照商品分组,进行聚合
    // 过滤pv行为
    val filterStream: DataStream[UserBehavior] = dataStream.filter((_: UserBehavior).behavior == "pv")
    // 按照商品id分组
    val keyedStream: KeyedStream[UserBehavior, Tuple] = filterStream.keyBy("itemId")
    keyedStream.print("keyed")
    // 设置滑动窗口,窗口长度1hour,滑动间隔5min
    val windowedStream: WindowedStream[UserBehavior, Tuple, TimeWindow] = keyedStream.timeWindow(Time.seconds(4), Time.seconds(2))
    println("aaa = " + windowedStream)
    // 直接聚合(itemId, count)无法判断属于哪个窗口,可以先预聚合来一条处理一条,然后窗口关闭时调用全窗口函数拿到预聚合的状态并添加窗口信息将其
    // 封装成ItemViewCount(itemId, windowEnd, count),得到每个商品在每个窗口的点击量
    val aggStream: DataStream[ItemViewCount] = windowedStream.aggregate(new CountAggregate(), new ItemViewWindow())
    aggStream.print("agg")

    // 3).按照窗口分组,进行聚合
//    aggStream
//      .keyBy("windowEnd")  // 按照窗口分组,收集当前窗口内商品的count值
//      .process(new TopNHotItems(5))  // 自定义处理流程

    // 4.输出操作

    // 5.启动任务
    env.execute("hot items")
  }
}

// 自定义增量聚合函数,聚合状态就是当前商品的count值
class CountAggregate() extends AggregateFunction[UserBehavior, Long, Long] {
  override def createAccumulator(): Long = 0L
  // 来一条数据调用一次add方法,count值+1
  override def add(value: UserBehavior, accumulator: Long): Long = accumulator + 1

  override def getResult(accumulator: Long): Long = accumulator

  override def merge(a: Long, b: Long): Long = a + b

}

// 自定义全窗口函数
class ItemViewWindow() extends WindowFunction[Long, ItemViewCount, Tuple, TimeWindow] {
  /*
   * WindowFunction[IN, OUT, KEY, W <: Window]
   * IN:  输入的是前面的预聚合结果,累加器类型Long
   * OUT: 输出的是窗口聚合的结果样例类ItemViewCount(itemId, windowEnd, count),windowEnd是窗口的结束时间,也是窗口的唯一标识
   * KEY: 这里的key就是itemId,是Tuple类型(itemId, count)
   * W:   时间窗口
   */
  override def apply(key: Tuple, window: TimeWindow, input: Iterable[Long], out: Collector[ItemViewCount]): Unit = {
    println("key = " + key)
    // Tuple是一个java抽象类,并且这里的key只有itemId一个元素,需通过其子类Tuple1实现
    val itemId: Long = key.asInstanceOf[Tuple1[Long]].f0
    val windowEnd: Long = window.getEnd
    val count: Long = input.iterator.next()
    out.collect(ItemViewCount(itemId, windowEnd, count))
  }
}

// 自定义处理函数
class TopNHotItems(i: Int) extends KeyedProcessFunction[Tuple, ItemViewCount, String] {
  override def processElement(value: ItemViewCount, ctx: KeyedProcessFunction[Tuple, ItemViewCount, String]#Context, out: Collector[String]): Unit = {

  }
}