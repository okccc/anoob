package com.okccc.realtime.ods

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import com.okccc.realtime.common.Configs
import com.okccc.util.{KafkaConsUtil, KafkaProdUtil, OffsetManageUtil}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}

/**
 * Author: okccc
 * Date: 2020/12/20 12:24 下午
 * Desc: SparkStreaming实时读取canal同步到kafka的业务数据并解析写入hive
 */
object RealTimeMysql {
  def main(args: Array[String]): Unit = {

    // 参数校验
    if (args.length != 1) {
      println("Usage: Please input batchDuration(s)")
      System.exit(1)
    }

    // 创建SparkSession对象
    val sparkSession: SparkSession = SparkSession
      .builder()
      .appName("mysql2hive")
      .enableHiveSupport()
      .config("spark.debug.maxToStringFields", 200)
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()
    // 获取SparkContext
    val sc: SparkContext = sparkSession.sparkContext
    // 创建StreamingContext对象
    val ssc: StreamingContext = new StreamingContext(sc, Seconds(args(0).toLong))
    // 设置日志级别
    sc.setLogLevel("warn")

    // topic和groupId
    val topics: String = Configs.get(Configs.NGINX_TOPICS)
    val groupId: String = Configs.get(Configs.GROUP_ID)
    // hive表
    val table: String = Configs.get(Configs.HIVE_TABLE)
    val columns: String = Configs.get(Configs.HIVE_COLUMNS)

    // ============================== 功能1.SparkStreaming读取kafka数据 =============================
    // 1.每次启动时从redis读取偏移量
    val offsetMap: Map[TopicPartition, Long] = OffsetManageUtil.getOffset(topics, groupId)
    println(offsetMap)  // 第一次读取是Map(),此时redis还没有g01:canal这个key

    // 2.加载偏移量起始点处的kafka数据
    var recordDStream: InputDStream[ConsumerRecord[String, String]] = null
    if (offsetMap != null && offsetMap.nonEmpty) {
      recordDStream = KafkaConsUtil.getKafkaDStream(ssc, topics, offsetMap)
    } else {
      recordDStream = KafkaConsUtil.getKafkaDStream(ssc, topics)
    }

    // 3.获取本批次数据的偏移量起始点和结束点
    var offsetRanges: Array[OffsetRange] = null
    val offsetDStream: DStream[ConsumerRecord[String, String]] = recordDStream.transform((rdd: RDD[ConsumerRecord[String, String]]) => {
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    })

    // 4.Transform操作
    val jsonDStream: DStream[JSONObject] = offsetDStream.map(
      (record: ConsumerRecord[String, String]) => {
        // ConsumerRecord对象的value部分是具体的record contents
        val jsonStr: String = record.value()
        // 将json字符串封装成json对象返回
        val jsonObj: JSONObject = JSON.parseObject(jsonStr)
        jsonObj
      }
    )
    //    jsonDStream.print() // {"data":[{"user_id":"77","sku_name":"iphone12"...}],"type":"INSERT","database":"canal","table":"cart_info"...}

    // 5.Output操作

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
          KafkaProdUtil.sendData(odsTopic, records.get(i).toString)
        }
      })
      // 处理完本批次数据之后要更新redis中的偏移量
      OffsetManageUtil.updateOffset(topics, groupId, offsetRanges)
    })

    // 6.启动程序
    ssc.start()
    ssc.awaitTermination()
  }
}
