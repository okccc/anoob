package com.okccc.realtime.ods

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import com.okccc.realtime.common.Configs
import com.okccc.util.{CanalUtil, HiveUtil, KafkaConsUtil, KafkaProdUtil, OffsetManageUtil}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
 * @Author: okccc
 * @Date: 2020/12/20 12:24 下午
 * @Desc: SparkStreaming实时读取canal同步到kafka的业务数据并解析写入hive
 */
object CanalApp {
  def main(args: Array[String]): Unit = {

    // 参数校验
    if (args.length != 1) {
      println("Usage: Please input batchDuration(s)")
      System.exit(1)
    }

    // 创建SparkSession对象
    val spark: SparkSession = SparkSession
      .builder()
      .appName("mysql2hive")
      .enableHiveSupport()
      .config("spark.debug.maxToStringFields", 200)
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()
    // 获取SparkContext
    val sc: SparkContext = spark.sparkContext
    // 创建StreamingContext对象
    val ssc: StreamingContext = new StreamingContext(sc, Seconds(args(0).toLong))
    // 设置日志级别
    sc.setLogLevel("warn")

    // topic和groupId
    val topics: String = Configs.get(Configs.MYSQL_TOPICS)
    val groupId: String = Configs.get(Configs.MYSQL_GROUP_ID)
    // hive表和列
    val hiveTable: String = Configs.get(Configs.ORDERS_HIVE_TABLE)
    val hiveColumns: String = Configs.get(Configs.ORDERS_HIVE_COLUMNS)

    // ============================== 功能1.SparkStreaming读取kafka数据 =============================
    // 1.每次启动时从redis读取偏移量
    val offsetMap: Map[TopicPartition, Long] = OffsetManageUtil.getOffset(topics, groupId)
    println(offsetMap)  // 第一次读取是Map(),此时redis还没有g01:canal这个key

    // 2.加载偏移量起始点处的kafka数据
    var recordDStream: InputDStream[ConsumerRecord[String, String]] = null
    if (offsetMap != null && offsetMap.nonEmpty) {
      recordDStream = KafkaConsUtil.getKafkaDStream(ssc, topics, groupId, offsetMap)
    } else {
      recordDStream = KafkaConsUtil.getKafkaDStream(ssc, topics, groupId)
    }

    // 3.获取本批次数据的偏移量起始点和结束点
    var offsetRanges: Array[OffsetRange] = null
    val offsetDStream: DStream[ConsumerRecord[String, String]] = recordDStream.transform((rdd: RDD[ConsumerRecord[String, String]]) => {
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    })

    // 4.Transform操作
    val jsonDStream: DStream[JSONObject] = offsetDStream.map((record: ConsumerRecord[String, String]) => {
      // 获取ConsumerRecord对象的value部分
      val jsonStr: String = record.value()
      // json字符串 -> JSONObject
      JSON.parseObject(jsonStr)
    })

    // ============================== 功能2.将topic按表进行分流写入ods层 ==============================
    // 5.Output操作
    // 方式一：将topic由ip/database级别拆分到table级别(实时,每个表对应一个topic)
    jsonDStream.foreachRDD((rdd: RDD[JSONObject]) => {
      rdd.foreach((jsonObj: JSONObject) => {
        // 获取表名
        val tableName: String = jsonObj.getString("table")
        // 获取更新数据
        val dataArr: JSONArray = jsonObj.getJSONArray("data")
        if (tableName == "orders") {
          // 拼接topic
          val odsTopic: String = "ods_" + tableName
          // 发送数据
          import scala.collection.JavaConverters._
          for (data <- dataArr.asScala) {
            KafkaProdUtil.sendData(odsTopic, data.toString)
          }
        }
        // 6.处理完本批次数据后要更新redis中的偏移量(先消费后提交at least once)
        OffsetManageUtil.updateOffset(topics, groupId, offsetRanges)
      })
    })

    // 方式二：将topic直接按table分类写入对应ods层hive表(离线,每个表都得单独写一个类)
//    jsonDStream.foreachRDD((rdd: RDD[JSONObject]) => {
//      if (!rdd.isEmpty()) {
//        // 结构转换
//        val mapRDD: RDD[ArrayBuffer[ArrayBuffer[String]]] = rdd.map((jsonObj: JSONObject) => {
//          // JSONObject -> ArrayBuffer[String]
//          CanalUtil.parseJSONObjectToArrayBuffer(jsonObj, hiveTable)
//        })
//        // 扁平化操作
//        val flatmapRDD: RDD[ArrayBuffer[String]] = mapRDD.flatMap((ab: ArrayBuffer[ArrayBuffer[String]]) => ab)
//        // 以分区为单位处理RDD
//        val rowRDD: RDD[Row] = flatmapRDD.mapPartitions((partition: Iterator[ArrayBuffer[String]]) => {
//          // ArrayBuffer[String] -> Row
//          partition.map((ab: ArrayBuffer[String]) => Row.fromSeq(ab))
//        })
//        // 写入hive表
//        HiveUtil.write(rowRDD, spark, hiveTable, hiveColumns)
//        // 6.处理完本批次数据后要更新redis中的偏移量(先消费后提交at least once)
//        OffsetManageUtil.updateOffset(topics, groupId, offsetRanges)
//      }
//    })

    // 启动程序
    ssc.start()
    ssc.awaitTermination()
  }
}
