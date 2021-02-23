package app

import java.lang
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
 * Desc: 热门商品统计
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
    // watermark：表示数据流中timestamp小于watermark的数据都已经达到了,从而触发window执行,是一种延迟触发机制,专门处理乱序数据
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.读取kafka数据
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")              // kafka地址
    prop.put("group.id", "consumer-group")                       // 消费者组
    prop.put("key.deserializer", classOf[StringDeserializer])    // key反序列化器
    prop.put("value.deserializer", classOf[StringDeserializer])  // value反序列化器
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.转换操作
    val dataStream: DataStream[UserBehavior] = inputStream
      // 将数据封装成样例类对象
      .map((line: String) => {
        val words: Array[String] = line.split(",")
        UserBehavior(words(0).toLong, words(1).toLong, words(2).toInt, words(3), words(4).toLong)
      })
      // 分配升序时间戳
      .assignAscendingTimestamps((userBehavior: UserBehavior) => userBehavior.timestamp * 1000L)

    // 进行窗口聚合
    val aggStream: DataStream[ItemViewCount] = dataStream
      .filter((ub: UserBehavior) => ub.behavior == "pv") // 过滤pv行为
      .keyBy("itemId") // 按照商品id分组
      .timeWindow(Time.hours(1), Time.minutes(5)) // 设置滑动窗口,窗口长度1hour,滑动间隔5min
      .aggregate(new CountAgg(), new ItemViewWindowResult())       // 对window和key应用给定的聚合函数

    aggStream
      .keyBy("windowEnd")  // 按照窗口分组,收集当前窗口内商品的count值
      .process(new TopNHotItems(5))  // 自定义处理流程

    // 4.输出操作
  }
}

// 自定义预聚合函数AggregateFunction,聚合状态就是当前商品的count值
class CountAgg() extends AggregateFunction[UserBehavior, Long, Long] {
  override def createAccumulator(): Long = 0L
  // 来一条数据调用一次add方法,count + 1
  override def add(value: UserBehavior, accumulator: Long): Long = accumulator + 1

  override def getResult(accumulator: Long): Long = accumulator

  override def merge(a: Long, b: Long): Long = a + b
}

// 自定义窗口函数WindowFunction
class ItemViewWindowResult() extends WindowFunction[Long, ItemViewCount, Tuple, TimeWindow] {
  override def apply(key: Tuple, window: TimeWindow, input: Iterable[Long], out: Collector[ItemViewCount]): Unit = {
    val itemId: Long = key.asInstanceOf[Tuple1[Long]].f0
    val windowEnd: Long = window.getEnd
    val count: Long = input.iterator.next()
    out.collect(ItemViewCount(itemId, windowEnd, count))
  }
}

// 自定义处理函数KeyedProcessFunction
class TopNHotItems(i: Int) extends KeyedProcessFunction[Tuple, ItemViewCount, String] {
  override def processElement(value: ItemViewCount, ctx: KeyedProcessFunction[Tuple, ItemViewCount, String]#Context, out: Collector[String]): Unit = {

  }
}