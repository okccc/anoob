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

    // idea本地访问远程hive要设置hadoop用户,本地用户没有权限
//    System.setProperty("HADOOP_USER_NAME", "root")
    // 本地调试代码时参数不要写死,本地代码和提交到服务器的代码应该保持一致,建议在VM options添加配置
    // main方法参数配置：Edit Configurations - Main class(主类) - Program arguments(传参) - VM options(jvm配置)
    // idea本地调试spark代码
    // -Dspark.master=local[*]  A master URL must be set in your configuration
    // -DHADOOP_USER_NAME=hdfs  User okc does not have privileges for ALTERTABLE_ADDPARTS
    // -Dspark.sql.shuffle.partitions=1

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

    // topic和groupId
    val topics: String = Configs.get(Configs.NGINX_TOPICS)
    val groupId: String = Configs.get(Configs.GROUP_ID)
    // hive表
    val table: String = Configs.get(Configs.HIVE_TABLE)
    val columns: String = Configs.get(Configs.HIVE_COLUMNS)

    // ==================== 功能1.SparkStreaming读取kafka数据 ====================
    // 1.从redis读取偏移量
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

    // 4.转换DStream数据结构
    val RowDStream: DStream[Row] = offsetDStream.map((record: ConsumerRecord[String, String]) => {
      // 获取ConsumerRecord的value部分
      val jsonStr: String = record.value()
      // 将json字符串转换成json对象
      val jsonObj: JSONObject = JSON.parseObject(jsonStr)
      // 先解析公共字段
      val ts: String = jsonObj.getString("@timestamp")
      val hostname: String = jsonObj.getString("hostname")
      val remote_addr: String = jsonObj.getString("remote_addr")
      val ip: String = jsonObj.getString("ip")
      val method: String = jsonObj.getString("Method")
      val referer: String = jsonObj.getString("referer")
      val request: String = jsonObj.getString("request")
      val status: String = jsonObj.getString("status")
      val bytes: String = jsonObj.getString("bytes")
      val agent: String = jsonObj.getString("agent")
      val x_forwarded: String = jsonObj.getString("x_forwarded")
      // 再解析body字段
      val body: String = jsonObj.getString("request_body")
      val p_date: String = "20210623"
      // 封装成Row对象
      Row(ts, hostname, remote_addr, ip, method, referer, request, body, status, bytes, agent, x_forwarded, p_date)
    })

    RowDStream.foreachRDD((rdd: RDD[Row]) => {
      // 根据列创建hive表结构
      val schema: StructType = HiveUtil.createSchema(columns)
      // 将RDD转换为DataFrame
      val df: DataFrame = spark.createDataFrame(rdd, schema)
      // 生成临时表
      df.repartition(30).createOrReplaceTempView("event")
      // 将临时表数据插入正式表
      spark.sql("insert into table " + table + " partition(p_date) select * from event")
      // 处理完当前批次数据要更新偏移量,由于消费速度低于生产速度导致消息积压,虽然每隔5秒获取一批数据但是更新offset时可能已经过去了好几个5秒
      // 所以输出的更新分区偏移量不是连续的,更新分区偏移量: 0 923~971,更新分区偏移量: 0 1554~1602,更新分区偏移量: 0 2184~2232
      OffsetManageUtil.updateOffset(topics, groupId, offsetRanges)
    })


    // 4.输出操作
    //    offsetDStream.foreachRDD((rdd: RDD[ConsumerRecord[String, String]]) => {
    //      // 以分区为单位处理数据
    //      rdd.mapPartitions((iterator: Iterator[ConsumerRecord[String, String]]) => {
    //        // 结构转换
    //        iterator.map((record: ConsumerRecord[String, String]) => {
    //          // 获取ConsumerRecord的value部分
    //          val jsonStr: String = record.value()
    //          // 将json字符串封装成json对象
    //          val jsonObj: JSONObject = JSON.parseObject(jsonStr)
    //          // 解析json对象
    //          val body: String = jsonObj.getString("request_body")
    //          // url解码
    //          body
    //        })
    //      })
    //    })

    // 启动程序
    ssc.start()
    ssc.awaitTermination()
  }

}
