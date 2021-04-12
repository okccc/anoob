package bigdata.flink.risk

import java.sql.Timestamp
import java.util.Properties

import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.scala.{DataStream, OutputTag, StreamExecutionEnvironment, createTypeInformation}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.kafka.common.serialization.StringDeserializer

/**
 * Author: okccc
 * Date: 2021/3/29 7:56 下午
 * Desc: 广告点击刷单行为分析
 */

// 定义输入数据样例类
case class AdClickLog(userId: String, adId: String, province: String, city: String, timestamp: Long)
// 定义输出结果样例类
case class AdClickCount(windowStart: String, windowEnd: String, province: String, count: Int)
// 定义黑名单样例类,刷单是指短时间内的大量重复请求
case class BlackListUser(userId: String, adId: String, msg: String)

object AdClick {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.Source操作
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    prop.put("key.deserializer", classOf[StringDeserializer])
    prop.put("value.deserializer", classOf[StringDeserializer])
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.Transform操作
    // 将流数据封装成样例类对象
    val mapStream: DataStream[AdClickLog] = inputStream
      .map((data: String) => {
        val words: Array[String] = data.split(",")
        AdClickLog(words(0), words(1), words(2), words(3), words(4).toLong)
      })
      // 提取事件时间生成水位线
      .assignAscendingTimestamps((ad: AdClickLog) => ad.timestamp * 1000)

    // 代码优化：先处理刷单行为 1.刷单数据放到侧输出流输出 2.刷单用户添加到黑名单  涉及状态管理和定时器操作直接上process
    val filterStream: DataStream[AdClickLog] = mapStream
      .keyBy((ad: AdClickLog) => (ad.userId, ad.adId))
      .process(new FilterBlackListUser())

    // 窗口操作
    val resultStream: DataStream[AdClickCount] = filterStream
      // 按照省份分组
      .keyBy((ad: AdClickLog) => ad.province)
      // 分配滑动窗口
      .timeWindow(Time.hours(1), Time.seconds(5))
      // 窗口关闭后的聚合操作
      .aggregate(new AdClickCountAgg(), new AdClickCountWindow())

    // 4.Sink操作
    // 结果显示beijing地区的数据明显偏多,查看数据源发现有大量相同的(userId, adId)属于刷单行为,应该在数据处理之间就过滤掉
    resultStream.print("res")
    // 获取侧输出流数据
    filterStream.getSideOutput(new OutputTag[BlackListUser]("black-list")).print("warning")
    // 5.开启任务
    env.execute("ad click count")
  }
}

// 自定义预聚合函数
class AdClickCountAgg() extends AggregateFunction[AdClickLog, Int, Int] {
  override def createAccumulator(): Int = 0
  override def add(value: AdClickLog, accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = a + b
}

// 自定义全窗口函数
class AdClickCountWindow() extends WindowFunction[Int, AdClickCount, String, TimeWindow] {
  override def apply(key: String, window: TimeWindow, input: Iterable[Int], out: Collector[AdClickCount]): Unit = {
    // 将long类型时间戳转换成字符串
    val windowStart: String = new Timestamp(window.getStart).toString
    val windowEnd: String = new Timestamp(window.getEnd).toString
    // 收集结果封装成样例类
    out.collect(AdClickCount(windowStart, windowEnd, key, input.head))
  }
}

/*
 * 自定义处理函数
 * abstract class KeyedProcessFunction<K, I, O>
 * K: 分组字段类型,(userId, adId)
 * I: 输入元素类型,AdClickLog
 * O: 输出结果类型,AdClickLog
 */
class FilterBlackListUser() extends KeyedProcessFunction[(String, String), AdClickLog, AdClickLog] {
  // 定义状态记录用户点击广告次数
  lazy val clickCountState: ValueState[Int] = getRuntimeContext.getState(new ValueStateDescriptor[Int]("click-count", classOf[Int]))
  // 定义状态标记当前用户是否已经在黑名单
  lazy val isBlackState: ValueState[Boolean] = getRuntimeContext.getState(new ValueStateDescriptor[Boolean]("is-black", classOf[Boolean]))
  // 定义状态记录每天定时清空状态的时间戳
  lazy val resetTimerState: ValueState[Long] = getRuntimeContext.getState(new ValueStateDescriptor[Long]("reset-ts", classOf[Long]))

  // 设置点击次数上限和告警信息
  val maxCount: Int = 60
  val warningMsg: String = "WARNING: ad click over " + maxCount + " times today!"

  /**
   * 处理每个进来的AdClickLog
   * @param value 输入元素类型,AdClickLog
   * @param ctx KeyedProcessFunction的内部类Context,提供了流的一些上下文信息,可以获取时间戳、定时服务、侧输出流、当前key等
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素,processElement方法一般用不到out
   */
  override def processElement(value: AdClickLog, ctx: KeyedProcessFunction[(String, String), AdClickLog, AdClickLog]#Context, out: Collector[AdClickLog]): Unit = {
    // 获取点击次数的当前状态值
    val curCount: Int = clickCountState.value()

    // 当该状态的第一个数据进来时,就应该注册0点的清空状态定时器
    if (curCount == 0) {
      // 获取明天0点的时间戳,默认是伦敦时间,北京时间要减8小时
      val currentTime: Long = ctx.timerService().currentProcessingTime()
      val triggerTime: Long = (currentTime / (1000 * 60 * 60 * 24) + 1) * (24 * 60 * 60 * 1000) - (8 * 60 * 60 * 1000)
      // 更新清空状态时间戳的状态值
      resetTimerState.update(triggerTime)
      // 注册明天0点的定时器,这里是机器时间
      ctx.timerService().registerProcessingTimeTimer(triggerTime)
    }

    // 判断点击次数是否已达上限
    if (curCount >= maxCount) {
      // 判断用户是否已经在黑名单
      if (!isBlackState.value()) {
        // 不在的话就更新到黑名单,并将该条数据放到侧输出流
        isBlackState.update(true)
        ctx.output(new OutputTag[BlackListUser]("black-list"), BlackListUser(value.userId, value.adId, warningMsg))
      }
      // 已经在黑名单就不用处理
      return
    }

    // 点击次数未达到上限就正常累加状态,然后收集结果输出
    clickCountState.update(curCount + 1)
    out.collect(value)
  }

  /**
   * 触发定时器时调用
   * @param timestamp processElement方法中设定的触发定时器的时间戳
   * @param ctx KeyedProcessFunction的内部类OnTimerContext,提供了流的一些上下文信息,包括当前定时器的时间域和key
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素,onTimer方法会用到out
   */
  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[(String, String), AdClickLog, AdClickLog]#OnTimerContext, out: Collector[AdClickLog]): Unit = {
    // 第二天0点时清空点击次数状态和黑名单状态
    if (timestamp == resetTimerState.value()) {
      clickCountState.clear()
      isBlackState.clear()
    }
  }
}