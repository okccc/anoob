package bigdata.flink.risk

import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.co.{CoProcessFunction, KeyedCoProcessFunction}
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector

/**
 * Author: okccc
 * Date: 2021/4/9 11:22 上午
 * Desc: 订单支付和账单流水进行对账
 */

// 定义输入数据样例类,订单事件
//case class OrderEvent(orderId: String, eventType: String, txId: String, timestamp: Long)
// 定义输入数据样例类,到账事件
case class ReceiptEvent(txId: String, payChannel: String, timestamp: Long)

object TxMatch {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.Source操作
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    //    prop.put("key.deserializer", classOf[StringDeserializer])
    //    prop.put("value.deserializer", classOf[StringDeserializer])
    val orderStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("order", new SimpleStringSchema(), prop))
    val txStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("tx", new SimpleStringSchema(), prop))

    // 3.Transform操作
    // 1).将流数据封装成样例类对象
    val orderEventStream: KeyedStream[OrderEvent, String] = orderStream
      .map((data: String) => {
        val words: Array[String] = data.split(",")
        OrderEvent(words(0), words(1), words(2), words(3).toLong)
      })
      // 提取事件时间生成水位线
      .assignAscendingTimestamps((oe: OrderEvent) => oe.timestamp * 1000)
      // 过滤支付事件
      .filter((oe: OrderEvent) => oe.eventType == "pay")
      // 按照账单号分组
      .keyBy((oe: OrderEvent) => oe.txId)

    val receiptEventStream: KeyedStream[ReceiptEvent, String] = txStream
      .map((data: String) => {
        val words: Array[String] = data.split(",")
        ReceiptEvent(words(0), words(1), words(2).toLong)
      })
      .assignAscendingTimestamps((re: ReceiptEvent) => re.timestamp * 1000)
      .keyBy((re: ReceiptEvent) => re.txId)

    // 2).合流操作
    val connectedStream: ConnectedStreams[OrderEvent, ReceiptEvent] = orderEventStream.connect(receiptEventStream)
    val resultStream: DataStream[(OrderEvent, ReceiptEvent)] = connectedStream.process(new TxPayMatch())

    // 4.Sink操作
    resultStream.print("matched")
    resultStream.getSideOutput(new OutputTag[OrderEvent]("unmatched-order")).print("unmatched order")
    resultStream.getSideOutput(new OutputTag[ReceiptEvent]("unmatched-receipt")).print("unmatched receipt")
    // 5.启动任务
    env.execute("tx match job")
  }
}

/*
 * 自定义合流处理函数
 * abstract class CoProcessFunction<IN1, IN2, OUT>
 * IN1: 输入元素1,OrderEvent
 * IN2: 输入元素2,ReceiptEvent
 * OUT: 输出结果,(OrderEvent, ReceiptEvent)
 */
class TxPayMatch() extends CoProcessFunction[OrderEvent, ReceiptEvent, (OrderEvent, ReceiptEvent)] {
  // 定义状态,保存当前交易对应的订单事件和到账事件,两条流相互之间数据肯定是乱序的谁等谁都有可能
  lazy val orderEventValueState: ValueState[OrderEvent] = getRuntimeContext.getState(new ValueStateDescriptor[OrderEvent]("order", classOf[OrderEvent]))
  lazy val receiptEventValueState: ValueState[ReceiptEvent] = getRuntimeContext.getState(new ValueStateDescriptor[ReceiptEvent]("tx", classOf[ReceiptEvent]))

  // 设置侧输出流
  private val outputTag01: OutputTag[OrderEvent] = new OutputTag[OrderEvent]("unmatched-order")
  private val outputTag02: OutputTag[ReceiptEvent] = new OutputTag[ReceiptEvent]("unmatched-receipt")

  // 处理订单事件输入流
  override def processElement1(value: OrderEvent, ctx: CoProcessFunction[OrderEvent, ReceiptEvent, (OrderEvent, ReceiptEvent)]#Context, out: Collector[(OrderEvent, ReceiptEvent)]): Unit = {
    // 订单事件来了,判断之前是否有到账事件
    if (receiptEventValueState.value() != null) {
      // 有就正常输出,清空状态
      out.collect((value, receiptEventValueState.value()))
      orderEventValueState.clear()
      receiptEventValueState.clear()
    } else {
      // 到账事件还没来,注册10秒后的定时器(具体时间视数据乱序程度而定)
      ctx.timerService().registerEventTimeTimer(value.timestamp * 1000 + 10000)
      // 更新状态
      orderEventValueState.update(value)
    }
  }

  // 处理到账事件输入流
  override def processElement2(value: ReceiptEvent, ctx: CoProcessFunction[OrderEvent, ReceiptEvent, (OrderEvent, ReceiptEvent)]#Context, out: Collector[(OrderEvent, ReceiptEvent)]): Unit = {
    // 到账事件来了,判断之前是否有订单事件
    if (orderEventValueState.value() != null) {
      // 有就正常输出,清空状态
      out.collect((orderEventValueState.value(), value))
      orderEventValueState.clear()
      receiptEventValueState.clear()
    } else {
      // 订单事件还没来,注册5秒后的定时器(具体时间视数据乱序程度而定)
      ctx.timerService().registerEventTimeTimer(value.timestamp * 1000 + 5000)
      // 更新状态
      receiptEventValueState.update(value)
    }
  }

  // 触发定时器时调用
  override def onTimer(timestamp: Long, ctx: CoProcessFunction[OrderEvent, ReceiptEvent, (OrderEvent, ReceiptEvent)]#OnTimerContext, out: Collector[(OrderEvent, ReceiptEvent)]): Unit = {
    // 判断一下哪个状态来了,说明另一个状态没来,放到侧输出流
    if (orderEventValueState.value() != null) {
      ctx.output(outputTag01, orderEventValueState.value())
    }
    if (receiptEventValueState.value() != null) {
      ctx.output(outputTag02, receiptEventValueState.value())
    }
    // 清空状态
    orderEventValueState.clear()
    receiptEventValueState.clear()
  }
}