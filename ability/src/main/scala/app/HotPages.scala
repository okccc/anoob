package app

import java.text.SimpleDateFormat
import java.util.Properties

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

/**
 * Author: okccc
 * Date: 2021/3/4 2:46 下午
 * Desc: 热门页面流量统计
 */

// 定义输入数据的样例类
case class LogEvent(ip: String, userId: String, timestamp: Long, method: String, url: String)
// 定义窗口聚合结果的样例类
case class PageViewCount(url: String, windowEnd: Long, count: Int)

object HotPages {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度
    env.setParallelism(1)
    // 设置时间语义
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.读取kafka数据
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    prop.put("key.deserializer", classOf[StringDeserializer])
    prop.put("value.deserializer", classOf[StringDeserializer])
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.转换处理
    // 1).将流数据封装成样例类对象,并提取时间戳生成watermark
    val dataStream: DataStream[LogEvent] = inputStream
      .map((line: String) => {
        // 83.149.9.216 - - 17/05/2015:10:05:03 +0000 GET /presentations/logstash-2013/images/kibana-search.png
        val words: Array[String] = line.split(" ")
        // 将日志中的时间格式转换成Long类型的时间戳
        val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss")
        val ts: Long = simpleDateFormat.parse(words(3)).getTime
        LogEvent(words(0), words(1), ts, words(5), words(6))
      })
      // 乱序数据的水位线设置
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[LogEvent](Time.seconds(30)) {
        override def extractTimestamp(element: LogEvent): Long = element.timestamp
      })
    dataStream.print("data")  // data> LogEvent(93.114.45.13,-,1431828317000,GET,/images/jordan-80.png)

    // 2).按照页面分组聚合
    // 过滤GET请求
    val filterStream: DataStream[LogEvent] = dataStream.filter((logEvent: LogEvent) => logEvent.method == "GET")
    // 按照页面url分组
    val keyedStream: KeyedStream[LogEvent, String] = filterStream.keyBy((logEvent: LogEvent) => logEvent.url)
    // 设置滑动窗口,窗口长度10min,滑动间隔5s
    val windowStream: WindowedStream[LogEvent, String, TimeWindow] = keyedStream.timeWindow(Time.minutes(10), Time.seconds(5))
    // 聚合操作
    windowStream.aggregate(new PageCountAgg(), new PageViewCountWindow())


    // 3).按照窗口分组聚合


    // 4.启动任务
    env.execute("hot pages")
  }


}

class PageCountAgg() extends AggregateFunction[LogEvent, Int, Int] {
  override def createAccumulator(): Int = 0
  // 聚合状态就是当前页面的count值,来一条数据调用一次add方法,count值+1
  override def add(value: LogEvent, accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = a + b
}

class PageViewCountWindow()
