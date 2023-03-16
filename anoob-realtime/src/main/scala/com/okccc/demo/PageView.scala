package com.okccc.demo

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.kafka.common.serialization.StringDeserializer

import java.sql.Timestamp
import java.time.Duration
import java.util.Properties
import scala.util.Random

/**
 * @Author: okccc
 * @Date: 2021/3/18 4:16 下午
 * @Desc: 实时统计1小时内的页面访问量(PV=PageView)
 */

// 输出结果样例类
case class PVCount(windowStart: Timestamp, windowEnd: Timestamp, count: Int)

object PageView {
  def main(args: Array[String]): Unit = {
    // 创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度,默认是cpu核数
//    env.setParallelism(1)

    // 获取数据源
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    prop.put("key.deserializer", classOf[StringDeserializer])
    prop.put("value.deserializer", classOf[StringDeserializer])
    env
      .addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))
      // 将流数据封装样例类
      .map((data: String) => {
        val words: Array[String] = data.split(",")
        UserBehavior(words(0), words(1), words(2), words(3), words(4).toLong * 1000)
      })
      .filter((u: UserBehavior) => u.behavior == "pv")
      // 提取时间戳生成水位线
      .assignTimestampsAndWatermarks(
        WatermarkStrategy.forBoundedOutOfOrderness[UserBehavior](Duration.ofSeconds(0))
          .withTimestampAssigner(new SerializableTimestampAssigner[UserBehavior] {
            override def extractTimestamp(element: UserBehavior, recordTimestamp: Long): Long =
              element.timestamp
          })
      )
      // 如果将数据都放到一条流,并行度是1无法充分利用集群资源,先将数据映射成("randomStr",1)再按照随机字符串分组
      // 生产中按照userId分组时,如果某个用户数据过多就会导致数据倾斜,此时也可以自定义map逻辑重新设计分组字段
//      .keyBy((_: UserBehavior) => 1)
      .map((_: UserBehavior) => (Random.nextString(10), 1))
      .keyBy((t: (String, Int)) => t._1)
      // 1小时的滚动窗口
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      // 统计每个slot里面每个窗口的访问量
      .aggregate(new PVCountAgg(), new PVWindowResult())
      // 再按照窗口分组
      .keyBy((pv: PVCount) => pv.windowEnd)
      // 将窗口内所有数据进行累加
      .process(new MyKeyedProcessFunction())
      .print()

    // 启动任务
    env.execute()
  }
}

// 自定义预聚合函数
class PVCountAgg extends AggregateFunction[(String, Int), Int, Int] {
  override def createAccumulator(): Int = 0
  override def add(value: (String, Int), accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = 0
}

// 自定义窗口函数
class PVWindowResult extends WindowFunction[Int, PVCount, String, TimeWindow] {
  override def apply(key: String, window: TimeWindow, input: Iterable[Int], out: Collector[PVCount]): Unit = {
    out.collect(PVCount(new Timestamp(window.getStart), new Timestamp(window.getEnd), input.iterator.next()))
  }
}

// 自定义处理函数
class MyKeyedProcessFunction() extends KeyedProcessFunction[Timestamp, PVCount, PVCount] {
  // 定义状态保存窗口的PV值
  var valueState: ValueState[Int] = _

  override def open(parameters: Configuration): Unit = {
    valueState = getRuntimeContext.getState(new ValueStateDescriptor[Int]("valueState", classOf[Int]))
  }

  override def processElement(value: PVCount, ctx: KeyedProcessFunction[Timestamp, PVCount, PVCount]#Context, out: Collector[PVCount]): Unit = {
    // 每来一条数据就更新状态值
    valueState.update(valueState.value() + value.count)
    // 注册一个windowEnd + 1(ms)之后触发的定时器
    ctx.timerService().registerEventTimeTimer(value.windowEnd.getTime + 1)
  }

  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[Timestamp, PVCount, PVCount]#OnTimerContext, out: Collector[PVCount]): Unit = {
    // 收集结果往下游发送
    out.collect(PVCount(new Timestamp(ctx.getCurrentKey.getTime - 3600 * 1000), ctx.getCurrentKey, valueState.value()))
    // 清空状态
    valueState.clear()
  }
}
