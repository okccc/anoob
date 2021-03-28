package bigdata.flink

import java.util.Properties

import org.apache.flink.api.common.functions.{AggregateFunction, MapFunction}
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.scala.function.WindowFunction
import org.apache.flink.streaming.api.scala.{DataStream, KeyedStream, StreamExecutionEnvironment, WindowedStream, createTypeInformation}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.kafka.common.serialization.StringDeserializer

import scala.util.Random

/**
 * Author: okccc
 * Date: 2021/3/18 4:16 下午
 * Desc: 统计1小时内的访问量(PV=PageView)
 */

// 定义输入数据样例类(同一个package下类名不能重复,只需定义一次即可)
//case class UserBehavior(userId: Long, itemId: Long, categoryId: Int, behavior: String, timestamp: Long)
// 定义输出统计结果的样例类
case class PVCount(windowEnd: Long, count: Int)

object PageView {
  def main(args: Array[String]): Unit = {
    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度,默认是cpu核数
//    env.setParallelism(1)
    // 设置时间语义
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.Source操作
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    prop.put("key.deserializer", classOf[StringDeserializer])
    prop.put("value.deserializer", classOf[StringDeserializer])
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.Transform操作
    // 1).将流数据封装成样例类对象,并提取事件时间生成watermark
    val dataStream: DataStream[UserBehavior] = inputStream
      .map((data: String) => {
        val words: Array[String] = data.split(",") // 543462,1715,1464116,pv,1511658000
        UserBehavior(words(0).toLong, words(1).toLong, words(2).toInt, words(3), words(4).toLong)
      })
      .assignAscendingTimestamps((u: UserBehavior) => u.timestamp * 1000)

    // 2).统计pv值
    val filterStream: DataStream[UserBehavior] = dataStream.filter((u: UserBehavior) => u.behavior == "pv")
    // 计算pv本身不涉及keyBy操作,DataStream只能使用timeWindowAll,所有数据都会发送到一个slot,并行度是1无法充分利用集群资源
    // 可以先map映射成("pv", 1)元组,再按元组的第一个字段"pv"进行分组得到KeyedStream,这样就可以使用timeWindow
//    val mapStream: DataStream[(String, Int)] = filterStream.map((_: UserBehavior) => ("pv", 1))
    val mapStream: DataStream[(String, Int)] = filterStream.map(new MyMapper())
    // 前面算子并行度都是cpu核数,到keyBy这里并行度只有1,因为所有数据都会分到"pv"这个slot,相当于还是timeWindowAll,所以map逻辑要优化成并行的
    // 生产环境中以userId/itemId作为key进行分组时,如果某个用户/商品的数据特别多就会导致数据倾斜,此时也可以自定义map逻辑重新设计key
    val keyedStream: KeyedStream[(String, Int), String] = mapStream.keyBy((t: (String, Int)) => t._1)
    // 不涉及刷新频率,分配滚动窗口即可
    val windowStream: WindowedStream[(String, Int), String, TimeWindow] = keyedStream.timeWindow(Time.hours(1))
    // 关闭窗口后的聚合操作
    val aggStream: DataStream[PVCount] = windowStream.aggregate(new PVCountAgg(), new PVCountWindow())
//    aggStream.print()

    // 3).将DatStream[PVCount]按照windowEnd分组聚合
    val keyedByWindowEndStream: KeyedStream[PVCount, Long] = aggStream.keyBy((pv: PVCount) => pv.windowEnd)
    // 将各个slot里的数据结果进行汇总得到最终的pv值
    val resultStream: DataStream[PVCount] = keyedByWindowEndStream.process(new PVCountResult())

    // 4.Sink操作
    resultStream.print("pv")

    // 5.启动任务
    env.execute("page view")
  }
}

// 自定义预聚合函数
class PVCountAgg() extends AggregateFunction[(String, Int), Int, Int] {
  override def createAccumulator(): Int = 0
  override def add(value: (String, Int), accumulator: Int): Int = accumulator + 1
  override def getResult(accumulator: Int): Int = accumulator
  override def merge(a: Int, b: Int): Int = a + b
}

// 自定义窗口函数
class PVCountWindow() extends WindowFunction[Int, PVCount, String, TimeWindow] {
  override def apply(key: String, window: TimeWindow, input: Iterable[Int], out: Collector[PVCount]): Unit = {
    // 收集结果封装成样例类对象
    out.collect(PVCount(window.getEnd, input.head))
  }
}

// 以下为数据并行优化的代码部分
// 自定义map函数
// interface MapFunction<T(输入元素类型), O(返回结果类型)>
class MyMapper() extends MapFunction[UserBehavior, (String, Int)] {
  override def map(value: UserBehavior): (String, Int) = {
    // 随机生成字符串作为分组的key,这样数据就会均匀分布
    (Random.nextString(10), 1)
    // 随机生成长度为10的字符串
    //    val alpha: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    //    val randomStr: String = (1 to 10).map((_: Int) => alpha(Random.nextInt.abs % alpha.length)).mkString
    //    (randomStr, 1)
  }
}

// 自定义处理函数
class PVCountResult() extends KeyedProcessFunction[Long, PVCount, PVCount] {
  // 先定义状态：每个窗口都应该有一个ValueState来保存当前窗口的pv值,ValueState接口体系包含value/update/clear等方法
  lazy val pvCountValueState: ValueState[Int] = getRuntimeContext.getState(new ValueStateDescriptor[Int]("pv", classOf[Int]))

  // 处理每个过来的PVCount
  override def processElement(value: PVCount, ctx: KeyedProcessFunction[Long, PVCount, PVCount]#Context, out: Collector[PVCount]): Unit = {
    // 每来一个数据就将count值叠加到当前状态
    pvCountValueState.update(pvCountValueState.value() + value.count)
    // 注册windowEnd + 1(ms)之后触发的定时器
    ctx.timerService().registerEventTimeTimer(value.windowEnd + 1)
  }

  // 触发定时器时调用
  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[Long, PVCount, PVCount]#OnTimerContext, out: Collector[PVCount]): Unit = {
    // 收集结果封装成样例类对象
    out.collect(PVCount(ctx.getCurrentKey, pvCountValueState.value()))
    // 清空状态
    pvCountValueState.clear()
  }
}