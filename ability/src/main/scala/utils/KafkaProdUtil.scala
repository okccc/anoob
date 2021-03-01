package utils

import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties

import scala.io.BufferedSource

/**
 * Author: okccc
 * Date: 2021/1/20 6:07 下午
 * Desc: kafka生产者工具类
 */
object KafkaProdUtil {

  var producer: KafkaProducer[String, String] = _

  // 1.kafka生产者配置
  private val prop: Properties = new Properties()
  // 必选参数
  prop.put("bootstrap.servers", "localhost:9092")          // kafka地址
  prop.put("key.serializer", classOf[StringSerializer])    // value序列化器
  prop.put("value.serializer", classOf[StringSerializer])  // value序列化器
  // 可选参数
  prop.put("acks", "all")                                  // ack可靠性级别 0/1/-1(all)
  prop.put("enable.idempotence", true: java.lang.Boolean)  // 开启幂等性机制,配合ack=-1确保生产者exactly once

  // 2.创建生产者对象
  if(producer == null) {
    producer = new KafkaProducer[String, String](prop)
  }

  // 3.发送数据,不指定partition也不指定key,会根据递增的随机数和partition数取余决定往哪个partition写数据
  def sendMsg(topicName: String, msg: String): Unit = {
    producer.send(new ProducerRecord[String, String](topicName, msg))
  }

  // 发送数据,不指定partition但是指定key,会按照key的hash值和partition数取余决定往哪个partition写数据
  def sendMsg(topicName: String, key: String, msg: String): Unit = {
    producer.send(new ProducerRecord[String, String](topicName, key, msg))
  }

  def main(args: Array[String]): Unit = {
    // 读取文件数据写入kafka
    val topicName: String = "nginx"
    val bufferedSource: BufferedSource = scala.io.Source.fromFile("/Users/okc/projects/anoob/ability/input/UserBehavior.csv")
    for (line <- bufferedSource.getLines()) {
      sendMsg(topicName, line)
    }
  }

}
