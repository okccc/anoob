package utils

import java.util.Properties

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}

import scala.collection.mutable

/**
 * @author okccc
 * @date 2020/12/12 17:52
 * @desc kafka创建生产者和消费者的工具类
 */
object MyKafkaUtil {

  // 读取配置文件
  private val prop: Properties = MyPropertiesUtil.load("config.properties")
  private val broker_list: String = prop.getProperty("kafka.broker.list")
  private val group: String = prop.getProperty("group.id")

  var kafkaProducer: KafkaProducer[String, String] = _

  // kafka生产者配置
  private val producer_prop: Properties = new Properties()
  // 必选参数
  producer_prop.put("bootstrap.servers", broker_list)  // kafka集群地址
  producer_prop.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")  // key的序列化器
  producer_prop.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")  // value的序列化器
  // 可选参数
  producer_prop.put("enable.idempotence", true: java.lang.Boolean)  // 开启幂等性机制确保producer到broker的exactly once

  // kafka消费者配置,使用mutable.Map存储,方便更改参数
  private val kafkaParams: mutable.Map[String, Object] = collection.mutable.Map(
    // 必选参数
    "bootstrap.servers" -> broker_list, // kafka集群地址
    "key.deserializer" -> classOf[StringDeserializer],  // key的反序列化器
    "value.deserializer" -> classOf[StringDeserializer],  // value的反序列化器
    "group.id" -> group, // 消费者组
    // 可选参数
    "auto.offset.reset" -> "latest", // 没有offset时从哪里开始消费 latest(默认)/earliest/none
    "enable.auto.commit" -> (false: java.lang.Boolean) // true自动提交(默认),false手动提交
  )

  // 创建kafka生产者
  def getKafkaProducer: KafkaProducer[String, String] = {
    var producer: KafkaProducer[String, String] = null
    try {
      producer = new KafkaProducer[String, String](producer_prop)
    } catch {
      case e:Exception => e.printStackTrace()
    }
    producer
  }

  // 生产者往broker发送消息,指定topic/msg
  def send(topic: String, msg: String): Unit = {
    if (kafkaProducer == null) {
      val producer: KafkaProducer[String, String] = getKafkaProducer
      // 将发送的消息封装成ProducerRecord对象
      producer.send(new ProducerRecord[String, String](topic, msg))
    }
  }

  // 生产者往broker发送消息,指定topic/key/msg
  def send(topic: String, key: String, msg: String): Unit = {
    if (kafkaProducer == null) {
      val producer: KafkaProducer[String, String] = getKafkaProducer
      producer.send(new ProducerRecord[String, String](topic, key, msg))
    }
  }

  // 创建kafka消费者的DStream,指定topic
  def getKafkaDStream(ssc: StreamingContext, topic: String): InputDStream[ConsumerRecord[String, String]] = {
    // 将接收的消息封装成ConsumerRecord对象
    val kafkaDStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream[String, String](
      ssc,
      LocationStrategies.PreferConsistent,  // 位置策略
      ConsumerStrategies.Subscribe[String, String](Array(topic), kafkaParams)  // 消费策略
    )
    kafkaDStream
  }

  // 创建kafka消费者的DStream,指定topic/groupId
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

  // 创建kafka消费者的DStream,指定topic/groupId/offset
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
