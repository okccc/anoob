package spark.streaming

import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Durations, StreamingContext}

/**
 * @author okccc
 * @date 2020/12/9 3:38 下午
 * @version 1.0
 */
object ConsumerDemo {
  def main(args: Array[String]): Unit = {

    // 创建spark配置信息
    val conf: SparkConf = new SparkConf().setMaster("local").setAppName("SparkStreamingOnKafka")
    // 创建SparkStreaming对象,指定批处理时间间隔
    val ssc: StreamingContext = new StreamingContext(conf, Durations.seconds(3))
    //设置日志级别
    ssc.sparkContext.setLogLevel("info")

    // kafka参数配置
    val kafkaParams: Map[String, Any] = Map(
      // 必选参数
      "bootstrap.servers" -> "localhost:9092", // kafka集群地址
      "key.deserializer" -> classOf[StringDeserializer], // key的反序列化器
      "value.deserializer" -> classOf[StringDeserializer], // value的反序列化器
      "group.id" -> "g01", // 消费者组
      // 可选参数
      "enable.auto.commit" -> false, // 关闭自动向kafka发送offset,改成异步处理完数据后手动提交
      "auto.offset.reset" -> "earliest" // earliest各分区下有已提交的offset就从提交的offset开始消费,没有就从头开始消费,latest重置偏移量为最大偏移量(默认),none没找到offset就抛异常
    )

    // topics集合
    val topics: Array[String] = Array("t01")

    // 创建流对象
  }
}
