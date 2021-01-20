package utils

import java.util
import org.apache.kafka.common.TopicPartition
import org.apache.spark.streaming.kafka010.OffsetRange
import redis.clients.jedis.Jedis

import scala.collection.mutable

/**
 * Author: okccc
 * Date: 2020/12/21 6:14 下午
 * Desc: 手动维护offset的工具类
 */
object OffsetManageUtil {
  /**
   * 从redis读取offset
   * 存储offset的数据结构 type=Hash, key=groupId:topicName, field=partitionId, value=offset
   * @param topicName 主题名称
   * @param groupId 消费者组
   * @return Map[TopicPartition, Long] 主题的分区对应的偏移量
   */
  def getOffset(topicName: String, groupId: String): Map[TopicPartition, Long] = {
    // 1.拼接hash的key
    val offsetKey: String = groupId + ":" + topicName

    // 2.根据hash的key获取由field和value组成的Map<String, String>  g:start {0=10} {1=20} {2=30}
    // 获取redis客户端
    val jedis: Jedis = MyRedisUtil.getJedis()
    // Jedis是java写的,返回的集合也是java集合
    val offset_map: util.Map[String, String] = jedis.hgetAll(offsetKey)
    // 关闭连接
    jedis.close()

    // 3.转换结构
    // 导入scala集合转换器,允许通过asScala和asJava方法在java和scala之间相互转换集合
    import scala.collection.JavaConverters._
    val mutableMap: mutable.Map[String, String] = offset_map.asScala
    // map算子转换结构 mutable.Map[String, String] => Map[TopicPartition, Long]
    val offsetMap: Map[TopicPartition, Long] = mutableMap.toMap.map((t: (String, String)) => {
      // 获取Map[String, String]中的field和value
      val partitionId: String = t._1
      val offset: String = t._2
      println("读取分区偏移量: " + partitionId + " " + offset)
      // 将topic和partition封装成TopicPartition对象
      val topicPartition: TopicPartition = new TopicPartition(topicName, partitionId.toInt)
      // 返回新的Map集合
      (topicPartition, offset.toLong)
    })
    offsetMap
  }

  /**
   * 保存offset到redis
   * @param topicName 主题名称
   * @param groupId 消费者组
   * @param offsetRanges 偏移量范围数组
   */
  def saveOffset(topicName: String, groupId: String, offsetRanges: Array[OffsetRange]): Unit = {
    // 1.拼接hash的key和由field和value组成的util.Map<String, String>
    val offsetKey: String = groupId + ":" +topicName
    val offsetMap: util.HashMap[String, String] = new util.HashMap[String, String]()
    // 遍历偏移量范围数组
    for (offsetRange <- offsetRanges) {
      // 获取OffsetRange中的topic/partition/fromOffset/untilOffset
      val partitionId: Int = offsetRange.partition
      val fromOffset: Long = offsetRange.fromOffset
      val untilOffset: Long = offsetRange.untilOffset
      // 添加到Map集合
      offsetMap.put(partitionId.toString, untilOffset.toString)
      println("保存分区偏移量: " + partitionId + " " + fromOffset + "~" + untilOffset)
    }

    // 2.保存偏移量到redis
    if (offsetMap != null && offsetMap.size()>0) {
      // 获取redis客户端
      val jedis: Jedis = MyRedisUtil.getJedis()
      // 往hash写入key和对应的util.Map<String, String>
      jedis.hmset(offsetKey, offsetMap)
      // 关闭连接
      jedis.close()
    }
  }

  def main(args: Array[String]): Unit = {
    // 测试写入
    saveOffset("start", "g", Array(OffsetRange("start", 0, 0L, 10L)))
    // 测试读取
    val topicPartition: Map[TopicPartition, Long] = getOffset("start", "g")
    println(topicPartition)  // Map(start-0 -> 10)
  }
}