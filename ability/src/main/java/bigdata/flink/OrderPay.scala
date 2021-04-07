package bigdata.flink

import java.util
import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.cep.{PatternSelectFunction, PatternTimeoutFunction}
import org.apache.flink.cep.scala.{CEP, PatternStream}
import org.apache.flink.cep.scala.pattern.Pattern
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
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

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
    // 1).定义pattern：订单创建15min内完成支付的是有效订单正常输出,未支付的是超时订单放到侧输出流
    val orderPayPattern: Pattern[OrderEvent, OrderEvent] = Pattern
      .begin[OrderEvent]("create").where((oe: OrderEvent) => oe.eventType == "create")
      .followedBy("pay").where((oe: OrderEvent) => oe.eventType == "pay")
      .within(Time.minutes(15))
    // 2).将pattern应用到数据流
    val patternStream: PatternStream[OrderEvent] = CEP.pattern(keyedStream, orderPayPattern)
    // 3).获取符合pattern的事件序列,可以设置侧输出流
    val outputTag: OutputTag[OrderResult] = new OutputTag[OrderResult]("orderTimeout")
    val resultStream: DataStream[OrderResult] = patternStream.select(outputTag, new OrderTimeout(), new OrderSelect())

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

