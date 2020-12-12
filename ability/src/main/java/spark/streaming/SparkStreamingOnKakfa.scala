package spark.streaming

import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Durations, StreamingContext}

/**
 * @author okccc
 * @date 2020/12/9 3:38 下午
 * @version 1.0
 */
object SparkStreamingOnKakfa {
  def main(args: Array[String]): Unit = {
    /**
     * SparkStreaming2.3版本读取kafka数据
     * kafka两个参数
     * heartbeat.interval.ms：kafka集群确保消费者保持连接的心跳通信间隔时间,默认3s.这个值必须设置的比session.timeout.ms小,一般设置不大于session.timeout.ms的1/3
     * session.timeout.ms：消费者与kafka之间的session会话超时时间,如果在这个时间内,kafka没有接收到消费者的心跳heartbeat.interval.ms,那么kafka将移除当前的消费者,默认30s
     * 这个时间位于配置 group.min.session.timeout.ms(6s) 和 group.max.session.timeout.m(300S)之间的一个参数,如果SparkSteaming 批次间隔时间大于5分钟,也就是大于300s,那么就要相应的调大group.max.session.timeout.ms 这个值
     * SparkStreaming读数据一般用 LocationStrategies.PreferConsistent 策略,将分区均匀的分布在集群的Executor之间
     * 如果Executor在kafka集群中的某些节点上,可以使用 LocationStrategies.PreferBrokers 策略,那么当前这个Executor中的数据会来自当前broker节点
     * 如果节点之间的分区有明显的分布不均,可以使用 LocationStrategies.PreferFixed 策略,通过map将topic分区分布在指定节点
     *
     * 新的消费者api可以将kafka中的消息预读取到缓存区中,默认大小为64k,默认缓存区在Executor中,加快处理数据速度
     * 可以通过参数 spark.streaming.kafka.consumer.cache.maxCapacity 调大,也可以通过spark.streaming.kafka.consumer.cache.enabled 设置成false 关闭缓存机制
     * 注意：官网中描述这里建议关闭,在读取kafka时如果开启会有重复读取同一个topic partition 消息的问题,报错：KafkaConsumer is not safe for multi-threaded access"
     *
     * 关于消费者offset
     * 1.设置checkpoint存储offset,缺点是可能重复消费
     * 2.kafka内置topic存储offset,设置enable.auto.commit=false,不然先提交后消费可能丢数据,缺点是kafka会定时清空offset
     * 3.自己存储offset,保证数据处理的事务,数据处理成功才保存,确保exactly once
     */

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
