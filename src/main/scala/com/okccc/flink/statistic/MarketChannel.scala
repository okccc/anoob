package com.okccc.flink.statistic

import java.sql.Timestamp
import java.util.UUID

import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment, createTypeInformation}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import scala.util.Random

/**
 * Author: okccc
 * Date: 2021/3/29 3:49 下午
 * Desc: app市场渠道推广统计
 */

// 定义输入数据样例类
case class ChannelLog(userId: String, behavior: String, channel: String, timestamp: Long)
// 定义输出结果样例类
case class ChannelCount(windowStart: String, windowEnd: String, behavior: String, channel: String, count: Int)

/*
 * 模拟生成测试数据源
 * interface SourceFunction<T>: 该接口用于自定义数据源
 */
class TestSource extends SourceFunction[ChannelLog] {
  // 定义运行标识
  var running: Boolean = true

  // 定义行为、渠道、随机数生成器
  val behaviors: Array[String] = Array("view", "download", "install", "uninstall")
  val channels: Seq[String] = Seq("appstore", "weibo", "wechat")
  private val random: Random = new Random()

  // 生产数据
  override def run(ctx: SourceFunction.SourceContext[ChannelLog]): Unit = {
    // 设置数据量上限
    val maxCount: Int = Int.MaxValue
    var count: Int = 0
    // 循环生成数据
    while (running && count < maxCount) {
      // 随机生成数据
      val userId: String = UUID.randomUUID().toString
      val behavior: String = behaviors(random.nextInt(behaviors.length))
      val channel: String = channels(random.nextInt(channels.size))
      val timestamp: Long = System.currentTimeMillis()
      // 收集结果封装成样例类
      ctx.collect(ChannelLog(userId, behavior, channel, timestamp))
      count += 1
      Thread.sleep(100)
    }
  }

  // 停止生产数据
  override def cancel(): Unit = {
    running = false
  }
}

object MarketChannel {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.Source操作
    val inputStream: DataStream[ChannelLog] = env
      .addSource(new TestSource())
      .assignAscendingTimestamps((c: ChannelLog) => c.timestamp)

    // 3.Transform操作
    val resultStream: DataStream[ChannelCount] = inputStream
      // 过滤行为
      .filter((c: ChannelLog) => c.behavior != "uninstall")
      // 按照行为和渠道两个维度分组
      .keyBy((u: ChannelLog) => (u.behavior, u.channel))
      // 分配滑动窗口
      .timeWindow(Time.hours(1), Time.seconds(5))
      // 这里可以先aggregate预聚合,也可以直接全窗口函数处理,具体情况具体分析
      .process(new MarketCountByChannel())

    // 4.Sink操作
    resultStream.print("res")
    // 5.启动任务
    env.execute("com.okccc.app market channel")
  }
}

/*
 * 自定义全窗口函数
 * abstract class ProcessWindowFunction[IN, OUT, KEY, W <: Window]
 * IN:  输入元素类型,ChannelLog
 * OUT: 输出元素类型,ChannelCount
 * KEY: 分组字段类型,(behavior, channel)
 * W:   窗口分配器分配的窗口类型,一般都是时间窗口,W <: Window表示上边界,即W必须是Window类型或其子类
 */
class MarketCountByChannel() extends ProcessWindowFunction[ChannelLog, ChannelCount, (String, String), TimeWindow] {
  /**
   * 处理窗口中的所有元素
   * @param key 分组字段
   * @param context ProcessWindowFunction类的内部类Context,提供了流的一些上下文信息,包括窗口信息、处理时间、水位线、侧输出流等
   * @param elements 窗口中存储的元素,process全窗口函数会把所有数据都存下来,aggregate增量聚合函数只存一个accumulator记录状态(推荐)
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素
   */
  override def process(key: (String, String), context: Context, elements: Iterable[ChannelLog], out: Collector[ChannelCount]): Unit = {
    // 将时间戳转换成字符串类型
    val windowStart: String = new Timestamp(context.window.getStart).toString
    val windowEnd: String = new Timestamp(context.window.getEnd).toString
    // 收集结果封装成样例类
    out.collect(ChannelCount(windowStart, windowEnd, key._1, key._2, elements.size))
  }
}
