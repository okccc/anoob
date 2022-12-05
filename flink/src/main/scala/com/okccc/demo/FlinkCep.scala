package com.okccc.demo

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.cep.PatternSelectFunction
import org.apache.flink.cep.scala.pattern.Pattern
import org.apache.flink.cep.scala.{CEP, PatternStream}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.{DataStream, OutputTag, StreamExecutionEnvironment, createTypeInformation}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

import java.time.Duration
import java.util

/**
 * Author: okccc
 * Date: 2021/10/14 下午4:39
 * Desc: 5秒内连续3次登录失败(严格近邻)、15分钟内未支付的超时订单(宽松近邻)
 */

// 输入数据样例类
case class LoginEvent(userId: String, ip: String, eventType: String, timestamp: Long)
//case class OrderEvent(orderId: String, eventType: String, txId: String, timestamp: Long)

object FlinkCep {
  def main(args: Array[String]): Unit = {
    // 创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

//    demo01(env)
    demo02(env)

    // 启动任务
    env.execute()
  }

  def demo01(env: StreamExecutionEnvironment): Unit = {
    // 使用CEP检测5秒内连续三次登录失败
    val inputStream: DataStream[LoginEvent] = env
      .readTextFile("input/LoginLog.csv")
      // 将流数据封装成样例类对象
      .map((data: String) => {
        // 5402,83.149.11.115,success,1558430815
        val words: Array[String] = data.split(",")
        LoginEvent(words(0), words(1), words(2), words(3).toLong * 1000)
      })
      // 提取事件时间生成水位线
      .assignTimestampsAndWatermarks(
        WatermarkStrategy.forBoundedOutOfOrderness[LoginEvent](Duration.ofSeconds(0))
          .withTimestampAssigner(new SerializableTimestampAssigner[LoginEvent] {
            override def extractTimestamp(element: LoginEvent, recordTimestamp: Long): Long = element.timestamp
          })
      )

    // 定义匹配模板,类似正则表达式(主要就是写这玩意)
    val pattern: Pattern[LoginEvent, LoginEvent] = Pattern
//      .begin[LoginEvent]("first fail").where((le: LoginEvent) => le.eventType == "fail")
//      .next("second fail").where((le: LoginEvent) => le.eventType == "fail")
//      .next("third fail").where((le: LoginEvent) => le.eventType == "fail")
//      .within(Time.seconds(5))
      // 事件类型相同的话可以.times简写成次数
      .begin[LoginEvent]("fail").where((le: LoginEvent) => le.eventType == "fail")
      .times(3).consecutive()
      .within(Time.seconds(5))

    // 将pattern应用到数据流
    val patternStream: PatternStream[LoginEvent] = CEP.pattern(inputStream.keyBy((r: LoginEvent) => r.userId), pattern)

    // select获取匹配事件
    patternStream
      .select(new PatternSelectFunction[LoginEvent, String] {
        override def select(pattern: util.Map[String, util.List[LoginEvent]]): String = {
          // 根据key获取value列表
          //    val firstFail: LoginEvent = pattern.get("first fail").get(0)
          //    val thirdFail: LoginEvent = pattern.get("third fail").get(0)
          val iter: util.Iterator[LoginEvent] = pattern.get("fail").iterator()
          val first: LoginEvent = iter.next()
          val second: LoginEvent = iter.next()
          val third: LoginEvent = iter.next()
          "用户 " + first.userId + " 在时间 " + first.timestamp + ", " + second.timestamp + ", " + third.timestamp + " 连续三次登录失败！"
        }
      })
      .print()
  }

  def demo02(env: StreamExecutionEnvironment): Unit = {
    // 使用CEP检测超时订单
    val inputStream: DataStream[OrderEvent] = env
      .readTextFile("input/OrderLog.csv")
      .map((data: String) => {
        // 34729,create,,1558430842 | 34729,pay,sd76f87d6,1558430844
        val words: Array[String] = data.split(",")
        OrderEvent(words(0), words(1), words(2), words(3).toLong * 1000)
      })
      // 提取事件时间生成水位线
      .assignTimestampsAndWatermarks(
        WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(0))
          .withTimestampAssigner(new SerializableTimestampAssigner[OrderEvent] {
            override def extractTimestamp(element: OrderEvent, recordTimestamp: Long): Long = element.timestamp
          })
      )

