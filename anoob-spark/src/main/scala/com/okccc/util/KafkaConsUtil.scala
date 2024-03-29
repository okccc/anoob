package com.okccc.util

import com.okccc.realtime.common.Configs
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka010.{ConsumerStrategies, KafkaUtils, LocationStrategies}

import scala.collection.mutable

/**
 * @Author: okccc
 * @Date: 2020/12/12 17:52
 * @Desc: SparkStreaming读取kafka的工具类
 */
object KafkaConsUtil {

  // 1.kafka消费者配置
  private val kafkaParams: mutable.Map[String, Object] = mutable.Map(
    // 必选参数
    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> Configs.get(Configs.BOOTSTRAP_SERVERS), // kafka地址
    //    ConsumerConfig.GROUP_ID_CONFIG -> Configs.get(Configs.GROUP_ID), // 消费者组应该随业务需求变化而变化
    ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer], // key的反序列化器
    ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer], // value的反序列化器
    // 可选参数
    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> Configs.get(Configs.ENABLE_AUTO_COMMIT), // true自动提交(默认),false手动提交
    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> Configs.get(Configs.AUTO_OFFSET_RESET), // 没有offset就从latest(默认)/earliest/none开始消费
    ConsumerConfig.MAX_POLL_RECORDS_CONFIG -> Configs.get(Configs.MAX_POLL_RECORDS), // 消费者每次poll拉取的最大消息条数
    ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG -> Configs.get(Configs.MAX_PARTITION_FETCH_BYTES) // 消费者获取数据的最大字节数
  )

  // 2.创建读取kafka数据的DStream
  def getKafkaDStream(ssc: StreamingContext, topics: String, groupId: String): InputDStream[ConsumerRecord[String, String]] = {
    kafkaParams("group.id") = groupId
    val recordDStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream[String, String](
      ssc,
      // 位置策略
      LocationStrategies.PreferConsistent,
      // 消费策略: 订阅topic集合,不设置offset,自动提交偏移量
      ConsumerStrategies.Subscribe[String, String](Array(topics), kafkaParams)
    )
    recordDStream
  }

  def getKafkaDStream(ssc: StreamingContext, topics: String, groupId: String, offsets: Map[TopicPartition, Long]): InputDStream[ConsumerRecord[String, String]] = {
    kafkaParams("group.id") = groupId
    val recordDStream: InputDStream[ConsumerRecord[String, String]] = KafkaUtils.createDirectStream(
      ssc,
      // 位置策略
      LocationStrategies.PreferConsistent,
      // 消费策略: 订阅topic集合,设置初始化启动时开始的offset,手动提交偏移量(常用)
      ConsumerStrategies.Subscribe[String, String](Array(topics), kafkaParams, offsets)
    )
    recordDStream
  }

}
