package com.okccc.common

import java.util.Properties

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
 * Author: okccc
 * Date: 2021/5/28 下午3:52
 * Desc:
 */
object MockRealTimeData {
  /**
    * 模拟生成实时数据
    * 时间点: 当前时间毫秒
    * userId: 0 - 99
    * 省份、城市 ID相同 ： 1 - 9
    * adid: 0 - 19
    * ((0L,"北京","北京"),(1L,"上海","上海"),(2L,"南京","江苏省"),(3L,"广州","广东省"),(4L,"三亚","海南省"),(5L,"武汉","湖北省"),(6L,"长沙","湖南省"),(7L,"西安","陕西省"),(8L,"成都","四川省"),(9L,"哈尔滨","东北省"))
    * 格式 ：timestamp province city userid adid
    * 某个时间点 某个省份 某个城市 某个用户 某个广告
    */
  def generateMockData(): Array[String] = {
    val array: ArrayBuffer[String] = ArrayBuffer[String]()
    val random: Random = new Random()
    // 模拟实时数据：
    // timestamp province city userid adid
    for (i <- 0 to 50) {
      val timestamp: Long = System.currentTimeMillis()
      val province: Int = random.nextInt(10)
      val city: Int = province
      val adid: Int = random.nextInt(20)
      val userid: Int = random.nextInt(100)
      // 拼接实时数据
      array += timestamp + " " + province + " " + city + " " + userid + " " + adid
    }
    array.toArray
  }

  def createKafkaProducer(broker: String): KafkaProducer[String, String] = {
    // 创建配置对象
    val prop: Properties = new Properties()
    // 添加配置
    prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker)
    prop.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    prop.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer")
    // 根据配置创建Kafka生产者
    new KafkaProducer[String, String](prop)
  }

  def main(args: Array[String]): Unit = {
    // 获取配置文件commerce.properties中的Kafka配置参数
    val broker: String = ConfigurationManager.config.getString("bootstrap.servers")
    val topic: String = ConfigurationManager.config.getString("kafka.topics")
    // 创建Kafka消费者
    val kafkaProducer: KafkaProducer[String, String] = createKafkaProducer(broker)
    while (true) {
      // 随机产生实时数据并通过Kafka生产者发送到Kafka集群中
      for (item <- generateMockData()) {
        kafkaProducer.send(new ProducerRecord[String, String](topic, item))
      }
      Thread.sleep(5000)
    }
  }
}