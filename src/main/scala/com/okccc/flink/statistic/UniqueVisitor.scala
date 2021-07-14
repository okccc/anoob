package com.okccc.flink.statistic

import java.lang
import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.scala.function.{ProcessWindowFunction, WindowFunction}
import org.apache.flink.streaming.api.scala.{DataStream, KeyedStream, StreamExecutionEnvironment, WindowedStream, createTypeInformation}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.triggers.{Trigger, TriggerResult}
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.util.Collector
import org.apache.kafka.common.serialization.StringDeserializer
import redis.clients.jedis.Jedis

/**
 * Author: okccc
 * Date: 2021/3/18 4:17 下午
 * Desc: 统计1小时内的独立访客数(UV=UniqueVisitor)
 */

// 定义输入数据样例类(同一个package下类名不能重复,只需定义一次即可)
//case class UserBehavior(userId: Long, itemId: Long, categoryId: Int, behavior: String, timestamp: Long)
// 定义输出结果样例类
case class UVCount(windowEnd: Long, count: Int)

object UniqueVisitor {
  def main(args: Array[String]): Unit = {
    /**
     * 缓存穿透
     * 生产环境中会把一些数据放到redis做缓存,查询请求过来的时候会先查缓存,有就直接返回没有就再去查数据库并把查询结果放入缓存
     * 但是如果有大量请求故意查询一些不存在的userId,既然不存在那么肯定没有缓存,去数据库也查询不到结果也就不会往redis写数据
     * 所以这些请求都会怼到数据库,redis本来是放在前面挡一挡请求减轻数据库压力的,现在redis形同虚设相当于不存在,这就是缓存穿透
     *
     * 海量数据去重
     * 一亿userId存储空间大小：10^8 * 10byte ≈ 1g 使用set集合存储对服务器内存压力很大,redis也消耗不起这么多数据
     * 布隆过滤器：10^8 * 1bit ≈ 10m, 1byte = 8bit 考虑hash碰撞可以给大一点空间比如20m,随便放内存还是redis都很轻松
     *
     * 布隆过滤器
     * 位图(bit数组)和hash函数(MD5,SHA-1..)组成的特殊数据结构,可以判断某个数据一定不存在(0)或可能存在(1),相较于List/Set/Map占用空间更少
     * hash碰撞：不同数据经过hash函数计算得到的hash值相同,会导致结果误判,可以增大位图容量和增加hash函数个数来降低碰撞概率
     * google提供的guava布隆过滤器是单机的,分布式项目可以使用redis的bitmap数据结构(本质上还是字符串)实现布隆过滤器
     */

    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置时间语义
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    // 设置并行度
//    env.setParallelism(1)

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
        val words: Array[String] = data.split(",")
        UserBehavior(words(0).toLong, words(1).toLong, words(2).toInt, words(3), words(4).toLong)
      })
      .assignAscendingTimestamps((u: UserBehavior) => u.timestamp * 1000)
    // 2).统计uv值
    val filterStream: DataStream[UserBehavior] = dataStream.filter((u: UserBehavior) => u.behavior == "pv")
    // 计算uv本身不涉及keyBy操作,DataStream只能使用timeWindowAll,所有数据都会发送到一个slot,并行度是1无法充分利用集群资源
    // 可以先map映射成("uv", userId)元组,再按元组的第一个字段"uv"进行分组得到KeyedStream,这样就可以使用timeWindow
    val mapStream: DataStream[(String, Long)] = filterStream.map((u: UserBehavior) => ("uv", u.userId))
    val keyedStream: KeyedStream[(String, Long), String] = mapStream.keyBy((t: (String, Long)) => t._1)
    // 不涉及刷新频率,分配滚动窗口即可
    val windowStream: WindowedStream[(String, Long), String, TimeWindow] = keyedStream
      .timeWindow(Time.hours(1))
      .trigger(new MyTrigger())  // 自定义触发器
    // 窗口关闭后的操作,由于数据量巨大,我不想在window里存任何数据,计算逻辑都放到redis
//    val resultStream: DataStream[UVCount] = windowStream.apply(new UVCountWindow())
    val resultStream: DataStream[UVCount] = windowStream.process(new UVCountWithBloom())

    // 4.Sink操作
    resultStream.print("uv")

    // 5.启动任务
    env.execute("unique visitor")
  }
}

