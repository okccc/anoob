package com.okccc.demo

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.state.{MapState, MapStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Duration
import java.util
import scala.collection.mutable.ListBuffer

/**
 * @Author: okccc
 * @Date: 2021/3/4 2:46 下午
 * @Desc: 实时统计10分钟内的热门页面排名,5秒钟刷新一次(乱序数据)
 */

// 输入数据样例类
case class LogEvent(ip: String, userId: String, timestamp: Long, method: String, url: String)
// 输出结果样例类
case class PageViewCount(url: String, windowEnd: Long, count: Int)

object HotPages {
  def main(args: Array[String]): Unit = {
    // 创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度
    env.setParallelism(1)
    // 可以自定义水位线生成间隔
//    env.getConfig.setAutoWatermarkInterval(100)
    // 设置侧输出流
    val outputTag: OutputTag[LogEvent] = new OutputTag[LogEvent]("output")

    // 获取数据源
    val result: DataStream[String] = env
      // 83.149.9.216 - - 17/05/2015:10:05:49 +0000 GET /presentations
      // 83.149.9.216 - - 17/05/2015:10:05:50 +0000 GET /presentations
      // 83.149.9.216 - - 17/05/2015:10:05:46 +0000 GET /presentations
      // 83.149.9.216 - - 17/05/2015:10:05:51 +0000 GET /presentations
      // 83.149.9.216 - - 17/05/2015:10:05:31 +0000 GET /presentations
      // 83.149.9.216 - - 17/05/2015:10:05:52 +0000 GET /presentations
      // 83.149.9.216 - - 17/05/2015:10:05:31 +0000 GET /presentations
      // 83.149.9.216 - - 17/05/2015:10:05:53 +0000 GET /presentations
      // 83.149.9.216 - - 17/05/2015:10:05:49 +0000 GET /present
      // 83.149.9.216 - - 17/05/2015:10:05:54 +0000 GET /presentations
      // socket方便调试代码,没问题再替换成kafka
//      .socketTextStream("localhost", 9999)
      .readTextFile("input/apache.log")
      // 将流数据封装成样例类
      .map((line: String) => {
        // 83.149.9.216 - - 17/05/2015:10:05:03 +0000 GET /presentations/kibana-search.png
        val arr: Array[String] = line.split(" ")
        // 将日志中的时间字符串转换成Long类型的时间戳
        val sdf: SimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss")
        val ts: Long = sdf.parse(arr(3)).getTime
        // LogEvent(93.114.45.13,-,1431828317000,GET,/images/jordan-80.png)
        LogEvent(arr(0), arr(1), ts, arr(5), arr(6))
      })
      .filter((l: LogEvent) => l.method == "GET")
      // 提取时间戳生成水位线,观察数据乱序程度发现最大延迟接近1min,但整体上符合正态分布大部分延迟都在几秒钟
      .assignTimestampsAndWatermarks(
        // 为了保证实时性通常将watermark设置小一些,然后allowedLateness设置大一些
        WatermarkStrategy.forBoundedOutOfOrderness[LogEvent](Duration.ofSeconds(1))
          .withTimestampAssigner(new SerializableTimestampAssigner[LogEvent] {
            override def extractTimestamp(element: LogEvent, recordTimestamp: Long): Long =
              element.timestamp
          })
      )
      // 按照页面分组
      .keyBy((l: LogEvent) => l.url)
      // 开窗,有刷新频率的就是滑动窗口,一个EventTime可以属于窗口大小(10min)/滑动间隔(5s)=120个窗口
      .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.seconds(5)))
      // 允许迟到事件,水位线越过windowEnd + allowLateness时窗口才销毁
      .allowedLateness(Time.seconds(60))
      // 设置侧输出流
      .sideOutputLateData(outputTag)
      // 统计每个页面在每个窗口的访问量
      .aggregate(new PageCountAgg(), new PageWindowResult())
      // 再按照窗口分组
      .keyBy((p: PageViewCount) => p.windowEnd)
      // 对窗口内所有页面的访问量排序取前3
      .process(new PageTopN(3))

    result.print("topN")
    result.getSideOutput(outputTag).print("output")

    // 启动任务,流处理有头没尾源源不断,开启后一直监听直到手动关闭
    env.execute()
  }
}

