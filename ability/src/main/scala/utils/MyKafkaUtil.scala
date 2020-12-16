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
 * @author okccc
 * @date 2020/12/12 17:52
 * @desc SparkStreaming消费kafka的工具类
 */
object MyKafkaUtil {

  // 读取配置文件
  private val prop: Properties = MyPropertiesUtil.load("config.properties")
  private val brokers: String = prop.getProperty("kafka.broker.list")
  private val group: String = prop.getProperty("group.id")

  // kafka消费者配置,使用mutable.Map存储,方便更改参数
  private val kafkaParams: mutable.Map[String, Object] = collection.mutable.Map(
    // 必选参数
    "bootstrap.servers" -> brokers, // kafka集群地址
    "key.deserializer" -> classOf[StringDeserializer],  // key的反序列化器
    "value.deserializer" -> classOf[StringDeserializer],  // value的反序列化器
    "group.id" -> group, // 消费者组
    // 可选参数
    "auto.offset.reset" -> "latest", // 偏移量位置 latest(默认)/earliest/none
    "enable.auto.commit" -> (false: java.lang.Boolean) // true(默认)自动提交,false手动提交
  )

  // 创建从kafka获取数据的DStream,指定topic
  def getKafkaDStream(ssc: StreamingContext, topic: String): InputDStream[ConsumerRecord[String, String]] = {
    // kafka将每条消息都封装成ConsumerRecord对象
    val kafkaDStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,  // 位置策略
      ConsumerStrategies.Subscribe[String, String](Array(topic), kafkaParams)  // 消费策略
    )
    kafkaDStream
  }

  // 创建从kafka获取数据的DStream,指定topic/groupId
  def getKafkaDStream(ssc: StreamingContext, topic: String, groupId: String): InputDStream[ConsumerRecord[String, String]] = {
    // 手动传入groupId
    kafkaParams("group.id") = groupId
    val kafkaDStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream(
      ssc,
      LocationStrategies.PreferConsistent,
      ConsumerStrategies.Subscribe[String, String](Array(topic), kafkaParams)
    )
    kafkaDStream
  }

  // 创建从kafka获取数据的DStream,指定topic/groupId/offset
  def getKafkaDStream(ssc: StreamingContext, topic: String, groupId: String, offset: Map[TopicPartition, Long]): InputDStream[ConsumerRecord[String, String]] = {
    // 手动传入groupId
    kafkaParams("group.id") = groupId
    val kafkaDStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream(
      ssc,
      LocationStrategies.PreferConsistent,
      // 手动传入offset替换默认的latest
      ConsumerStrategies.Subscribe[String, String](Array(topic), kafkaParams, offset)
    )
    kafkaDStream
  }
}
