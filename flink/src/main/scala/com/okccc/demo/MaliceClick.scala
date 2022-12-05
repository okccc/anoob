package com.okccc.demo

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.scala.{DataStream, OutputTag, StreamExecutionEnvironment, createTypeInformation}
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import java.sql.Timestamp
import java.time.Duration

/**
 * Author: okccc
 * Date: 2021/3/29 7:56 下午
 * Desc: 广告点击刷单行为分析
 */

// 输入数据样例类
case class ClickEvent(userId: String, adId: String, province: String, city: String, timestamp: Long)
// 输出结果样例类
case class ClickCount(windowStart: Timestamp, windowEnd: Timestamp, province: String, city: String, count: Int)
// 黑名单样例类,刷单是指短时间内的大量重复请求
case class BlackListUser(userId: String, adId: String, msg: String)

object MaliceClick {
  def main(args: Array[String]): Unit = {
    // 创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    // 获取数据源
    val filterStream: DataStream[ClickEvent] = env
      .readTextFile("input/ClickLog.csv")
      // 将流数据封装成样例类对象
      .map((data: String) => {
        // 561558,3611281,guangdong,shenzhen,1511658120
        val arr: Array[String] = data.split(",")
        ClickEvent(arr(0), arr(1), arr(2), arr(3), arr(4).toLong * 1000)
      })
      // 提取时间戳生成水位线
      .assignTimestampsAndWatermarks(
        WatermarkStrategy.forBoundedOutOfOrderness[ClickEvent](Duration.ofSeconds(0))
          .withTimestampAssigner(new SerializableTimestampAssigner[ClickEvent] {
            override def extractTimestamp(element: ClickEvent, recordTimestamp: Long): Long =
              element.timestamp
          })
      )
      // 先处理刷单行为,按照(userId, adId)分组
      .keyBy((ad: ClickEvent) => (ad.userId, ad.adId))
      // 将刷单数据放到侧输出流,将刷单用户添加到黑名单,涉及状态管理和定时器操作直接上大招
      .process(new BlackListFilter())

    filterStream
      // 分组
      .keyBy((ad: ClickEvent) => (ad.province, ad.city))
      // 开窗
      .window(TumblingEventTimeWindows.of(Time.minutes(5)))
      // 聚合,结果显示beijing地区数据明显偏多,有大量相同的(userId,adId)属于刷单行为,应该在数据统计之前就过滤掉
      .aggregate(new ClickCountAgg(), new ClickWindowResult())
      .print()

    // 获取侧输出流数据
    filterStream.getSideOutput(new OutputTag[BlackListUser]("black")).print("output")

    // 启动任务
    env.execute()
  }
}

// 自定义预聚合函数
class ClickCountAgg extends AggregateFunction[ClickEvent, Int, Int] {
  override def createAccumulator(): Int = 0
  override def add(value: ClickEvent, accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = a + b
}

// 自定义窗口处理函数
class ClickWindowResult extends WindowFunction[Int, ClickCount, (String, String), TimeWindow] {
  override def apply(key: (String, String), window: TimeWindow, input: Iterable[Int], out: Collector[ClickCount]): Unit = {
    // 收集结果封装成样例类
    out.collect(ClickCount(new Timestamp(window.getStart), new Timestamp(window.getEnd), key._1, key._2, input.head))
  }
}

// 自定义处理函数
class BlackListFilter extends KeyedProcessFunction[(String, String), ClickEvent, ClickEvent] {
  // 定义状态记录用户点击广告次数
  lazy val clickNumState: ValueState[Int] = getRuntimeContext.getState(new ValueStateDescriptor[Int]("click-num", classOf[Int]))
  // 定义状态标记当前用户是否已经在黑名单
  lazy val isBlackState: ValueState[Boolean] = getRuntimeContext.getState(new ValueStateDescriptor[Boolean]("is-black", classOf[Boolean]))
  // 定义状态记录每天定时清空状态的时间戳
  lazy val timerState: ValueState[Long] = getRuntimeContext.getState(new ValueStateDescriptor[Long]("timer", classOf[Long]))

  // 设置点击次数上限和告警信息
  val maxCount: Int = 30
  val warningMsg: String = "WARNING: ad click over " + maxCount + " times today!"

  override def processElement(value: ClickEvent, ctx: KeyedProcessFunction[(String, String), ClickEvent, ClickEvent]#Context, out: Collector[ClickEvent]): Unit = {
    // 1.第一次点击
    if (clickNumState.value() == 0) {
      // 获取明天0点的时间戳,默认是伦敦时间,北京时间要减8小时
      val currentTime: Long = ctx.timerService().currentProcessingTime()
      val triggerTime: Long = (currentTime / (1000 * 60 * 60 * 24) + 1) * (24 * 60 * 60 * 1000) - (8 * 60 * 60 * 1000)
      // 注册明天0点的定时器,这里是机器时间
      ctx.timerService().registerProcessingTimeTimer(triggerTime)
      // 更新清空状态时间戳的状态值
      timerState.update(triggerTime)
      clickNumState.update(1)
      // 2.点击次数未达上限
    } else if (clickNumState.value() < maxCount) {
      // 更新状态
      clickNumState.update(clickNumState.value() + 1)
      // 3.点击次数已达到上限
    } else {
      // 判断用户是否已经在黑名单
      if (!isBlackState.value()) {
        // 不在的话就更新到黑名单,并将该条数据放到侧输出流
        isBlackState.update(true)
        ctx.output(new OutputTag[BlackListUser]("black"), BlackListUser(value.userId, value.adId, warningMsg))
      }
      // 已经在黑名单就不用处理直接结束
      return
    }

    // 收集结果往下游发送
    out.collect(value)
  }

  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[(String, String), ClickEvent, ClickEvent]#OnTimerContext, out: Collector[ClickEvent]): Unit = {
    // 第二天0点时清空所有状态
    clickNumState.clear()
    isBlackState.clear()
    timerState.clear()
  }
}