// 自定义窗口函数
class UVCountWindow() extends WindowFunction[(String, Long), UVCount, String, TimeWindow] {
  override def apply(key: String, window: TimeWindow, input: Iterable[(String, Long)], out: Collector[UVCount]): Unit = {
    // 存放userId的set集合,假设一个userId占用10byte,如果userId有上亿的话就要占用1g内存,这里可以使用BloomFilter优化
    var userIdSet: Set[Long] = Set[Long]()
    // 遍历窗口数据
    for (tuple <- input) {
      // 将去重后的userId添加到集合
      userIdSet += tuple._2
    }
    // 收集结果封装成样例类对象
    out.collect(UVCount(window.getEnd, userIdSet.size))
  }
}

/*
 * 自定义Trigger触发器
 * abstract class Trigger<T, W extends Window>
 * T: Trigger操作的元素类型,("uv", userId)
 * W: 窗口分配器分配的窗口类型,一般都是时间窗口,W <: Window表示上边界,即W必须是Window类型或其子类
 */
class MyTrigger() extends Trigger[(String, Long), TimeWindow] {
  // 每来一条数据时触发的操作
  override def onElement(element: (String, Long), timestamp: Long, window: TimeWindow, ctx: Trigger.TriggerContext): TriggerResult = {
    TriggerResult.FIRE_AND_PURGE  // 触发窗口计算并清空窗口状态
  }
  // 系统时间更新时触发的操作
  override def onProcessingTime(time: Long, window: TimeWindow, ctx: Trigger.TriggerContext): TriggerResult = {
    TriggerResult.CONTINUE  // 什么都不做
  }
  // 事件时间更新时触发的操作
  override def onEventTime(time: Long, window: TimeWindow, ctx: Trigger.TriggerContext): TriggerResult = {
    TriggerResult.CONTINUE
  }
  // 收尾时的清理工作,这里啥也没定义
  override def clear(window: TimeWindow, ctx: Trigger.TriggerContext): Unit = {}
}

// 自定义布隆过滤器
class MyBloomFilter(size: Long) extends Serializable {
  // 定义位图大小,是2的整次幂,会放到redis而不是本地存储
  private val cap: Long = size
  // 设置hash函数
  def hash(value: String, seed: Int): Long = {
    var res: Long = 0
    // 对字符串的每个字符都做权重分配
    for(i <- 0 until value.length) {
      res = res * seed + value.charAt(i)
    }
    // 做一个位与操作,返回hash值,要映射到cap范围内  2^0 -1 = 00000001 -1 = 00000000 | 2^5 -1 = 00100000 -1 = 00011111
    (cap - 1) & res
  }
}

// 自定义全窗口处理函数
class UVCountWithBloom() extends ProcessWindowFunction[(String, Long), UVCount, String, TimeWindow] {
  // 定义redis连接以及布隆过滤器
  lazy val jedis: Jedis = new Jedis("localhost", 6379)
  // 位的个数：64M = 2^6(64) * 2^20(1M) * 2^3(8bit)1左位移29位就是2^29
  lazy val bloomFilter: MyBloomFilter = new MyBloomFilter(1<<29)

  // 本来是收集齐所有数据、窗口触发计算的时候才会调用；现在每来一条数据都调用一次
  override def process(key: String, context: Context, elements: Iterable[(String, Long)], out: Collector[UVCount]): Unit = {
    // 先定义redis中存储位图的key
    val storedBitMapKey: String = context.window.getEnd.toString

    // 另外将当前窗口的uv count值，作为状态保存到redis里，用一个叫做uvcount的hash表来保存（windowEnd，count）
    val uvCountMap: String = "uvcount"
    val currentKey: String = context.window.getEnd.toString
    var count: Long = 0L
    // 从redis中取出当前窗口的uv count值
    if(jedis.hget(uvCountMap, currentKey) != null)
      count = jedis.hget(uvCountMap, currentKey).toLong

    // 去重：判断当前userId的hash值对应的位图位置，是否为0
    val userId: String = elements.last._2.toString
    // 计算hash值，就对应着位图中的偏移量,随机给个sed种子61
    val offset: Long = bloomFilter.hash(userId, 61)
    // 用redis的位操作命令，取bitmap中对应位的值
    val isExist: lang.Boolean = jedis.getbit(storedBitMapKey, offset)
    if(!isExist){
      // 如果不存在，那么位图对应位置置1，并且将count值加1
      jedis.setbit(storedBitMapKey, offset, true)
      jedis.hset(uvCountMap, currentKey, (count + 1).toString)
    }
  }
}
