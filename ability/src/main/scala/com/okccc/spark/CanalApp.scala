package com.okccc.spark

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import com.okccc.util.{KafkaConsUtil, KafkaProdUtil, OffsetManageUtil}

/**
 * Author: okccc
 * Date: 2020/12/20 12:24 下午
 * Desc: 基于canal从kafka读取业务数据并分流写到ods层
 */
object CanalApp {
  def main(args: Array[String]): Unit = {
    // com.okccc.spark.streaming.stopGracefullyOnShutdown
    if (args.length != 1) {
      println("Usage: Please input batchDuration(s)")
      System.exit(1)
    }
    // 创建SparkSession对象
    val spark: SparkSession = SparkSession
      .builder()
      .appName("canal-kafka-com.okccc.spark-ods")
      // pom.xml添加spark-hive_2.11依赖,不然报错Unable to instantiate SparkSession with Hive support because Hive classes are not found.
      .enableHiveSupport()
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      //      .config("com.okccc.spark.serializer", "org.apache.com.okccc.spark.serializer.KryoSerializer")
      .getOrCreate()
    // 创建StreamingContext对象
    val ssc: StreamingContext = new StreamingContext(spark.sparkContext, Seconds(args(0).toLong))
    // 设置日志级别
    spark.sparkContext.setLogLevel("warn")

    // 指定topic和groupId
    val topicName: String = "canal"
    val groupId: String = "g"

    // ============================== 功能1.SparkStreaming读取kafka数据 =============================
    // 1.从redis读取偏移量起始点,只在程序启动时读取一次
    val offsetMap: Map[TopicPartition, Long] = OffsetManageUtil.getOffset(topicName, groupId)
    println(offsetMap) // 第一次读取应该是Map(),此时redis还没有g:canal这个key
    // 2.加载偏移量起始点处的kafka数据
    var recordDStream: InputDStream[ConsumerRecord[String, String]] = null
    if (offsetMap != null && offsetMap.nonEmpty) {
      // 如果redis已经有偏移量,就从偏移量处读取
      recordDStream = KafkaConsUtil.getKafkaDStream(ssc, topicName, groupId, offsetMap)
    } else {
      // 如果redis还没有偏移量,默认会从kafka本地topic __consumer_offsets的latest处读取
      recordDStream = KafkaConsUtil.getKafkaDStream(ssc, topicName, groupId)
    }
    // 3.获取本批次数据的偏移量起始点和结束点
    var offsetRanges: Array[OffsetRange] = null
    val offsetDStream: DStream[ConsumerRecord[String, String]] = recordDStream.transform((rdd: RDD[ConsumerRecord[String, String]]) => {
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    })
    // 4.转换DStream数据结构
    val jsonDStream: DStream[JSONObject] = offsetDStream.map((record: ConsumerRecord[String, String]) => {
      // ConsumerRecord对象的value部分是具体的record contents
      val jsonStr: String = record.value()
      // 将json字符串封装成json对象返回
      val jsonObj: JSONObject = JSON.parseObject(jsonStr)
      jsonObj
    })
    //    jsonDStream.print() // {"data":[{"user_id":"77","sku_name":"iphone12"...}],"type":"INSERT","database":"canal","table":"cart_info"...}

    // ============================== 功能2.将topic按表进行分流写入ods层 ==============================
    // 方式一：将topic由ip/database级别拆分到table级别,再按业务需求单独处理每个表,弊端是会多出来很多topic/partition/replication,生成大量冗余数据
    // 方式二：将topic直接按table分类写入对应的ods层hive表
    // 1.DStream输出操作通常由foreachRDD完成,RDD本身是一个集合,只不过存储的是逻辑抽象而不是具体数据
    jsonDStream.foreachRDD((rdd: RDD[JSONObject]) => {
      // 2.遍历元素
      rdd.foreach((jsonObj: JSONObject) => {
        // 3.解析JSONObject,获取table以及对应更新记录
        val mysqlTable: String = jsonObj.getString("table")
        val odsTopic: String = "ods_" + mysqlTable
        val records: JSONArray = jsonObj.getJSONArray("data")
        // 4.根据table分类写入对应topic
        for (i <- 0 until records.size()) {
          KafkaProdUtil.sendMsg(odsTopic, records.get(i).toString)
        }
      })
      // 5.处理完本批次数据之后要更新redis中的偏移量
      OffsetManageUtil.updateOffset(topicName, groupId, offsetRanges)
    })

    // 启动程序
    ssc.start()
    ssc.awaitTermination()
  }
}
