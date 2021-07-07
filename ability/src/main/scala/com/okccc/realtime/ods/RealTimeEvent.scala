package com.okccc.realtime.ods

import java.util

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import com.okccc.realtime.common.Configs
import com.okccc.util.{DateUtil, HiveUtil, IPUtil, KafkaConsUtil, LogParseUtil, OffsetManageUtil, StringUtil}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}

import scala.collection.mutable.ArrayBuffer

/**
 * Author: okccc
 * Date: 2021/7/3 下午5:14
 * Desc: SparkStreaming实时读取flume同步到kafka的日志数据并解析写入hive
 */
object RealTimeEvent {
  def main(args: Array[String]): Unit = {

    /**
     * 常见错误
     * 1.java.net.UnknownHostException: jiliguala
     * idea本地访问远程hive要把hdfs-site.xml和hive-site.xml拷过来,不然报错
     *
     * 2.User okc does not have privileges for ALTERTABLE_ADDPARTS
     * idea本地访问远程hive要设置hadoop用户,本地用户没有权限 System.setProperty("HADOOP_USER_NAME", "root")
     * 建议本地调试代码时参数不要写死,本地代码和提交到服务器的代码应该保持一致,可以在VM options添加配置-DHADOOP_USER_NAME=hdfs
     * main方法参数配置：Edit Configurations - Main class(主类) - Program arguments(传参) - VM options(jvm配置)
     *
     * 3.A master URL must be set in your configuration
     * idea本地调试spark代码要指定master,可以在VM option添加配置-Dspark.master=local[*]
     *
     * 4.Truncated the string representation of a plan since it was too large.
     * This behavior can be adjusted by setting 'spark.debug.maxToStringFields' in SparkEnv.conf.
     * spark操作表字段数默认是25,可以设置spark.debug.maxToStringFields
     *
     * 5.spark-submit报错 java.lang.NoSuchMethodError: scala.Predef$.ArrowAssoc(Ljava/lang/Object;)Ljava/lang/Object
     * 这种错一般有两个原因,一个是真的缺jar包,二个是jar包冲突了(常见)
     * idea的scala插件,pom.xml的scala及spark/flink依赖引用的scala,服务器最终执行jar包的spark/flink命令引用的scala,三者要保持版本一致
     */

    // 参数校验
    if (args.length != 1) {
      println("Usage: Please input batchDuration(s)")
      System.exit(1)
    }

    // 创建SparkSession对象
    val spark: SparkSession = SparkSession
      .builder()
      .appName("kafka-hive")
      .enableHiveSupport()
      .config("spark.debug.maxToStringFields", 100)
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      // spark集成kafka会有序列化问题
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()
    // 获取SparkContext
    val sc: SparkContext = spark.sparkContext
    // 创建StreamingContext对象
    val ssc: StreamingContext = new StreamingContext(sc, Seconds(args(0).toLong))
    // 设置日志级别
    sc.setLogLevel("WARN")

    // 看看能不能访问到hive
//    spark.sql("show databases").show()

    // topic和groupId
    val topics: String = Configs.get(Configs.NGINX_TOPICS)
    val groupId: String = Configs.get(Configs.GROUP_ID)
    // hive表
    val table: String = Configs.get(Configs.HIVE_TABLE)
    val common: String = Configs.get(Configs.HIVE_COLUMNS_COMMON)
    val body: String = Configs.get(Configs.HIVE_COLUMNS_BODY)
    val e: String = Configs.get(Configs.HIVE_COLUMNS_E)

    // ==================== 功能1.SparkStreaming读取kafka数据 ====================
    // 1.每次启动时从redis读取偏移量
    val offsetMap: Map[TopicPartition, Long] = OffsetManageUtil.getOffset(topics, groupId)
    println(offsetMap)

    // 2.加载偏移量起始点处的kafka数据
    var inputDStream: InputDStream[ConsumerRecord[String, String]] = null
    if (offsetMap != null && offsetMap.nonEmpty) {
      inputDStream = KafkaConsUtil.getKafkaDStream(ssc, topics, offsetMap)
    } else {
      inputDStream = KafkaConsUtil.getKafkaDStream(ssc, topics)
    }

    // 3.获取本批次数据的偏移量起始点和结束点
    var offsetRanges: Array[OffsetRange] = null
    val offsetDStream: DStream[ConsumerRecord[String, String]] = inputDStream.transform((rdd: RDD[ConsumerRecord[String, String]]) => {
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    })



    // 6.启动程序
    ssc.start()
    ssc.awaitTermination()
  }
}
