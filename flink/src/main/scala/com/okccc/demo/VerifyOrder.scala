package com.okccc.demo

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.streaming.api.scala._
import org.apache.flink.util.Collector

import java.time.Duration

/**
 * Author: okccc
 * Date: 2021/4/9 11:22 上午
 * Desc: 订单支付和账单流水实时对账
 */

// 订单事件样例类
case class OrderEvent(orderId: String, eventType: String, txId: String, timestamp: Long)
// 到账事件样例类
case class ReceiptEvent(txId: String, payChannel: String, timestamp: Long)

object VerifyOrder {
  def main(args: Array[String]): Unit = {
    // 创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    // 获取数据源
    val orderStream: DataStream[OrderEvent] = env
      .readTextFile("input/OrderLog.csv")
      .map((data: String) => {
        // 34729,pay,sd76f87d6,1558430844
        val arr: Array[String] = data.split(",")
        OrderEvent(arr(0), arr(1), arr(2), arr(3).toLong * 1000)
      })
      .filter((oe: OrderEvent) => oe.eventType == "pay")
      .assignTimestampsAndWatermarks(
        WatermarkStrategy.forBoundedOutOfOrderness[OrderEvent](Duration.ofSeconds(0))
          .withTimestampAssigner(new SerializableTimestampAssigner[OrderEvent] {
            override def extractTimestamp(element: OrderEvent, recordTimestamp: Long): Long =
              element.timestamp
          })
      )
    val receiptStream: DataStream[ReceiptEvent] = env
      .readTextFile("input/ReceiptLog.csv")
      .map((data: String) => {
        // ewr342as4,wechat,1558430845
        val arr: Array[String] = data.split(",")
        ReceiptEvent(arr(0), arr(1), arr(2).toLong * 1000)
      })
      .assignTimestampsAndWatermarks(
        WatermarkStrategy.forBoundedOutOfOrderness[ReceiptEvent](Duration.ofSeconds(0))
          .withTimestampAssigner(new SerializableTimestampAssigner[ReceiptEvent] {
            override def extractTimestamp(element: ReceiptEvent, recordTimestamp: Long): Long =
              element.timestamp
          })
      )

    // 双流合并
    val result: DataStream[String] = orderStream
      .keyBy((oe: OrderEvent) => oe.txId)
      .connect(receiptStream.keyBy((re: ReceiptEvent) => re.txId))
      .process(new MyCoProcessFunction())

    result.print("match")
    result.getSideOutput(new OutputTag[OrderEvent]("order")).print("unmatch-order")
    result.getSideOutput(new OutputTag[ReceiptEvent]("receipt")).print("unmatch-receipt")

    // 启动任务
    env.execute()
  }
}

// 自定义合流处理函数
class MyCoProcessFunction extends CoProcessFunction[OrderEvent, ReceiptEvent, String] {
  // 声明状态,保存订单事件和到账事件,两条流相互之间数据肯定是乱序的谁等谁都有可能
  var orderState: ValueState[OrderEvent] = _
  var receiptState: ValueState[ReceiptEvent] = _

  override def open(parameters: Configuration): Unit = {
    orderState = getRuntimeContext.getState(new ValueStateDescriptor[OrderEvent]("order", classOf[OrderEvent]))
    receiptState = getRuntimeContext.getState(new ValueStateDescriptor[ReceiptEvent]("receipt", classOf[ReceiptEvent]))
  }

  override def processElement1(value: OrderEvent, ctx: CoProcessFunction[OrderEvent, ReceiptEvent, String]#Context, out: Collector[String]): Unit = {
    // 订单事件进来了,判断之前是否有到账事件
    if (receiptState.value != null) {
      // 有就正常输出,清空状态
      out.collect("账单ID " + value.txId + " 对账成功")
      orderState.clear()
      receiptState.clear()
    } else {
      // 到账事件还没来,注册10秒后的定时器(具体时间视数据乱序程度而定)
      ctx.timerService.registerEventTimeTimer(ctx.timerService.currentWatermark + 10 * 1000)
      // 更新订单事件状态
      orderState.update(value)
    }
  }

  override def processElement2(value: ReceiptEvent, ctx: CoProcessFunction[OrderEvent, ReceiptEvent, String]#Context, out: Collector[String]): Unit = {
    // 到账事件进来了,判断之前是否有订单事件
    if (orderState.value != null) {
      // 有就正常输出,清空状态
      out.collect("账单ID " + value.txId + " 对账成功")
      orderState.clear()
      receiptState.clear()
    }
    else {
      // 订单事件还没来,注册5秒后的定时器(具体时间视数据乱序程度而定)
      ctx.timerService.registerEventTimeTimer(ctx.timerService.currentWatermark + 5 * 1000)
      // 更新到账事件状态
      receiptState.update(value)
    }
  }

  override def onTimer(timestamp: Long, ctx: CoProcessFunction[OrderEvent, ReceiptEvent, String]#OnTimerContext, out: Collector[String]): Unit = {
    // 判断一下哪个状态来了,说明另一个状态没来
    if (orderState.value() != null) {
      // 放到侧输出流
      ctx.output(new OutputTag[OrderEvent]("order"), orderState.value())
    }
    if (receiptState.value() != null) {
      // 放到侧输出流
      ctx.output(new OutputTag[ReceiptEvent]("receipt"), receiptState.value())
    }

    // 清空状态
    orderState.clear()
    receiptState.clear()
  }
}