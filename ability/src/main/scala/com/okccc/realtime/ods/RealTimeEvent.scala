package com.okccc.realtime.ods

import com.alibaba.fastjson.{JSON, JSONObject}
import com.okccc.realtime.common.Configs
import com.okccc.util.{HiveUtil, KafkaConsUtil, OffsetManageUtil}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
 * Author: okccc
 * Date: 2021/6/15 下午3:10
 * Desc: SparkStreaming实时读取flume采集到kafka的数据并解析写入hive
 */
object RealTimeEvent {

  def main(args: Array[String]): Unit = {
    // 参数校验
    if (args.length != 1) {
      println("Usage: Please input batchDuration(s)")
      System.exit(1)
    }

    // idea本地访问远程hive要设置hadoop用户名,不然报错 User okc does not have privileges for ALTERTABLE_ADDPARTS
//    System.setProperty("HADOOP_USER_NAME", "root")
    // 本地调试代码时参数不要写死,本地代码和提交到服务器的代码应该保持一致,建议在VM options添加配置
    // main方法参数配置：Edit Configurations - Main class(主类) - Program arguments(传参) - VM options(jvm配置)
    // idea本地调试spark代码：-Dspark.master=local[*] -DHADOOP_USER_NAME=hdfs -Dspark.sql.shuffle.partitions=1

    // 创建SparkSession对象
    val spark: SparkSession = SparkSession
      .builder()
      .appName("kafka-hive")
      .enableHiveSupport()
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .getOrCreate()
    // 获取SparkContext
    val sc: SparkContext = spark.sparkContext
    // 创建StreamingContext对象
    val ssc: StreamingContext = new StreamingContext(sc, Seconds(args(0).toLong))
    // 设置日志级别
    sc.setLogLevel("warn")

    // 看看能不能访问到hive
    spark.sql("show databases").show()
  }

}
