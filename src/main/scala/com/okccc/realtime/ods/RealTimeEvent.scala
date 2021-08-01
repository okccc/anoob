package com.okccc.realtime.ods

import com.alibaba.fastjson.JSONObject
import com.okccc.realtime.common.Configs
import com.okccc.util.{HiveUtil, KafkaConsUtil, LogParseUtil, OffsetManageUtil}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SparkSession}
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}

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
     * 5.Unable to instantiate SparkSession with Hive support because Hive classes are not found.
     * pom.xml添加spark-hive_2.12依赖
     *
     * 6.spark-submit报错 java.lang.NoSuchMethodError: scala.Predef$.ArrowAssoc(Ljava/lang/Object;)Ljava/lang/Object
     * 这种错一般有两个原因,一个是真的缺jar包,二个是jar包冲突了(常见)
     * idea的scala插件,pom.xml的scala及spark/flink依赖引用的scala,服务器最终执行jar包的spark/flink命令引用的scala,要保持版本一致
     *
     * 7.numRecords must not be negative
     * 如果topic删除重建要先清空redis中的offset,不然读不到之前偏移量
     */

    // 参数校验
    if (args.length != 1) {
      println("Usage: Please input batchDuration(s)")
      System.exit(1)
    }

    // 创建SparkSession对象
    val spark: SparkSession = SparkSession
      .builder()
      .appName("nginx2hive")
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
    val groupId: String = Configs.get(Configs.NGINX_GROUP_ID)
    // hive表和列
    val hiveTable: String = Configs.get(Configs.NGINX_HIVE_TABLE)
    val hiveColumns: String = Configs.get(Configs.NGINX_HIVE_COLUMNS)

    // ==================== 功能1.SparkStreaming读取kafka数据 ====================
    // 1.每次启动时从redis读取偏移量
    val offsetMap: Map[TopicPartition, Long] = OffsetManageUtil.getOffset(topics, groupId)
    println(offsetMap)  // 第一次读取是Map(),此时redis还没有g01:canal这个key

    // 2.加载偏移量起始点处的kafka数据
    var inputDStream: InputDStream[ConsumerRecord[String, String]] = null
    if (offsetMap != null && offsetMap.nonEmpty) {
      // 如果redis已经有偏移量,就从偏移量处读取
      inputDStream = KafkaConsUtil.getKafkaDStream(ssc, topics, groupId, offsetMap)
    } else {
      // 如果redis还没有偏移量,auto.offset.reset参数会生效,从__consumer_offsets的latest处读取,消费到数据后会更新redis中的偏移量
      inputDStream = KafkaConsUtil.getKafkaDStream(ssc, topics, groupId)
    }

    // 3.获取本批次数据所在分区对应偏移量的起始点和结束点
    var offsetRanges: Array[OffsetRange] = null
    val offsetDStream: DStream[ConsumerRecord[String, String]] = inputDStream.transform((rdd: RDD[ConsumerRecord[String, String]]) => {
      // RDD是顶层父类,将其强制类型转换为实现了HasOffsetRange特质的子类,查看源码发现RDD的所有子类中只有KafkaRDD实现了该特质
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    })

    // ============================== 功能2.将topic数据写入ods层 ==============================
    // 4.Transform操作
    val jsonDStream: DStream[ArrayBuffer[JSONObject]] = offsetDStream.map((record: ConsumerRecord[String, String]) => {
      // 获取ConsumerRecord的value部分
      val value: String = record.value()
      // json字符串 -> ArrayBuffer[JSONObject] 由于request_body的e字段是数组,要先拆开和公共字段拼接成JSONObject,不然后面不好解析
      LogParseUtil.parseStrToArrayBuffer(value)
    })

    // 5.Output操作
    // DStream的输出操作通常由foreachRDD完成,RDD本身是一个集合,只不过存储的是逻辑抽象而不是具体数据
    jsonDStream.foreachRDD((rdd: RDD[ArrayBuffer[JSONObject]]) => {
      if (!rdd.isEmpty()) {
        // 扁平化操作
        val jsonRDD: RDD[JSONObject] = rdd.flatMap((ab: ArrayBuffer[JSONObject]) => ab)
        // 以分区为单位处理RDD
        val rowRDD: RDD[Row] = jsonRDD.mapPartitions((partition: Iterator[JSONObject]) => {
          // JSONObject -> Row
          partition.map((jsonObj: JSONObject) => {
            Row.fromSeq(LogParseUtil.parseJSONObjectToArrayBuffer(jsonObj, hiveColumns))
          })
        })
        // 写入hive表
        HiveUtil.write(rowRDD, spark, hiveTable, hiveColumns)
        // 处理完本批次数据后要更新redis中的偏移量(先消费后提交at least once)
        OffsetManageUtil.updateOffset(topics, groupId, offsetRanges)
      }
    })

    // 6.启动程序
    ssc.start()
    ssc.awaitTermination()
  }
}