// 自定义预聚合函数
class PageCountAgg extends AggregateFunction[LogEvent, Int, Int] {
  override def createAccumulator(): Int = 0
  override def add(value: LogEvent, accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = 0
}

// 自定义窗口处理函数
class PageWindowResult extends ProcessWindowFunction[Int, PageViewCount, String, TimeWindow] {
  override def process(key: String, context: Context, elements: Iterable[Int], out: Collector[PageViewCount]): Unit = {
    out.collect(PageViewCount(key, context.window.getEnd, elements.iterator.next()))
  }
}

// 自定义处理函数
class PageTopN(n: Int) extends KeyedProcessFunction[Long, PageViewCount, String] {
  // 由于迟到数据的存在,窗口的统计结果会不断更新,而ListState状态无法判断进来的url是否存在,这就可能导致多个相同url的PageViewCount
  // 进入process方法的定时器排序,所以迟到数据场景下应该使用MapState管理状态,当迟到数据进来时先判断url是否存在,存在就更新不存在就添加
  var mapState: MapState[String, Int] = _

  override def open(parameters: Configuration): Unit = {
    mapState = getRuntimeContext.getMapState(
      new MapStateDescriptor[String, Int]("mapState", classOf[String], classOf[Int])
    )
  }

  override def processElement(value: PageViewCount, ctx: KeyedProcessFunction[Long, PageViewCount, String]#Context, out: Collector[String]): Unit = {
    // 添加或更新状态
    mapState.put(value.url, value.count)
    // 注册一个windowEnd + 1(ms)触发的定时器,比如[0,4999]窗口加1ms就是[0,5000],当watermark>=5000时就触发
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 1)
    // 注册一个windowEnd + 1(min)触发的定时器,此时窗口等待1分钟后已经彻底关闭,不会再有迟到数据进来,这时候就可以清空状态了
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 60000)
  }

  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[Long, PageViewCount, String]#OnTimerContext, out: Collector[String]): Unit = {
    // 当有多个定时器时可以先根据触发时间判断是哪个定时器,这里触发定时器的key就是PageViewCount对象的windowEnd
    if (timestamp == ctx.getCurrentKey + 60000) {
      // 后面的定时器触发时,前面的定时器肯定已经触发执行完了,此时可以清空状态结束程序
      mapState.clear()
      return
    }

    // MapState本身没有排序功能,需额外定义ListBuffer存放MapState中的数据
    val urlcounts: ListBuffer[(String, Int)] = new ListBuffer[(String, Int)]
    // 获取MapState的迭代器
    val iterator: util.Iterator[util.Map.Entry[String, Int]] = mapState.entries().iterator()
    // 遍历迭代器
    while (iterator.hasNext) {
      // 将MapState数据添加到ListBuffer
      val entry: util.Map.Entry[String, Int] = iterator.next()
      urlcounts += ((entry.getKey, entry.getValue))
    }

    // 按count值排序取前n个,默认升序,排序可以使用sortBy()或sortWith()
    val topN: ListBuffer[(String, Int)] = urlcounts.sortWith((t1: (String, Int), t2: (String, Int)) => t1._2 > t2._2).take(n)

    // 窗口信息
    val windowEnd: Long = timestamp - 1
    val windowStart: Long = windowEnd - 3600 * 1000

    // 将排名信息拼接成字符串展示,实际应用场景可以写入数据库
    val sb: StringBuilder = new StringBuilder
    sb.append("========================================\n")
    sb.append("窗口区间：" + new Timestamp(windowStart) + " ~ " + new Timestamp(windowEnd) + "\n")
    for (i <- topN.indices) {
      val curUrlCount: (String, Int) = topN(i)
      sb.append("NO " + (i + 1) + " 的页面 " + curUrlCount._1 + " 访问次数 " + curUrlCount._2 + "\n")
    }
    // 收集结果往下游发送
    out.collect(sb.toString())
  }
}