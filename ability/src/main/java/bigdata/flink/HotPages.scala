package bigdata.flink

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util
import java.util.Properties

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
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
 * Desc: 实时统计10min内的热门页面,5s刷新一次
 */

// 定义输入数据的样例类
case class LogEvent(ip: String, userId: String, timestamp: Long, method: String, url: String)
// 定义输出窗口聚合结果的样例类
case class HotPageCount(url: String, windowEnd: Long, count: Int)

object HotPages {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度
//    env.setParallelism(1)
    // 设置时间语义,查看源码发现水位线生成间隔ProcessingTime默认是0,其它默认是200
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    // 可以自定义水位线生成间隔
    env.getConfig.setAutoWatermarkInterval(100)

    // 2.读取kafka数据
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    prop.put("key.deserializer", classOf[StringDeserializer])
    prop.put("value.deserializer", classOf[StringDeserializer])
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.转换处理
    // 1).将流数据封装成样例类对象,并提取事件时间生成watermark
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
      // 而且窗口的滑动间隔才5s但是延迟却设置了1min显然不合理,实际上watermark应该小一些,然后窗口状态保存久一些,剩下还没处理的数据放到侧输出流
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[LogEvent](Time.seconds(1)) {
        // 每来一条数据都抽取其时间戳
        override def extractTimestamp(element: LogEvent): Long = element.timestamp
      })
    // 每来一条数据都会输出
    dataStream.print("data")  // data> LogEvent(93.114.45.13,-,1431828317000,GET,/images/jordan-80.png)

    // 2).将DataStream[LogEvent]按照url分组聚合
    val filterStream: DataStream[LogEvent] = dataStream.filter((logEvent: LogEvent) => logEvent.method == "GET")
    val keyedByUrlStream: KeyedStream[LogEvent, String] = filterStream.keyBy((logEvent: LogEvent) => logEvent.url)
    // 创建侧输出流
    val outputTag: OutputTag[LogEvent] = new OutputTag[LogEvent]("late-data")

    /*
     * 窗口分配器: 定义窗口,KeyedStream对应timeWindow方法(推荐),non-KeyedStream对应timeWindowAll方法
     * class WindowedStream[T, K, W <: Window]
     * T: 输入元素类型,LogEvent
     * K: 分组字段类型,url字符串
     * W: 窗口分配器分配的窗口类型,一般都是时间窗口,W <: Window表示上边界,即W必须是Window类型或其子类
     */
    val windowStream: WindowedStream[LogEvent, String, TimeWindow] = keyedByUrlStream
      // 分配窗口tumbling window/sliding window,有刷新频率的就是滑动窗口,一个EventTime可以属于窗口大小(10min)/滑动间隔(5s)=120个窗口
      .timeWindow(Time.minutes(10), Time.seconds(5))
      // 设置允许延迟时长：保持窗口状态1min,只要是在这1分钟时间内进来的迟到数据都会叠加在原先的统计结果上
      .allowedLateness(Time.seconds(60))
      // 开启侧输出流
      .sideOutputLateData(outputTag)

    /*
     * 窗口函数: 窗口关闭后的聚合操作
     * flink函数式编程提供了所有udf函数(实现方式为接口或抽象类),MapFunction/AggregateFunction/WindowFunction/ProcessFunction...
     * AggregateFunction是一个底层通用函数,直接聚合(url, count)无法判断属于哪个窗口,需结合WindowFunction使用,先来一条处理一条做增量聚合
     * 等窗口关闭时再调用窗口函数,拿到预聚合状态并添加窗口信息将其封装成PageViewCount(url, windowEnd, count)得到每个页面在每个窗口的点击量
     */
    val aggStream: DataStream[HotPageCount] = windowStream.aggregate(new HotPageCountAgg(), new HotPageCountWindow())
    // 有窗口关闭时就会生成聚合结果
    aggStream.print("agg")
    // 当EventTime所属的所有窗口都已经关闭时就会进入侧输出流,比如事件时间10:25:51属于[10:15:55, 10:25:55) ~ [10:25:50, 10:35:50)
    // 之间的所有窗口,当最后一个窗口[10:25:50, 10:35:50)也关闭,等到下一个窗口[10:25:55, 10:35:55)时它就再也进不来了,只能放到侧输出流
    aggStream.getSideOutput(outputTag).print("late-data")

    // 3).将DataStream[HotPageCount]按照windowEnd分组聚合
    val keyedByWindowEndStream: KeyedStream[HotPageCount, Long] = aggStream.keyBy((p: HotPageCount) => p.windowEnd)
    // map/window这些算子是无法访问事件时间和水位线的,因此DataStream API提供了一些Low-Level算子,可以访问时间戳/watermark/注册定时事件
    // ProcessFunction属于底层大招,可以构建基于事件驱动的应用,自定义所有功能,最常用的是KeyedProcessFunction,用来操作KeyedStream
    val resultStream: DataStream[String] = keyedByWindowEndStream.process(new TopNHotPages(3))
    // topN操作是在定时器里完成
    resultStream.print("topN")

    // 4.启动任务
    env.execute("hot pages")
  }
}

