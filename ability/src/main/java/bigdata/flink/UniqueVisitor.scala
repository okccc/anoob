package bigdata.flink

import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.function.{AllWindowFunction, WindowFunction}
import org.apache.flink.streaming.api.scala.{AllWindowedStream, DataStream, KeyedStream, StreamExecutionEnvironment, WindowedStream, createTypeInformation}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.kafka.common.serialization.StringDeserializer

/**
 * Author: okccc
 * Date: 2021/3/18 4:17 下午
 * Desc: 统计1小时内的独立访客数(UV=UniqueVisitor)
 */

// 定义输入数据样例类(同一个package下类名不能重复,只需定义一次即可)
//case class UserBehavior(userId: Long, itemId: Long, categoryId: Int, behavior: String, timestamp: Long)
// 定义输出结果样例类
case class UVCount(windowEnd: Long, count: Int)

object UniqueVisitor {
  def main(args: Array[String]): Unit = {
    /**
     * 缓存穿透
     * 生产环境中会把一些数据放到redis做缓存,查询请求过来的时候会先查缓存,有就直接返回没有就再去查数据库并把查询结果放入缓存
     * 但是如果有大量请求都在查询一个不存在的userId,既然不存在那么肯定没有缓存,所以这些请求都会怼到数据库,可能会把数据库干翻
     *
     * 海量数据去重
     * 一亿userId存储空间大小：10^8 * 10byte ≈ 1g 使用set集合存储对服务器内存压力很大,redis也消耗不起这么多数据
     */

    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置时间语义
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    // 设置并行度
    env.setParallelism(1)

    // 2.Source操作
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    prop.put("key.deserializer", classOf[StringDeserializer])
    prop.put("value.deserializer", classOf[StringDeserializer])
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.Transform操作
    // 1).将流数据封装成样例类对象,并提取事件时间生成watermark
    val dataStream: DataStream[UserBehavior] = inputStream
      .map((data: String) => {
        val words: Array[String] = data.split(",")
        UserBehavior(words(0).toLong, words(1).toLong, words(2).toInt, words(3), words(4).toLong)
      })
      .assignAscendingTimestamps((u: UserBehavior) => u.timestamp * 1000)
    // 2).统计uv值
    val filterStream: DataStream[UserBehavior] = dataStream.filter((u: UserBehavior) => u.behavior == "pv")
    // 由于计算pv/uv本身不涉及keyBy操作,DataStream只能使用timeWindowAll,所有数据都会发送到一个slot,并行度是1无法充分利用集群资源
    // 可以先map映射成("uv", 1)元组,再按元组的第一个字段"uv"进行分组得到KeyedStream,这样就可以使用timeWindow
    // 不涉及刷新频率,分配滚动窗口即可
    val allWindowStream: AllWindowedStream[UserBehavior, TimeWindow] = filterStream.timeWindowAll(Time.hours(1))
    // 窗口关闭后的操作
    allWindowStream.apply(new UVCountWindow())

    // 4.Sink操作

    // 5.启动任务

  }

}


class UVCountWindow() extends AllWindowFunction[UserBehavior, UVCount, TimeWindow] {
  override def apply(window: TimeWindow, input: Iterable[UserBehavior], out: Collector[UVCount]): Unit = {
    // 存放userId的set集合
    val userIdSet: Set[Int] = Set[Int]()


  }
}

