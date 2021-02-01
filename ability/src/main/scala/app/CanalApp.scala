package app

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import utils.{KafkaConsUtil, OffsetManageUtil}

/**
 * Author: okccc
 * Date: 2020/12/20 12:24 下午
 * Desc: 基于canal从kafka读取业务数据并分流写到ods层
 */
object CanalApp {
  def main(args: Array[String]): Unit = {
    // yarn cluster和yarn client区别？
    if (args.length != 1) {
      println("Usage: Please input batchDuration(s)")
      System.exit(1)
    }
    // 创建SparkSession对象
    val spark: SparkSession = SparkSession
      .builder()
      .appName("canal-kafka-spark-ods")
      // pom.xml添加spark-hive_2.11依赖,不然报错Unable to instantiate SparkSession with Hive support because Hive classes are not found.
      .enableHiveSupport()
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
//      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()
    // 创建StreamingContext对象
    val ssc: StreamingContext = new StreamingContext(spark.sparkContext, Seconds(args(0).toLong))
    // 设置日志级别
    spark.sparkContext.setLogLevel("info")

    // 指定topic和groupId
    val topicName: String = "canal"
    val groupId: String = "g"

    // ============================== 功能1.SparkStreaming读取kafka数据=============================
    // 1.从redis读取偏移量起始点
    val offsetMap: Map[TopicPartition, Long] = OffsetManageUtil.getOffset(topicName, groupId)
    println(offsetMap)  // 第一次读取时应该是Map(),此时redis还没有g:canal这个key,下面的saveOffset方法会创建该key并第一次写入offset
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
    jsonDStream.print()
    // {"data":[{"sku_id":"11","user_id":"77","sku_name":"iphone12"...}],"type":"INSERT","database":"canal","table":"cart_info"...}

    // ============================== 功能2.将topic按表进行分流写入ods层 ==============================
    // 1.DStream输出操作通常由foreachRDD完成,RDD本身是一个集合,只不过存储的是逻辑抽象而不是具体数据
    jsonDStream.foreachRDD((rdd: RDD[JSONObject]) => {
      rdd.foreach((jsonObj: JSONObject) => {
        val dataArray: JSONArray = jsonObj.getJSONArray("data")
        import scala.collection.JavaConverters._
        for (data <- dataArray.asScala) {

        }
        val table: String = jsonObj.getString("table")
      })
    })

    // 启动程序5
    ssc.start()
    ssc.awaitTermination()

  }
}
