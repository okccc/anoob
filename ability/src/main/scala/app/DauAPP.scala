package app

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.spark.SparkConf
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.{Seconds, StreamingContext}
import utils.MyKafkaUtil

/**
 * @author okccc
 * @date 2020/12/13 11:24
 * @desc 日活统计
 */
object DauAPP {
  def main(args: Array[String]): Unit = {
    // 创建spark配置信息
    val conf: SparkConf = new SparkConf().setMaster("local").setAppName("DauApp")
    // 创建StreamingContext对象
    val ssc: StreamingContext = new StreamingContext(conf, Seconds(5))
    // 设置日志级别
    ssc.sparkContext.setLogLevel("info")

    // 指定kafka的topic和groupId
    val topic: String = "t01"
    val groupId: String = "g01"

    // 功能1：从kafka中读取数据
    val kafkaDStream: InputDStream[ConsumerRecord[String, String]] = MyKafkaUtil.getKafkaStream(ssc, topic, groupId)


    // 功能2：通过redis对数据去重



    // 功能3：将日活数据保存到es

  }
}
