package utils

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

import java.util.Properties

/**
 * Author: okccc
 * Date: 2021/1/20 6:07 下午
 * Desc: kafka生产者工具类
 */
object KafkaProdUtil {

  var producer: KafkaProducer[String, String] = _

  // 1.读取配置文件
  private val prop: Properties = PropertiesUtil.load("config.properties")

  // 2.kafka生产者配置
  // 必选参数
  prop.put("bootstrap.servers", "localhost:9092")                  // kafka地址
  prop.put("key.serializer", classOf[StringSerializer].getName)    // key序列化器
  prop.put("value.serializer", classOf[StringSerializer].getName)  // value序列化器
  // 可选参数
  prop.put("acks", "all")                                          // ack可靠性级别 0/1/-1(all)
  prop.put("enable.idempotence", true:java.lang.Boolean)           // 开启幂等性机制,配合ack=-1确保生产者exactly once

  // 3.创建生产者对象
  def createKafkaProducer(): KafkaProducer[String, String] = {
    producer = new KafkaProducer[String, String](prop)
    producer
  }

  // 发送数据,不指定partition也不指定key,会根据递增的随机数和partition数取余决定往哪个partition写数据
  def send(topicName: String, msg: String): Unit = {
    if(producer == null) {
      producer = createKafkaProducer()
      producer.send(new ProducerRecord[String, String](topicName, msg))
    }
  }

  // 发送数据,不指定partition但是指定key,会按照key的hash值和partition数取余决定往哪个partition写数据
  def send(topicName: String, key: String, msg: String): Unit = {
    if(producer == null) {
      producer = createKafkaProducer()
      producer.send(new ProducerRecord[String, String](topicName, key, msg))
    }
  }

}
