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
   * 存储offset的数据结构 type=Hash, key=groupId:topicName, field=partitionId, value=offset
   * @param topicName 主题名称
   * @param groupId 消费者组
   * @return Map[TopicPartition, Long] 主题的分区对应的偏移量
   */
  def getOffset(topicName: String, groupId: String): Map[TopicPartition, Long] = {
    // 1.获取redis客户端
    val jedis: Jedis = MyRedisUtil.getJedis()

    // 2.根据hash的key获取所有field和value, g01:t01 "0" "10" "1" "20" "2" "30"
    val key: String = groupId + ":" + topicName
    val field_value: util.Map[String, String] = jedis.hgetAll(key)
    println(key + ", " + field_value)  // g01:t01, {0=10}

    // 3.关闭连接
    jedis.close()

    // 导入scala集合转换器,允许通过asScala和asJava方法在java和scala之间相互转换集合
    import scala.collection.JavaConverters._

//    val offsetMap1: Map[TopicPartition, String] = field_value.asScala.toMap.map {
//      // Map集合的参数是元组(K,V),元组里有两个参数scala无法识别,需要case转换一下,并且当参数不止一个时只能用{}不能用()
//      case (partitionId, offset) =>
//        val topicPartition: TopicPartition = new TopicPartition(topicName, partitionId.toInt)
//        (topicPartition, offset)
//    }

    // 4.将java集合转换成对应的scala集合,方便后续操作
    val mutableMap: mutable.Map[String, String] = field_value.asScala
    // map算子转换结构 mutable.Map[String, String] => Map[TopicPartition, Long]
    val offsetMap: Map[TopicPartition, Long] = mutableMap.toMap.map((t: (String, String)) => {
      // 获取Map[String, String]中的field和value
      val partitionId: String = t._1
      val offset: String = t._2
      // 将topic和partition封装成TopicPartition对象
      val topicPartition: TopicPartition = new TopicPartition(topicName, partitionId.toInt)
      // 返回新的Map集合
      (topicPartition, offset.toLong)
    })
    offsetMap
  }

  def main(args: Array[String]): Unit = {
    val topicPartition: Map[TopicPartition, Long] = getOffset("t01", "g01")
    println(topicPartition)  // Map(t01-0 -> 10)
  }
}
