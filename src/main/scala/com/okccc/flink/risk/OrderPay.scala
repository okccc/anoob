package com.okccc.flink.risk

import java.util
import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.cep.scala.pattern.Pattern
import org.apache.flink.cep.scala.{CEP, PatternStream}
import org.apache.flink.cep.{PatternSelectFunction, PatternTimeoutFunction}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector

/**
 * Author: okccc
 * Date: 2021/4/7 10:16 上午
 * Desc: 订单超时检测,订单创建后15分钟内未支付就会超时,这个属于CEP里的宽松近邻场景
 */

// 输入数据样例类
case class OrderEvent(orderId: String, eventType: String, txId: String, timestamp: Long)
// 输出结果样例类,订单支付成功或者超时
case class OrderResult(orderId: String, msg: String)

object OrderPay {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度
    env.setParallelism(1)
    // 设置时间语义
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.Source操作
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    //    prop.put("key.deserializer", classOf[StringDeserializer])
    //    prop.put("value.deserializer", classOf[StringDeserializer])
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("order", new SimpleStringSchema(), prop))

    // 3.Transform操作
    // 将流数据封装成样例类对象
    val keyedStream: KeyedStream[OrderEvent, String] = inputStream
      .map((data: String) => {
        val words: Array[String] = data.split(",")
        OrderEvent(words(0), words(1), words(2), words(3).toLong)
      })
      // 提取事件时间生成水位线
      .assignAscendingTimestamps((oe: OrderEvent) => oe.timestamp * 1000)
      // 按照orderId分组
      .keyBy((oe: OrderEvent) => oe.orderId)

    // 使用CEP进行订单超时检测
    // 业务系统需要不停判断订单支付时间是否超时,类似618这种促销场景数据量很大时对系统压力很大,可以考虑使用低延迟高吞吐的flink处理
    // 1).定义模式序列pattern：订单创建15min内完成支付的是有效订单正常输出,未支付的是超时订单放到侧输出流
    val orderPayPattern: Pattern[OrderEvent, OrderEvent] = Pattern
      .begin[OrderEvent]("create").where((oe: OrderEvent) => oe.eventType == "create")
      .followedBy("pay").where((oe: OrderEvent) => oe.eventType == "pay")
      .within(Time.minutes(15))
    // 2).将pattern应用到数据流
    val patternStream: PatternStream[OrderEvent] = CEP.pattern(keyedStream, orderPayPattern)
    // 3).获取符合pattern的事件序列,可以设置侧输出流
    val outputTag: OutputTag[OrderResult] = new OutputTag[OrderResult]("orderTimeout")
//    val resultStream: DataStream[OrderResult] = patternStream.select(outputTag, new OrderTimeout(), new OrderSelect())

    // 使用非CEP的常规方式
    val resultStream: DataStream[OrderResult] = keyedStream.process(new OrderPayCheck())

    // 4.Sink操作
    resultStream.getSideOutput(outputTag).print("timeout")
    resultStream.print("payed")
    // 5.启动任务
    env.execute("order pay check")
  }
}

/*
 * 自定义模式超时函数
 * interface PatternTimeoutFunction<IN, OUT>
 * IN:  输入元素类型,OrderEvent
 * OUT: 输出结果类型,OrderResult
 */
class OrderTimeout() extends PatternTimeoutFunction[OrderEvent, OrderResult] {
  // 模式匹配到的事件序列保存在util.Map<String(事件名称), util.List(事件序列)>,timeoutTimestamp是匹配到的事件的超时时间
  override def timeout(pattern: util.Map[String, util.List[OrderEvent]], timeoutTimestamp: Long): OrderResult = {
    // 根据key获取value
    val orderEvent: OrderEvent = pattern.get("create").iterator().next()
    // 封装成样例类对象返回结果
    OrderResult(orderEvent.orderId, "order is timeout: " + timeoutTimestamp)
  }
}

/*
 * 自定义模式选择函数
 * interface PatternSelectFunction<IN, OUT>
 * IN:  输入元素类型,OrderEvent
 * OUT: 输出元素类型,OrderResult
 */
class OrderSelect() extends PatternSelectFunction[OrderEvent, OrderResult] {
  // 模式匹配到的事件序列保存在util.Map<String(事件名称), util.List(事件序列)>
  override def select(pattern: util.Map[String, util.List[OrderEvent]]): OrderResult = {
    // 根据key获取value
    val orderEvent: OrderEvent = pattern.get("pay").iterator().next()
    // 封装成样例类对象返回结果
    OrderResult(orderEvent.orderId, "order payed success!")
  }
}

