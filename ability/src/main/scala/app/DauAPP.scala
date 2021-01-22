package app

import bean.DauInfo
import com.alibaba.fastjson.{JSON, JSONObject}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import redis.clients.jedis.Jedis
import utils._

import java.lang
import scala.collection.mutable.ListBuffer

/**
 * Author: okccc
 * Date: 2020/12/13 11:24
 * Desc: DailyActiveUser日活统计
 */
object DauAPP {
  def main(args: Array[String]): Unit = {
    // 创建spark配置信息
    val conf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("nginx-kafka-spark-es/redis")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    // 创建StreamingContext对象
    val ssc: StreamingContext = new StreamingContext(conf, Seconds(3))
    // 设置日志级别
    ssc.sparkContext.setLogLevel("warn")

    // 指定topic和groupId
    val topicName: String = "start"
    val groupId: String = "g"

    // ============================== 功能1.SparkStreaming读取kafka数据=============================
    // 1.从redis读取偏移量起始点
    val offsetMap: Map[TopicPartition, Long] = OffsetManageUtil.getOffset(topicName, groupId)
    // 2.加载偏移量起始点处的kafka数据
    var recordDStream: InputDStream[ConsumerRecord[String, String]] = null
    if(offsetMap != null && offsetMap.nonEmpty) {
      // redis中已经有偏移量,就从偏移量处读取
      recordDStream  = KafkaConsUtil.getKafkaDStream(ssc, topicName, groupId, offsetMap)
    } else {
      // redis中还没有偏移量,默认从latest处读取
      recordDStream = KafkaConsUtil.getKafkaDStream(ssc, topicName, groupId)
    }
    // 测试输出
//    recordDStream.print()
    // ConsumerRecord(topic = start, partition = 0, leaderEpoch = 0, offset = 58001, CreateTime = 1610853239384, serialized key size = -1,
    // serialized value size = 330, headers = RecordHeaders(headers = [], isReadOnly = false), key = null, value = {"":""...})
    // 3.获取本批次数据所在分区对应偏移量的起始点和结束点
    var offsetRanges: Array[OffsetRange] = Array.empty[OffsetRange]
    val offsetDStream: DStream[ConsumerRecord[String, String]] = recordDStream.transform((rdd: RDD[ConsumerRecord[String, String]]) => {
      // RDD是顶层父类,将其强制类型转换为实现了HasOffsetRange特质的子类,RDD的所有子类中只有KafkaRDD实现了该特质
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    })

    // 4.转换DStream中的数据结构
    val jsonDStream: DStream[JSONObject] = offsetDStream.map((record: ConsumerRecord[String, String]) => {
      // 获取ConsumerRecord的value部分
      val jsonStr: String = record.value()
//      println("key = " + record.key())  // key = null
//      println("value = " + record.value())  // value = {"action":"2","ar":"MX","ba":"HTC","vn":"1.2.1"...}
      // 将json字符串封装成json对象
      val jsonObj: JSONObject = JSON.parseObject(jsonStr)
      // 获取时间戳
      val ts: lang.Long = jsonObj.getLong("t")
      // 转换成日期和小时
      val dtStr: String = MyDateUtil.parseUnixToDateTime(ts)
      // 添加到json对象
      jsonObj.put("dt", dtStr.substring(0, 10))
      jsonObj.put("hr", dtStr.substring(11, 13))
      jsonObj
    })
    // 测试输出
//    jsonDStream.print()  // {"mid":"11","hr":"18","dt":"2021-01-16","uid":"11","en":"start"...}

    // ============================== 功能2.利用redis过滤当日日活设备(去重) ==============================
    // 实时计算中去重是常见需求,redis/mysql(性能一般)/spark updateStateByKey(checkpoint小文件问题)
    // 1.遍历分区,涉及到数据库连接的操作,通常是以分区为单位处理数据,减少数据库连接次数
    val filteredDStream: DStream[JSONObject] = jsonDStream.mapPartitions((iterator: Iterator[JSONObject]) => {
      // 2.获取jedis客户端,有几个分区就创建几次连接,提高性能
      val jedis: Jedis = MyRedisUtil.getJedis()
      // 存放首次登录用户的列表
      val listBuffer: ListBuffer[JSONObject] = new ListBuffer[JSONObject]
      // 3.遍历分区中的元素
      iterator.foreach((jsonObj: JSONObject) => {
        // 获取日期和设备号
        val dt: String = jsonObj.getString("dt")
        val mid: String = jsonObj.getString("mid")
        // 将日期拼接成key
        val dauKey: String = "dau:" + dt
        // 往redis的set集合中添加数据,1表示添加成功,0表示已存在
        val long: lang.Long = jedis.sadd(dauKey, mid)
        // 设置key的过期时间
        jedis.expire(dauKey, 3600 * 24)
        // 将首次登录用户添加到列表
        if (long == 1L) {
          listBuffer.append(jsonObj)
        }
        // 释放资源,防止连接池不够用 redis.clients.jedis.exceptions.JedisException: Could not get a resource from the pool
        jedis.close()
      })
      listBuffer.toIterator
    })
    // 测试输出
    filteredDStream.count().print()

    // ============================== 功能3.将每批次新增的当日日活信息保存到es ==============================
    // 1.输出操作通常由foreachRDD完成,RDD本身是一个集合,只不过存储的是逻辑抽象而不是具体数据
    filteredDStream.foreachRDD((rdd: RDD[JSONObject]) => {
      // 2.遍历分区,涉及数据库连接的操作,通常是以分区为单位处理数据,减少数据库连接次数
      rdd.foreachPartition((iterator: Iterator[JSONObject]) => {
        var dt: String = null
        // 3.遍历每个分区中的元素
        val dauList: List[(String, DauInfo)] = iterator.map((jsonObj: JSONObject) => {
          // 4.封装DauInfo对象
          val uid: String = jsonObj.getString("uid")
          val mid: String = jsonObj.getString("mid")
          val ar: String = jsonObj.getString("ar")
          val ch: String = "0"
          val vc: String = jsonObj.getString("vc")
          dt = jsonObj.getString("dt")
          val hr: String = jsonObj.getString("hr")
          val mi: String = "0" // 日志中没有的字段可以给常量值
          val ts: lang.Long = jsonObj.getLong("t")
          val dauInfo: DauInfo = DauInfo(mid, uid, ar, ch, vc, dt, hr, mi, ts)
          // 返回结果
          (mid, dauInfo)
        }).toList
        // 5.往es批量插入数据
        MyESUtil.bulkIndex("dau_" + dt, dauList)
      })
      // 6.处理完本批次数据之后要更新redis中的偏移量
      OffsetManageUtil.saveOffset(topicName, groupId, offsetRanges)
    })

    // 启动采集
    ssc.start()
    ssc.awaitTermination()
  }
}
