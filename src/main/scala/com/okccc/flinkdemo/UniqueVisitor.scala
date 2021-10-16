package com.okccc.flinkdemo

import java.sql.Timestamp
import java.time.Duration
import java.util
import java.util.Properties

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.AggregateFunction
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.shaded.guava18.com.google.common.base.Charsets
import org.apache.flink.shaded.guava18.com.google.common.hash.{BloomFilter, Funnels}
import org.apache.flink.streaming.api.scala.function.ProcessWindowFunction
import org.apache.flink.streaming.api.scala.{StreamExecutionEnvironment, createTypeInformation}
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.kafka.common.serialization.StringDeserializer

/**
 * Author: okccc
 * Date: 2021/3/18 4:17 下午
 * Desc: 实时统计1小时内的独立访客数(UV=UniqueVisitor)
 */

// 输出结果样例类
case class UVCount(windowStart: Timestamp, windowEnd: Timestamp, count: Int)

object UniqueVisitor {
  def main(args: Array[String]): Unit = {
    // 创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度
    env.setParallelism(1)

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
        val arr: Array[String] = data.split(",")
        UserBehavior(arr(0), arr(1), arr(2), arr(3), arr(4).toLong * 1000)
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
      // 没有分组字段,将数据都放到一条流
      .keyBy((_: UserBehavior) => 1)
      // 1小时的滚动窗口
      .window(TumblingEventTimeWindows.of(Time.hours(1)))
      // 先增量聚合再全窗口处理,因为所有数据都在一个slot,所以不需要再按照窗口分组聚合
      .aggregate(new UVCountAggWithBloomFilter(), new UVWindowResult())
      .print()

    // 启动任务
    env.execute()
  }
}

// 自定义增量聚合函数
// 计算UV要去重所以使用HashSet存放userId,都放在HashSet会消耗大量内存,此处可以用BloomFilter优化
class UVCountAgg extends AggregateFunction[UserBehavior, util.HashSet[String], Int] {
  override def createAccumulator(): util.HashSet[String] = new util.HashSet[String]()
  override def add(value: UserBehavior, accumulator: util.HashSet[String]): util.HashSet[String] = {
    accumulator.add(value.userId)
    accumulator
  }
  override def getResult(accumulator: util.HashSet[String]): Int = accumulator.size()
  override def merge(a: util.HashSet[String], b: util.HashSet[String]): util.HashSet[String] = null
}

// 由于BloomFilter没有.size这种api,可以借助元组(f0, f1)的第一个参数求BloomFilter的大小
class UVCountAggWithBloomFilter extends AggregateFunction[UserBehavior, (Int, BloomFilter[String]), Int] {
  override def createAccumulator(): (Int, BloomFilter[String]) = {
    // 创建布隆过滤器,设置要去重的数据量和误差率
    (0, BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 100000, 0.01))
  }
  override def add(value: UserBehavior, accumulator: (Int, BloomFilter[String])): (Int, BloomFilter[String]) = {
    // 如果布隆过滤器一定不包含当前userId就将其添加进来
    if (!accumulator._2.mightContain(value.userId)) {
      // put操作就是将字符串传入hash函数,将位图置为1
      accumulator._2.put(value.userId)
      return (accumulator._1 + 1, accumulator._2)
    }
    accumulator
  }
  override def getResult(accumulator: (Int, BloomFilter[String])): Int = accumulator._1
  override def merge(a: (Int, BloomFilter[String]), b: (Int, BloomFilter[String])): (Int, BloomFilter[String]) = null
}

// 自定义全窗口函数
class UVWindowResult extends ProcessWindowFunction[Int, UVCount, Int, TimeWindow] {
  override def process(key: Int, context: Context, elements: Iterable[Int], out: Collector[UVCount]): Unit = {
    out.collect(UVCount(new Timestamp(context.window.getStart), new Timestamp(context.window.getEnd), elements.iterator.next()))
  }
}