package utils

import java.util

import org.apache.kafka.common.TopicPartition
import redis.clients.jedis.Jedis

import scala.collection.mutable

/**
 * Author: okccc
 * Date: 2020/12/21 6:14 下午
 * Desc: 手动维护offset的工具类
 */
object MyOffsetUtil {

  /**
   * 从redis读取offset
   * redis数据格式：type->Hash  key->offset:topic:groupId  field->partitionId  value->offset
   * @param topic 主题名称
   * @param groupId 消费者组
   * @return Map[TopicPartition, Long] 当前消费者组中消费的主题对应的分区的偏移量信息
   */
  def getOffset(topic: String, groupId: String): Map[TopicPartition, Long] = {
    // 1.获取redis客户端
    val jedis: Jedis = MyRedisUtil.getJedis()
    // 2.根据hash的key获取所有field和value, offset:t01:g01  "0" "10" "1" "20" "2" "30"
    val offsetKey: String = "offset:" + topic + ":" + groupId
    val offsetMap: util.Map[String, String] = jedis.hgetAll(offsetKey)
    println(offsetKey + " " + offsetMap)
    // 3.关闭连接
    jedis.close()

    // 导入scala集合转换器,允许通过asScala和asJava方法在java和scala之间相互转换集合
    import scala.collection.JavaConverters._

    // 4.将java的Map转换成scala的Map,方便后续操作
    //    val kafkaOffsetMap: Map[TopicPartition, Long] = offsetMap.asScala.map {
    //      // Map集合的参数是一个元组(K,V),元组里有两个参数scala无法识别,需要case转换一下,并且当参数不止一个时只能用{}不能用()
    //      case (partitionId, offset) => {
    //        println("partitionId=" + partitionId + ", offset=" + offset)
    //        // 将redis保存的分区对应的偏移量进行封装
    //        val topicPartition: TopicPartition = new TopicPartition(topic, partitionId.toInt)
    //        (topicPartition, offset.toLong)
    //      }
    //    }.toMap
    val offset_map: mutable.Map[String, String] = offsetMap.asScala
    // 转换结构：mutable.Map[String, String] => Map[TopicPartition, Long]
    val kafkaOffsetMap: Map[TopicPartition, Long] = offset_map.map((t: (String, String)) => {
      // 获取Map[field, value]集合中的field=>partitionId和value=>offset
      val partitionId: String = t._1
      val offset: String = t._2
      println("partitionId=" + partitionId + ", offset=" + offset)
      // 将topic和partition封装成TopicPartition对象
      val topicPartition: TopicPartition = new TopicPartition(topic, partitionId.toInt)
      // 返回新的Map集合
      (topicPartition, offset.toLong)
    }).toMap
    kafkaOffsetMap
  }
}