/*
 * 自定义预聚合函数
 * interface AggregateFunction<IN, ACC, OUT>
 * IN:  输入待聚合元素的类型,LogEvent
 * ACC: 累加器中间聚合状态的类型,Int
 * OUT: 输出聚合结果的类型,Int
 */
class HotPageCountAgg() extends AggregateFunction[LogEvent, Int, Int] {
  override def createAccumulator(): Int = 0
  // 聚合状态就是当前页面的count值,来一条数据调用一次add方法,count值+1
  override def add(value: LogEvent, accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = a + b
}

/*
 * 自定义窗口函数
 * trait WindowFunction[IN, OUT, KEY, W <: Window]
 * IN:  输入元素类型,就是前面的预聚合结果,Int
 * OUT: 输出的是窗口聚合的结果样例类PageViewCount(url, windowEnd, count)
 * KEY: 分组字段类型,url字符串
 * W:   窗口分配器分配的窗口类型,一般都是时间窗口,W <: Window表示上边界,即W必须是Window类型或其子类
 */
class HotPageCountWindow() extends WindowFunction[Int, HotPageCount, String, TimeWindow] {
  override def apply(key: String, window: TimeWindow, input: Iterable[Int], out: Collector[HotPageCount]): Unit = {
    // 获取页面url、窗口结束时间、累加值
    val url: String = key
    val windowEnd: Long = window.getEnd
    val count: Int = input.iterator.next()  // 或者直接取迭代器的第一个值input.head
    // 收集结果封装成样例类对象
    out.collect(HotPageCount(url, windowEnd, count))
  }
}

/*
 * 自定义处理函数
 * abstract class KeyedProcessFunction<K, I, O>
 * K: 分组字段类型
 * I: 输入元素类型
 * O: 输出元素类型
 */
class TopNHotPages(i: Int) extends KeyedProcessFunction[Long, HotPageCount, String] {
  /*
   * flink所有函数都有其对应的Rich版本,Rich函数和Process函数都继承自RichFunction接口,提供了三个特有方法
   * getRuntimeContext(运行环境上下文)/open(生命周期初始化,比如数据库连接)/close(生命周期结束,比如关闭数据库连接、清空状态等)
   * KeyedState常用数据结构：ValueState(单值状态)/ListState(列表状态)/MapState(键值对状态)/ReducingState & AggregatingState(聚合状态)
   * MapState接口体系包含put/get/entries/clear等方法
   */

  // 先定义状态,由于迟到数据的存在,每来一条迟到数据都会有对应等待窗口的PageViewCount更新,如果某个窗口连续来了几条相同url的迟到数据
  // 就会生成多个相同url的PageViewCount进入process方法的定时器进行排序,如果使用ListState状态会直接add,导致同一个url被多次排序
  // 合理做法应该是先判断进来的url是否已存在,存在就覆盖不存在就添加,所以应该用MapState状态
//  lazy val pageViewCountListState: ListState[HotPageCount] = getRuntimeContext
//    .getListState(new ListStateDescriptor[HotPageCount]("pageViewCount-list", classOf[HotPageCount]))
  lazy val pageViewCountMapState: MapState[String, Int] = super.getRuntimeContext
    .getMapState(new MapStateDescriptor[String, Int]("pageViewCount-map", classOf[String], classOf[Int]))