    // 定义匹配模板
    val pattern: Pattern[OrderEvent, OrderEvent] = Pattern
      .begin[OrderEvent]("create").where((oe: OrderEvent) => oe.eventType == "create")
      .followedBy("pay").where((oe: OrderEvent) => oe.eventType == "pay")
      .within(Time.minutes(15))

    // 将pattern应用到数据流
    val patternStream: PatternStream[OrderEvent] = CEP.pattern(inputStream.keyBy((oe: OrderEvent) => oe.orderId), pattern)

    // select获取匹配事件
    val result: DataStream[String] = patternStream.select(
      // 将超时订单放到侧输出流
      new OutputTag[String]("timeout"),
      // 超时订单
      (pattern: util.Map[String, util.List[OrderEvent]], timeoutTimestamp: Long) => {
        // 获取匹配事件
        val create: OrderEvent = pattern.get("create").iterator().next()
        "订单 " + create.orderId + " 已超时！当前时间为 " + timeoutTimestamp
      },
      // 正常订单
      new PatternSelectFunction[OrderEvent, String] {
        override def select(pattern: util.Map[String, util.List[OrderEvent]]): String = {
          // 获取匹配事件
          val pay: OrderEvent = pattern.get("pay").iterator().next()
          "订单 " + pay.orderId + " 已支付！"
        }
      }
    )

    // 非CEP的常规方式
    val result02: DataStream[String] = inputStream
      .keyBy((oe: OrderEvent) => oe.orderId)
      .process(new OrderCheck())

    result02.print()
    result02.getSideOutput(new OutputTag[String]("timeout")).print("output")
  }

}

// 自定义处理函数
class OrderCheck extends KeyedProcessFunction[String, OrderEvent, String] {
  // 声明状态,标识是否创建、是否支付、定时器
  var isCreate: ValueState[Boolean] = _
  var isPay: ValueState[Boolean] = _
  var timer: ValueState[Long] = _

  override def open(parameters: Configuration): Unit = {
    isCreate = getRuntimeContext.getState(new ValueStateDescriptor[Boolean]("is-create", classOf[Boolean]))
    isPay = getRuntimeContext.getState(new ValueStateDescriptor[Boolean]("is-pay", classOf[Boolean]))
    timer = getRuntimeContext.getState(new ValueStateDescriptor[Long]("timer", classOf[Long]))
  }

  override def processElement(value: OrderEvent, ctx: KeyedProcessFunction[String, OrderEvent, String]#Context, out: Collector[String]): Unit = {
    // 判断当前进来数据的事件类型
    if (value.eventType == "create") {
      // 判断是否已经支付
      if (isPay.value()) {
        // 已支付,正常输出,清空状态,删除定时器
        out.collect("订单 " + value.orderId + " 已支付")
        isCreate.clear()
        isPay.clear()
        timer.clear()
        ctx.timerService().deleteEventTimeTimer(timer.value())
      } else {
        // 未支付,注册15分钟后的定时器,更新状态
        val ts: Long = value.timestamp + 900 * 1000
        ctx.timerService().registerEventTimeTimer(ts)
        timer.update(ts)
        isCreate.update(true)
      }
    }

    if (value.eventType == "pay") {
      // 判断是否已经创建,防止有乱序数据
      if (isCreate.value()) {
        // 已创建,继续判断是否超时
        if (value.timestamp <= timer.value()) {
          out.collect("订单 " + value.orderId + " 已支付")
        } else {
          ctx.output(new OutputTag[String]("timeout"), "订单 " + value.orderId + " 已超时！当前时间为 " + value.timestamp)
        }
        // 清空状态,删除定时器
        isCreate.clear()
        isPay.clear()
        timer.clear()
        ctx.timerService().deleteEventTimeTimer(timer.value())
      } else {
        // 未创建,注册支付时间的定时器,更新状态
        ctx.timerService().registerEventTimeTimer(value.timestamp)
        isPay.update(true)
        timer.update(value.timestamp)
      }
    }
  }

  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[String, OrderEvent, String]#OnTimerContext, out: Collector[String]): Unit = {
    // pay来了,没等到create
    if (isPay.value()) {
      ctx.output(new OutputTag[String]("timeout"), "订单 " + ctx.getCurrentKey + " 未创建")
    } else {
      // create来了,没有pay
      ctx.output(new OutputTag[String]("timeout"), "订单 " + ctx.getCurrentKey + " 已超时！当前时间为 " + timestamp)
    }
    // 清空所有状态
    isCreate.clear()
    isPay.clear()
    timer.clear()
  }
}
