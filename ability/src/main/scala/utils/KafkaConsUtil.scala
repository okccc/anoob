package utils

import java.util.Properties

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}

import scala.collection.mutable

/**
 * Author: okccc
 * Date: 2020/12/12 17:52
 * Desc: kafka消费者工具类
 */
object KafkaConsUtil {

  // 1.读取配置文件
  private val prop: Properties = PropertiesUtil.load("config.properties")
  private val BOOTSTRAP_SERVERS: String = prop.getProperty("bootstrap.servers")
  private val GROUP_ID: String = prop.getProperty("group.id")

  // 2.kafka消费者配置
  private val kafkaParams: mutable.Map[String, Object] = mutable.Map(
    // 必选参数
    "bootstrap.servers" -> BOOTSTRAP_SERVERS,             // kafka地址
    "group.id" -> GROUP_ID,                               // 消费者组
    "key.deserializer" -> classOf[StringDeserializer],    // key反序列化器
    "value.deserializer" -> classOf[StringDeserializer],  // value反序列化器
    // 可选参数
    "enable.auto.commit" -> (false: java.lang.Boolean),   // true自动提交(默认),false手动提交
    "auto.offset.reset" -> "latest"                       // 没有offset就从latest(默认)/earliest/none开始消费
  )

  // 3.创建读取kafka数据的DStream,指定ssc/topicName
  def getKafkaDStream(ssc: StreamingContext, topicName: String): InputDStream[ConsumerRecord[String, String]] = {
    val recordDStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream[String, String](
      ssc,
      // 位置策略
      LocationStrategies.PreferConsistent,
      // 消费策略,自动提交偏移量
      ConsumerStrategies.Subscribe[String, String](Array(topicName), kafkaParams)
    )
    recordDStream
  }

  // 创建读取kafka数据的DStream,指定ssc/topicName/groupId
  def getKafkaDStream(ssc: StreamingContext, topicName: String, groupId: String): InputDStream[ConsumerRecord[String, String]] = {
    // 手动传入groupId
    kafkaParams("group.id") = groupId
    val recordDStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream(
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](Array(topicName), kafkaParams)
    )
    recordDStream
  }

  // 创建读取kafka数据的DStream,指定ssc/topicName/groupId/offset
  def getKafkaDStream(ssc: StreamingContext, topicName: String, groupId: String, offset: Map[TopicPartition, Long]): InputDStream[ConsumerRecord[String, String]] = {
    // 手动传入groupId
    kafkaParams("group.id") = groupId
    val recordDStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream(
      ssc,
      // 位置策略
      LocationStrategies.PreferConsistent,
      // 消费策略：手动提交偏移量
      ConsumerStrategies.Subscribe[String, String](Array(topicName), kafkaParams, offset)
    )
    recordDStream
  }
}