  /**
   * 处理每个进来的PageViewCount
   * @param value 输入元素
   * @param ctx KeyedProcessFunction的内部类Context,提供了流的一些上下文信息,包括当前处理元素的时间戳和key、注册定时服务、写数据到侧输出流
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素,processElement方法一般用不到out
   */
  override def processElement(value: HotPageCount, ctx: KeyedProcessFunction[Long, HotPageCount, String]#Context, out: Collector[String]): Unit = {
    // 添加或更新状态
    pageViewCountMapState.put(value.url, value.count)
    // 每来一个PageViewCount就会注册一个定时器,水位线传递机制是更新了才会往下游传递,不然还是之前的水位线,下游收不到新的水位线就不会触发定时器
    // 注册一个windowEnd + 1(毫秒)触发的定时器,比如到达09:30:50这个watermark了再延迟1毫秒就执行定时器
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 1)
    // 注册一个windowEnd + 1(分钟)触发的定时器,此时窗口等待1分钟后已经彻底关闭,不会再有聚合结果输出,这时候就可以清空状态了
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 60000)
  }

  /**
   * 触发定时器时调用
   * @param timestamp processElement方法中设定的触发定时器的时间戳
   * @param ctx KeyedProcessFunction的内部类OnTimerContext,提供了流的一些上下文信息,包括当前定时器的时间域和key
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素,onTimer方法会用到out
   */
  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[Long, HotPageCount, String]#OnTimerContext, out: Collector[String]): Unit = {
    // 先判断定时器触发时间,这里触发定时器的key就是PageViewCount对象的windowEnd
    if (timestamp == ctx.getCurrentKey + 60000) {
      // 如果已经是窗口关闭又等待了1min之后,就不会再有迟到数据进来了,也就不会再执行下面属于windowEnd + 1定时器的操作,此时可以清空状态结束程序
      pageViewCountMapState.clear()
      return
    }

    // MapState本身没有排序功能,需额外定义ListBuffer存放MapState中的数据
    val urlCounts: ListBuffer[(String, Int)] = new ListBuffer[(String, Int)]
    // 获取MapState的迭代器
    val iterator: util.Iterator[util.Map.Entry[String, Int]] = pageViewCountMapState.entries().iterator()
    // 遍历迭代器
    while (iterator.hasNext) {
      // 将MapState数据添加到ListBuffer
      val entry: util.Map.Entry[String, Int] = iterator.next()
      urlCounts += ((entry.getKey, entry.getValue))
    }

    // 按count值排序取前n个,默认升序,排序可以使用sortBy()或sortWith()
    val sortedUrlCounts: ListBuffer[(String, Int)] = urlCounts.sortWith((t1: (String, Int), t2: (String, Int)) => t1._2 > t2._2).take(i)

    // 将排名信息拼接成字符串展示,实际应用场景可以写入数据库
    val sb: StringBuilder = new StringBuilder
    sb.append("窗口结束时间：").append(new Timestamp(timestamp - 1)).append("\n")
    // 遍历当前窗口结果列表中的每个PageViewCount,输出到一行
    for (i <- sortedUrlCounts.indices) {
      val currentUrlCount: (String, Int) = sortedUrlCounts(i)
      sb.append("NO").append(i + 1).append(": ")
        .append("页面url=").append(currentUrlCount._1).append("\t")
        .append("流量=").append(currentUrlCount._2).append("\n")
    }
    sb.append("\n==================================\n")
    // 输出一个窗口后停1秒
    Thread.sleep(1000)

    // 收集结果
    out.collect(sb.toString())
  }
}