/*
 * 自定义处理函数
 * abstract class KeyedProcessFunction<K, I, O>
 * K: 分组字段类型,orderId
 * I: 输入元素类型,OrderEvent
 * O: 输出元素类型,OrderResult
 */
class OrderPayCheck() extends KeyedProcessFunction[String, OrderEvent, OrderResult] {
  // 定义状态,标识是否创建、是否支付、定时器时间戳
  lazy val isCreatedState: ValueState[Boolean] = getRuntimeContext.getState(new ValueStateDescriptor[Boolean]("isCreated", classOf[Boolean]))
  lazy val isPayedState: ValueState[Boolean] = getRuntimeContext.getState(new ValueStateDescriptor[Boolean]("isPayed", classOf[Boolean]))
  lazy val timerState: ValueState[Long] = getRuntimeContext.getState(new ValueStateDescriptor[Long]("timer", classOf[Long]))

  // 设置侧输出流
  private val outputTag: OutputTag[OrderResult] = new OutputTag[OrderResult]("orderTimeout")

  /**
   * 处理每个进来的OrderEvent
   * @param value 输入元素类型,OrderEvent
   * @param ctx KeyedProcessFunction的内部类Context,提供了流的一些上下文信息,可以获取当前key和时间戳、注册定时服务、侧输出流等
   * @param out Collector接口提供了collect方法收集结果,可以输出0个或多个元素,processElement方法一般用不到out
   */
  override def processElement(value: OrderEvent, ctx: KeyedProcessFunction[String, OrderEvent, OrderResult]#Context, out: Collector[OrderResult]): Unit = {
    // 先获取当前状态
    val isCreated: Boolean = isCreatedState.value()
    val isPayed: Boolean = isPayedState.value()
    val timer: Long = timerState.value()

    // 判断进来的OrderEvent的事件类型
    // 1.如果进来的是create,要继续判断是否pay过
    if (value.eventType == "create") {
      // 1.1 已经支付,正常输出,清空所有状态,删除定时器
      if (isPayed) {
        out.collect(OrderResult(value.orderId, "order payed success!"))
        isCreatedState.clear()
        isPayedState.clear()
        timerState.clear()
        ctx.timerService().deleteEventTimeTimer(timer)
        // 1.2 还未支付,注册15分钟后的定时器,更新相关状态
      } else {
        val ts: Long = value.timestamp * 1000 + 900 * 1000
        ctx.timerService().registerEventTimeTimer(ts)
        timerState.update(ts)
        isCreatedState.update(true)
      }
    }
    // 2.如果进来的是pay,要判断是否create过,防止有乱序数据
    else if (value.eventType == "pay") {
      // 2.1 如果已经创建,要看下支付时间是否超时
      if (isCreated) {
        if (value.timestamp * 1000 < timer) {
          // 2.1.1 没有超时,正常输出
          out.collect(OrderResult(value.orderId, "order payed success!"))
        } else {
          // 2.1.2 超时了,放到侧输出流
          ctx.output(outputTag, OrderResult(value.orderId, "order timeout!"))
        }
        // 当前OrderEvent已经处理结束,清空所有状态,删除定时器
        isCreatedState.clear()
        isPayedState.clear()
        timerState.clear()
        ctx.timerService().deleteEventTimeTimer(timer)
        // 2.2 create还没来,注册pay时间的定时器,更新状态
      } else {
        val ts: Long = value.timestamp * 1000
        ctx.timerService().registerEventTimeTimer(ts)
        isPayedState.update(true)
        timerState.update(ts)
      }
    }
  }

  // 触发定时器是调用
  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[String, OrderEvent, OrderResult]#OnTimerContext, out: Collector[OrderResult]): Unit = {
    // 1.pay来了,没等到create
    if (isPayedState.value()) {
      ctx.output(outputTag, OrderResult(ctx.getCurrentKey, "payed but not found create!"))
    } else {
      // 2.create来了,没有pay
      ctx.output(outputTag, OrderResult(ctx.getCurrentKey, "order timeout!"))
    }
    // 清空所有状态
    isCreatedState.clear()
    isPayedState.clear()
    timerState.clear()
  }
}