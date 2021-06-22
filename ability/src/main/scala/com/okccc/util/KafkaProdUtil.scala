package com.okccc.util

import java.util.Properties

import com.okccc.realtime.common.Config
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
    prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.get(Config.BOOTSTRAP_SERVERS))     // kafka地址
    prop.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])    // key的序列化器
    prop.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])  // value的序列化器
    // 可选参数
    prop.put(ProducerConfig.ACKS_CONFIG, Config.get(Config.ACK))   // ack可靠性级别 0/1/-1(all)
    prop.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, Config.get(Config.IDEMPOTENCE)) // 开启幂等性机制,配合ack=-1确保生产者exactly once

    // 2.创建Kafka生产者
    if (producer == null) {
      producer = new KafkaProducer[String, String](prop)
    }

    // 3.发送数据
  def sendData(topic: String, msg: String): Unit = {
    producer.send(new ProducerRecord[String, String](topic, msg))
  }


  def main(args: Array[String]): Unit = {
    // 获取topic
    val topic: String = Config.get(Config.NGINX_TOPICS)

//    while (true) {
//      // 随机生成实时数据
//      val sb: StringBuilder = new StringBuilder
//      // 生成时间
//      val time: String = DateTime.now.toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss"))
//      // 随机生成用户ID
//      val uid: String = "u-" + Random.nextInt(1000)
//      // 随机生成行为
//      val actions: Array[String] = Array("comment", "collect", "like", "follow", "coin")
//      val action: String = actions(Random.nextInt(5))
//      // 拼接字符串
//      sb.append(time + "\t" + uid + "\t" + action)
//      // 发送数据
//      sendData(topic, sb.toString())
//      Thread.sleep(100)
//    }

    // 读取文件数据写入kafka
    val bufferedSource: BufferedSource = scala.io.Source.fromFile("/Users/okc/projects/anoob/ability/input/UserBehavior.csv")
    //    val bufferedSource: BufferedSource = scala.io.Source.fromFile("/Users/okc/projects/anoob/ability/input/AdClickLog.csv")
    //    val bufferedSource: BufferedSource = scala.io.Source.fromFile("/Users/okc/projects/anoob/ability/input/apache.log")
    //    val bufferedSource: BufferedSource = scala.io.Source.fromFile("/Users/okc/projects/anoob/ability/input/LoginLog.csv")
    //    val bufferedSource01: BufferedSource = scala.io.Source.fromFile("/Users/okc/projects/anoob/ability/input/OrderLog.csv")
    //    val bufferedSource02: BufferedSource = scala.io.Source.fromFile("/Users/okc/projects/anoob/ability/input/ReceiptLog.csv")
    for (line <- bufferedSource.getLines()) {
      println(line)
      sendData(topic, line)
    }
    //    for (line <- bufferedSource02.getLines()) {
    //      println(line)
    //      sendMsg("tx", line)
    //    }
  }

}
