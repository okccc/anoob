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
      .config("spark.debug.maxToStringFields", 200)
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

    // 4.Transform操作
    // 获取ConsumerRecord的value部分
    val recordDStream: DStream[String] = offsetDStream.map((record: ConsumerRecord[String, String]) => record.value())
    // 由于request_body里面的e字段是数组,要先拆解开再拼接成一个个完整字符串,不然后面不好解析
    val arrayBufferDStream: DStream[ArrayBuffer[JSONObject]] = recordDStream.map((str: String) => {
      // 存放json对象的数组
      val arrayBuffer: ArrayBuffer[JSONObject] = new ArrayBuffer[JSONObject]
      // 解析字符串
      if (LogParseUtil.isJsonFormat(str)) {
        // 最外部的大json串
        val jsonObj: JSONObject = JSON.parseObject(str)
        // 获取common字段
        val hm: util.HashMap[String, Object] = new util.HashMap[String, Object]()
        hm.put("server_time", jsonObj.getString("@timestamp"))
        hm.put("ip", jsonObj.getString("ip"))
        hm.put("method", jsonObj.getString("Method"))
        // 获取body字段
        val request_body: String = jsonObj.getString("request_body")
        // v=3&new_log_flag=true&client=234406d28efbc2041bc1f58a501cad17&e=...
        if (request_body.length > 2) {
          val map: util.HashMap[String, String] = StringUtil.strToMap(request_body)
          for (field <- body.split(",")) {
            hm.put(field, map.get(field))
          }
          // 获取e字段 e=[{},{},{}]
          val e: String = map.get("e")
          // 解码
          val jsonArr: JSONArray = JSON.parseArray(LogParseUtil.decode(e))
          for (i <- 0 until jsonArr.size()) {
            // 包含多个小json串对象
            val jsonObj: JSONObject = JSON.parseObject(jsonArr.get(i).toString)
            // 添加common字段拼接成完整json对象
            jsonObj.fluentPutAll(hm)
            // 将该json对象添加到数组
            arrayBuffer.append(jsonObj)
          }
        }
      }
      arrayBuffer
    })

    // 5.Output操作
    arrayBufferDStream.foreachRDD((rdd: RDD[ArrayBuffer[JSONObject]]) => {
      if (!rdd.isEmpty()) {
        // 扁平化操作
        val jsonRDD: RDD[JSONObject] = rdd.flatMap((ab: ArrayBuffer[JSONObject]) => ab)
        // 以分区为单位处理RDD
        val rowRDD: RDD[Row] = jsonRDD.mapPartitions((partition: Iterator[JSONObject]) => {
          partition.map((jsonObj: JSONObject) => {
            // 解析所有字段
            val server_time: String = jsonObj.getString("server_time")
            val ip: String = jsonObj.getString("ip")
            val method: String = jsonObj.getString("method")
            val v: String = jsonObj.getString("Method")
            val client: String = jsonObj.getString("client")
            val upload_time: String = jsonObj.getString("upload_time")
            val checksum: String = jsonObj.getString("checksum")
            val session_id: String = jsonObj.getString("session_id")
            val user_properties: String = jsonObj.getString("user_properties")
            val language: String = jsonObj.getString("language")
            val event_type: String = jsonObj.getString("event_type")
            val sequence_number: String = jsonObj.getString("sequence_number")
            val user_id: String = jsonObj.getString("user_id")
            val country: String = jsonObj.getString("country")
            val api_properties: String = jsonObj.getString("api_properties")
            val device_id: String = jsonObj.getString("device_id")
            val event_properties: String = jsonObj.getString("event_properties")
            val uuid: String = jsonObj.getString("uuid")
            val device_manufacturer: String = jsonObj.getString("device_manufacturer")
            val version_name: String = jsonObj.getString("version_name")
            val library: String = jsonObj.getString("library")
            val os_name: String = jsonObj.getString("os_name")
            val platform: String = jsonObj.getString("platform")
            val event_id: String = jsonObj.getString("event_id")
            val carrier: String = jsonObj.getString("carrier")
            val timestamp: String = jsonObj.getString("timestamp")
            val groups: String = jsonObj.getString("groups")
            val os_version: String = jsonObj.getString("os_version")
            val device_model: String = jsonObj.getString("device_model")
            // 根据ip解析城市
            val res: String = IPUtil.getCity(ip)
            val province: String = JSON.parseObject(res).getString("province")
            val city: String = JSON.parseObject(res).getString("city")
            // 分区字段
            val dt: String = DateUtil.getCurrentDate.replace("-", "")
            // 封装成Row对象
            Row(client, v, upload_time, checksum, method, ip, session_id, user_properties, language, event_type,
              sequence_number, user_id, country, api_properties, device_id, event_properties, uuid, device_manufacturer,
              version_name, library, os_name, platform, event_id, carrier, timestamp, groups, os_version, device_model,
              province, city, server_time, dt)
          })
        })
        // 写入hive表
        val columns: String = common + "," + body + "," + e
        HiveUtil.write(rowRDD, spark, table, columns)
        // 处理完本批次数据后要更新redis中的偏移量(先消费后提交at least once)
        OffsetManageUtil.updateOffset(topics, groupId, offsetRanges)
      }
    })

    // 6.启动程序
    ssc.start()
    ssc.awaitTermination()
  }
}
