package app

import java.lang

import com.alibaba.fastjson.{JSON, JSONObject}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.spark.SparkConf
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import redis.clients.jedis.Jedis
import utils.{MyDateUtil, MyKafkaUtil, MyRedisUtil}

import scala.collection.mutable.ListBuffer

/**
 * @author okccc
 * @date 2020/12/13 11:24
 * @desc 日活统计
 */
object DauAPP {
  def main(args: Array[String]): Unit = {
    // 创建spark配置信息
    val conf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("DauApp")
//      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    // 创建StreamingContext对象
    val ssc: StreamingContext = new StreamingContext(conf, Seconds(3))
    // 设置日志级别
    ssc.sparkContext.setLogLevel("warn")

    // 指定kafka的topic和groupId
    val topic: String = "t_start"
    val groupId: String = "aaa"

    // ============================== 功能1.从kafka中读取数据 ==============================
    val kafkaDStream: InputDStream[ConsumerRecord[String, String]] = MyKafkaUtil.getKafkaDStream(ssc, topic, groupId)
    // 测试输出1
    kafkaDStream.map((record: ConsumerRecord[String, String]) => record.value()).print(100)

    // 转换DStream中的数据结构
    val jsonDStream: DStream[JSONObject] = kafkaDStream.map((record: ConsumerRecord[String, String]) => {
      // 获取ConsumerRecord的value部分 {"action":"1","ba":"Huawei","detail":"433","en":"start","t":"1607982026247"...}
      val jsonStr: String = record.value()
      // 将字符串转换成json对象
      val jsonObj: JSONObject = JSON.parseObject(jsonStr)
      // 获取时间戳
      val ts: lang.Long = jsonObj.getLong("t")
      // 转换成日期和小时
      val str: String = MyDateUtil.parseUnixToDateTime(ts)
      // 添加到json对象
      jsonObj.put("dt", str.substring(0, 10))
      jsonObj.put("hr", str.substring(11, 13))
      jsonObj
    })
    // 测试输出2
    jsonDStream.print()

    // ============================== 功能2.通过redis对数据去重 ==============================
    // 1).遍历元素
//    val filterDStream: DStream[JSONObject] = jsonDStream.filter((jsonObj: JSONObject) => {
//      // 获取日期和设备号,并将其拼接成保存到redis的key
//      val dt: String = jsonObj.getString("dt")
//      val mid: String = jsonObj.getString("mid")
//      val dauKey: String = "dau:" + dt
//      // 获取redis连接对象,每个元素都会建立一次连接,性能较差
//      val jedis: Jedis = MyRedisUtil.getJedis
//      // 往set集合添加键值对,1表示添加成功,0表示已存在
//      val long: lang.Long = jedis.sadd(dauKey, mid)
//      // 设置key的过期时间
//      jedis.expire(dauKey, 3600 * 24)
//      // 关闭连接
//      jedis.close()
//      if (long == 1) true else false
//    })
//    filterDStream.count().print()

    // 2).遍历分区
    val filterDStream: DStream[JSONObject] = jsonDStream.mapPartitions(mapPartFunc = (jsonObjIterator: Iterator[JSONObject]) => {
      // 获取redis连接对象,有几个分区就建立几次连接,提高性能
      val jedis: Jedis = MyRedisUtil.getJedis
      // 创建存放首次登陆对象的列表缓冲区
      val listBuffer: ListBuffer[JSONObject] = ListBuffer[JSONObject]()
      // 遍历分区中的元素
      for (jsonObj <- jsonObjIterator) {
        // 获取日期和设备号,并将其拼接成保存到redis的key
        val dt: String = jsonObj.getString("dt")
        val mid: String = jsonObj.getString("mid")
        val dauKey: String = "dau:" + dt
        // 往set集合添加键值对,1表示添加成功,0表示已存在
        val long: lang.Long = jedis.sadd(dauKey, mid)
        // 设置key的过期时间
        jedis.expire(dauKey, 3600 * 24)
        // 将首次登陆的对象添加到ListBuffer
        if (long == 1) {
          listBuffer.append(jsonObj)
        }
      }
      // 及时释放资源,不然连接池不够用 redis.clients.jedis.exceptions.JedisException: Could not get a resource from the pool
      jedis.close()
      listBuffer.toIterator
    })
    // 测试输出3
    filterDStream.count().print()

    // ============================== 功能3.将日活数据保存到es ==============================


    // 启动采集
    ssc.start()
    ssc.awaitTermination()
  }
}
