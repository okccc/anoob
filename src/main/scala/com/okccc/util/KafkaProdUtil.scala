package com.okccc.util

import java.util.Properties

import com.okccc.realtime.common.Configs
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

import scala.io.BufferedSource

/**
 * Author: okccc
 * Date: 2021/5/28 下午3:52
 * Desc: kafka生产者工具类
 */
object KafkaProdUtil {

  var producer: KafkaProducer[String, String] = _

  // 1.kafka生产者配置
  private val prop: Properties = new Properties()
  // 必选参数
//    prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Configs.get(Configs.BOOTSTRAP_SERVERS))  // kafka地址
  prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")  // kafka地址
  prop.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])            // key的序列化器
  prop.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])          // value的序列化器
  // 可选参数
  prop.put(ProducerConfig.ACKS_CONFIG, Configs.get(Configs.ACK))   // ack可靠性级别 0/1/-1(all)
  prop.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, Configs.get(Configs.IDEMPOTENCE)) // 开启幂等性机制

  // 2.创建Kafka生产者
  if (producer == null) {
    producer = new KafkaProducer[String, String](prop)
  }

    // 3.发送数据
  def sendData(topic: String, msg: String): Unit = {
    producer.send(new ProducerRecord[String, String](topic, msg))
  }

  def main(args: Array[String]): Unit = {
    // 读取文件数据写入kafka
    val source: BufferedSource = scala.io.Source.fromFile("input/UserBehavior.csv")  // 热门商品排行
//    val source: BufferedSource = scala.io.Source.fromFile("input/apache.log")  // 热门页面排行
    //    val source: BufferedSource = scala.io.Source.fromFile("input/AdClickLog.csv")  // 广告点击事件
//    val source: BufferedSource = scala.io.Source.fromFile("input/LoginLog.csv")  // 登录事件
    val source1: BufferedSource = scala.io.Source.fromFile("input/OrderLog.csv")  // 订单事件
    val source2: BufferedSource = scala.io.Source.fromFile("input/ReceiptLog.csv")  // 到账事件
    for (line <- source.getLines()) {
      println(line)
      sendData("nginx", line)
    }
//    for (line <- source1.getLines()) {
//      sendData("order", line)
//    }
//    for (line <- source2.getLines()) {
//      sendData("receipt", line)
//    }
  }

}