package com.okccc.util

import java.util
import org.apache.kafka.common.TopicPartition
import org.apache.spark.streaming.kafka010.OffsetRange
import redis.clients.jedis.Jedis

/**
 * Author: okccc
 * Date: 2020/12/21 6:14 下午
 * Desc: 手动维护offset的工具类
 */
object OffsetManageUtil {

  /**
   * 从redis读取offset
   * 存储offset的数据结构 type=Hash, key=groupId:topicName, field=partition, value=offset  gg:start {0=10} {1=20} {2=30}
   * @param topicName 主题名称
   * @param groupId 消费者组
   * @return Map[TopicPartition, Long] 主题的分区对应的偏移量
   */
  def getOffset(topicName: String, groupId: String): Map[TopicPartition, Long] = {
    // 1.拼接hash的key
    val offsetKey: String = groupId + ":" + topicName

    // 2.获取key的所有Map<field, value>
    val jedis: Jedis = JedisUtil.getJedis()
    val offsetMap: util.Map[String, String] = jedis.hgetAll(offsetKey)
    jedis.close()

    // 3.转换数据结构
    // 导入scala集合转换器,允许通过asScala和asJava方法在java和scala之间相互转换数据结构
    import scala.collection.JavaConverters._
    // map算子转换结构 util.Map[String, String] -> Map[TopicPartition, Long]
    offsetMap.asScala.map((t: (String, String)) => {
      // 获取partition和offset
      val partitionId: String = t._1
      val offset: String = t._2
      println("读取分区偏移量：" + partitionId + " " + offset)
      // 将topic和partition封装成TopicPartition对象
      val topicPartition: TopicPartition = new TopicPartition(topicName, partitionId.toInt)
      // 返回新的Map集合
      (topicPartition, offset.toLong)
    }).toMap
  }

  /**
   * 保存offset到redis
   * @param topicName 主题名称
   * @param groupId 消费者组
   * @param offsetRanges 偏移量范围数组
   */
  def updateOffset(topicName: String, groupId: String, offsetRanges: Array[OffsetRange]): Unit = {
    // 1.拼接hash的key
    val offsetKey: String = groupId + ":" +topicName

    // 2.拼接由partition和offset组成的util.Map<field, value>
    val offsetMap: util.HashMap[String, String] = new util.HashMap[String, String]()
    // 遍历偏移量范围数组
    for (offsetRange <- offsetRanges) {
      // 获取OffsetRange中的topic/partition/fromOffset/untilOffset
      val partitionId: Int = offsetRange.partition
      val fromOffset: Long = offsetRange.fromOffset  // inclusive
      val untilOffset: Long = offsetRange.untilOffset  // exclusive
      // 添加到Map集合
      offsetMap.put(partitionId.toString, untilOffset.toString)
      println(DateUtil.getCurrentTime + " 更新分区偏移量: " + partitionId + " " + fromOffset + "~" + untilOffset)
    }

    // 3.更新数据
    if (offsetMap != null && offsetMap.size() > 0) {
      val jedis: Jedis = JedisUtil.getJedis()
      jedis.hmset(offsetKey, offsetMap)
      jedis.close()
    }
  }

  def main(args: Array[String]): Unit = {
    // 测试写入
    updateOffset("start", "g", Array(OffsetRange("start", 0, 0L, 10L)))
    // 测试读取
    val topicPartition: Map[TopicPartition, Long] = getOffset("start", "g")
    println(topicPartition)  // Map(start-0 -> 10)
  }
}