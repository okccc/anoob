package app

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util
import java.util.Properties

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
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
    // 设置时间语义,查看源码发现水位线生成间隔ProcessingTime默认是0,其它默认是200
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    // 可以自定义水位线生成间隔
    env.getConfig.setAutoWatermarkInterval(100L)

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
        // 将日志中的时间字符串转换成Long类型的时间戳
        val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss")
        val ts: Long = simpleDateFormat.parse(words(3)).getTime
        LogEvent(words(0), words(1), ts, words(5), words(6))
      })
      // 观察数据乱序程度发现最大延迟接近1min,但是整体上符合正态分布大部分延迟几秒,如果设置maxOutOfOrderness=1min那么触发窗口计算就要等很久
      // 而且窗口的滑动间隔才5s但是延迟却设置了1min显然不合理,实际上watermark应该小一些,然后窗口状态保持久一些,剩下还没处理的数据放到侧输出流
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[LogEvent](Time.seconds(1)) {
        // 每来一条数据都抽取其时间戳
        override def extractTimestamp(element: LogEvent): Long = element.timestamp
      })
    dataStream.print("data")  // data> LogEvent(93.114.45.13,-,1431828317000,GET,/images/jordan-80.png)

    // 2).按照页面分组聚合
    // 过滤get请求
    val filterStream: DataStream[LogEvent] = dataStream.filter((logEvent: LogEvent) => {
      logEvent.method == "GET" // && !logEvent.url.endsWith("css") && !logEvent.url.endsWith("js")
    })
    // 按照页面url分组
    val keyedStream: KeyedStream[LogEvent, String] = filterStream.keyBy((logEvent: LogEvent) => logEvent.url)
    // 设置侧输出流
    val outputTag: OutputTag[LogEvent] = new OutputTag[LogEvent]("late-data")
    // 设置窗口大小10分钟滑动间隔5秒的时间窗口,并保留窗口状态至最大允许时长,并开启侧输出流
    val windowStream: WindowedStream[LogEvent, String, TimeWindow] = keyedStream
      .timeWindow(Time.minutes(10), Time.seconds(5))
      .allowedLateness(Time.seconds(60))
      .sideOutputLateData(outputTag)
    // 聚合操作,可以提取侧输出流
    val aggStream: DataStream[PageViewCount] = windowStream.aggregate(new PageCountAgg(), new PageViewCountWindow())
    aggStream.print("agg")
    aggStream.getSideOutput(outputTag).print("late")

    // 3).按照窗口分组聚合
    val windowEndStream: KeyedStream[PageViewCount, Long] = aggStream.keyBy((pageViewCount: PageViewCount) => pageViewCount.windowEnd)
    // 涉及状态编程和定时器,使用最底层的api,终极大招process()方法
    val resultStream: DataStream[String] = windowEndStream.process(new TopNHotPages(3))
    resultStream.print("topN")

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

class PageViewCountWindow() extends WindowFunction[Int, PageViewCount, String, TimeWindow] {
  override def apply(key: String, window: TimeWindow, input: Iterable[Int], out: Collector[PageViewCount]): Unit = {
    // 获取页面url、时间窗口、累加值
    val url: String = key
    val windowEnd: Long = window.getEnd
    val count: Int = input.iterator.next()
    // 收集结果封装成样例类对象
    out.collect(PageViewCount(url, windowEnd, count))
  }
}

class TopNHotPages(i: Int) extends KeyedProcessFunction[Long, PageViewCount, String] {
  // 先定义状态
  lazy val pageViewCountListState: ListState[PageViewCount] = getRuntimeContext.getListState(new ListStateDescriptor[PageViewCount]("pageViewCount-list", classOf[PageViewCount]))

  /**
   * 处理输入流中每个元素
   * @param value 输入元素
   * @param ctx KeyedProcessFunction的内部类Context,提供了流的一些上下文信息,包括当前处理元素的时间戳和key、注册定时服务、写数据到侧输出流
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素
   */
  override def processElement(value: PageViewCount, ctx: KeyedProcessFunction[Long, PageViewCount, String]#Context, out: Collector[String]): Unit = {
    // 每来一条数据都添加到状态中
    pageViewCountListState.add(value)
    // 注册一个windowEnd + 1(ms)之后触发的定时器,比如9点延迟1毫秒后到达watermark从而触发窗口执行
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 1)
  }

  /**
   * 触发定时器时调用
   * @param timestamp 定时器设定的触发时间戳,就是processElement方法里设定的windowEnd + 1
   * @param ctx KeyedProcessFunction的内部类OnTimerContext,提供了流的一些上下文信息,包括当前定时器的时间域和key
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素
   */
  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[Long, PageViewCount, String]#OnTimerContext, out: Collector[String]): Unit = {
    // ListState本身没有排序功能,需额外定义ListBuffer存放ListState所有数据
    val pageViewCounts: ListBuffer[PageViewCount] = new ListBuffer[PageViewCount]
    // 获取ListState的迭代器
    val iterator: util.Iterator[PageViewCount] = pageViewCountListState.get().iterator()
    // 遍历迭代器
    while (iterator.hasNext) {
      // 将ListState数据添加到ListBuffer
      pageViewCounts += iterator.next()
    }
    // 此时状态已经不需要了,可以提前清除状态,节约内存空间
    pageViewCountListState.clear()
    // 按count值排序取前n个,默认升序,排序可以使用sortBy()或sortWith()
    val sortedPageViewCounts: ListBuffer[PageViewCount] = pageViewCounts.sortWith((p1: PageViewCount, p2: PageViewCount) => p1.count > p2.count).take(i)

    // 将排名信息拼接成字符串展示
    val sb: StringBuilder = new StringBuilder
    sb.append("窗口结束时间：").append(new Timestamp(timestamp - 1)).append("\n")
    // 遍历当前窗口结果列表中的每个PageViewCount,输出到一行
    for (i <- sortedPageViewCounts.indices) {
      val pageViewCount: PageViewCount = sortedPageViewCounts(i)
      sb.append("NO").append(i + 1).append(": ")
        .append("页面url=").append(pageViewCount.url).append("\t")
        .append("流量=").append(pageViewCount.count).append("\n")
    }
    sb.append("\n==================================\n")
    // 输出一个窗口后停1秒
    Thread.sleep(1000)

    // 收集结果
    out.collect(sb.toString())
  }
}
