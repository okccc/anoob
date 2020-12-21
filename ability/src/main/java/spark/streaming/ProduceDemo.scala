package spark.streaming

import java.util.Properties

import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.util.Random

/**
 * @author okccc
 * @date 2020/12/9 3:38 下午
 * @version 1.0
 */
object ProduceDemo {
  /**
   * 模拟生产者往kafka写数据
   */
  def main(args: Array[String]): Unit = {

    // 生产者属性配置
    val prop: Properties = new Properties()
    // 必选参数
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    prop.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")

    // 创建生产者对象
    val producer: KafkaProducer[String, String] = new KafkaProducer[String, String](prop)

    // 往kafka发数据
    var flag: Int = 0
    while (true) {
      flag += 1
      // 随机生成一条用户日志
      val content: String = userlog()
      // 将日志封装成ProducerRecord对象发送,并且可以添加回调函数,在producer收到ack时调用
      producer.send(new ProducerRecord("t_event", "key-" + flag, content), new Callback {
        override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
          // 没有异常说明发送成功
          if (exception == null) {
            // 获取消息的元数据信息
            println("topic=" + metadata.topic() +
              ",partition=" + metadata.partition() +
              ",offset=" + metadata.offset() +
              ",key=" + metadata.serializedKeySize() +
              ",value=" + metadata.serializedValueSize())
          } else {
            // 有异常就打印日志
            exception.printStackTrace()
          }
        }
      })
      // 发送间隔
      Thread.sleep(50)
    }

    // 关闭生产者
    producer.close()
  }

  def userlog(): String = {

    // 创建字符串缓冲区
    val sb: StringBuffer = new StringBuffer()

    // 生成时间
    val datetime: String = DateTime.now.toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss"))
    // 随机生成用户ID
    val uid: String = "u-" + Random.nextInt(1000)
    // 随机生成网站
    val websites: Array[String] = Array("京东", "淘宝", "拼多多", "美团", "叮咚买菜", "饿了么")
    val website: String = websites(Random.nextInt(6))

    // 往字符串缓冲区添加内容,拼接成一条日志
    sb.append(datetime).append("\t").append(uid).append("\t").append(website)
    // 返回这条日志
    sb.toString
  }

}
