package com.okccc.spark

import java.sql.{Connection, DriverManager, PreparedStatement}

import org.apache.hadoop.io.{IntWritable, Text}
import org.apache.hadoop.mapred.TextOutputFormat
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.streaming.dstream.{DStream, ReceiverInputDStream}
import org.apache.spark.streaming.{Seconds, StreamingContext}

object S03_DStream {
  def main(args: Array[String]): Unit = {
    /*
     * Spark -> RDD | SparkSql -> DataFrame/DataSet | SparkStreaming -> DStream
     * 实时处理是来一条处理一条,SparkStreaming是将实时流数据按照batchDuration切分成微批准实时处理,类似map/mapPartitions算子
     *
     * DStream(离散化流)：a continuous series of RDDs 连续的RDD序列,一个时间批次的数据对应一个RDD
     * batchDuration(批处理时间间隔)：the time interval at which streaming data will be divided into batches
     * windowDuration(窗口大小)：一个窗口覆盖的批数据个数
     * slideDuration(滑动间隔)：前后两个窗口的间隔
     * 每隔滑动间隔去计算最近窗口长度内的数据
     *
     * DStreams are executed lazily by the output operations, just like RDDs are lazily executed by RDD actions.
     * Specifically, RDD actions inside the DStream output operations force the processing of the received data.
     * Hence, if your application does not have any output operation, or has output operations like foreachRDD without
     * any RDD action inside them, then nothing will get executed. The system will simply receive the data and discard it.
     * DStream由output操作延迟执行,就像RDD由action操作延迟执行一样,具体来说,DStream的output操作内部的RDD的action操作会强制处理
     * 接收到的数据,因此,如果没有任何output操作或者foreachRDD内部不包含RDD的action操作,那么什么都不会执行,系统只是接收数据并丢弃它
     *
     * SparkStreaming性能优化
     * 1.减小窗口长度 -> 防止每个DStream耗时太长导致DStream任务堆积,无法充分利用集群资源
     * 2.增大滑动间隔 -> 保证数据处理能跟上数据摄取的步伐
     * 3.设置checkpoint保存上一个窗口统计结果状态,上一个窗口统计结果 + 头部新的滑动间隔值 - 尾部旧的滑动间隔值 = 当前窗口统计结果,减少重复计算
     *
     * SparkStreaming为什么要通过Kafka连接Flume
     * 缓冲：flume采集数据太快的话spark可能处理不完,需要消息队列缓冲一下,kafka吞吐量大并且可以将数据落盘,向spark提供稳定的数据流
     * 解耦：flume只管采集数据,spark只管处理数据,两者互不影响
     *
     * SparkStreaming整合Kafka
     * spark1.6 + kafka0.8  -> 包含receiver模式和direct模式
     * spark2.3 + kafka0.11 -> 只有direct模式
     *
     * kafka高低阶消费者
     * 基于receiver方式,使用kafka高阶api在zk中保存消费过的offset,配合wa机制可以保证数据零丢失,但是可能重复消费,因为spark和zk并不同步
     * 基于direct方式,使用kafka低阶api由SparkStreaming自己追踪消费的offset并保存在checkpoint,保证数据只消费一次(常用)
     *
     * spark消费kafka数据存到哪里
     * mysql：不适合存大量数据速度也一般
     * hbase：能存储大量数据但是过于笨重
     * es：能存大量数据并且使用灵活,如果spark只是将kafka数据清洗过滤,数据量级并没有变,可以将明细数据存到es/clickhouse再做聚合
     * redis：只能存少量数据但速度极快,如果spark已经将数据聚合成少量结果集,可以存到redis,但是内存计算压力会较大,还有就是缓存偏移量这种中间结果
     *
     * SparkStreaming小文件问题
     * 微批处理模式(batchDuration)和DStream/RDD分布式(partition)特性导致生成大量小文件
     * 比如batchDuration=10s,每个输出的DStream有32个分区,那么一个小时产生的文件数=(3600/10)*32=11520
     * 内部处理: 1.调大batchDuration减少batch数量,但会降低实时性 2.借助repartition算子减少partition数量,但并行度降低会导致批处理变慢
     * 外部处理: 将已经落到hdfs的小文件按照dt/hour粒度手动合并
     * hive.merge.mapredfiles = true;
     * hive.merge.size.per.task = 256000000;
     * hive.merge.smallfiles.avgsize = 128000000;
     * hive.exec.compress.output = true;
     * mapred.output.compress = true;
     * mapred.output.compression.codec = org.apache.hadoop.io.compress.GzipCodec;
     * a.每天合并一次(等所有文件落地就可以覆盖原表)
     * insert overwrite table ods.access_log partition(dt=${dt})
     * select * from ods.access_log where dt=${dt};
     * b.每小时合并一次(写到小时表)
     * insert overwrite table ods.access_log_h partition(dt=${dt},hr=${hr})
     * select * from ods.access_log where from_unixtime(cast(server_time/1000 as int),'yyyyMMddHH') ='${hour}'
     *
     */

    //    if (args.length < 2) {
    //      System.err.println("Usage: ClassName <hostname> <port>"c)
    //      System.exit(1)
    //    }
    //    官方案例：nc -lk 9999 & run-example org.apache.spark.examples.streaming.NetworkWordCount localhost 9999

    //    // scala中的滑动窗口函数
    //    val ints: List[Int] = List(1,2,3,4,5,6)
    //    val ints1: Iterator[List[Int]] = ints.sliding(size = 3)
    //    val ints2: Iterator[List[Int]] = ints.sliding(size = 3, step = 3)
    //    println(ints1.mkString(","))  // List(1, 2, 3),List(2, 3, 4),List(3, 4, 5),List(4, 5, 6)
    //    println(ints2.mkString(","))  // List(1, 2, 3),List(4, 5, 6)

    // 创建spark配置信息
    // 配置参数优先级：SparkConf(代码写死) > spark-submit --conf(动态指定) > spark-defaults.conf(集群配置)
    // receiver模式的DStream(socket/flume/kafka高阶)接收器要单独占一个线程,"local[n>1]",direct模式没有接收器"local"即可
    val conf: SparkConf = new SparkConf().setMaster("local[2]").setAppName("Spark Streaming")
    // 创建StreamingContext对象,指定批处理时间间隔(采集周期)
    val ssc: StreamingContext = new StreamingContext(conf, batchDuration=Seconds(2))
    // 设置日志级别
    ssc.sparkContext.setLogLevel("warn")

    // DStream的有状态更新操作需要设置checkpoint将数据落盘保存内存中的状态,频率是 max(batchDuration, 10s)
    // checkpoint可以保存：1.conf配置信息 2.DStream处理逻辑 3.batch处理的位置信息,比如kafka的offset
    // 但是checkpoint存在小文件等问题,生产环境中通常使用redis去重和存储偏移量
    ssc.checkpoint("cp")

    // 创建DStream两种方式：接收输入数据流,从其他DStream转换
    // 1).socket套接字, yum -y install nc 在终端开启`nc -lk 9999`写入数据
    val socketDStream: ReceiverInputDStream[String] = ssc.socketTextStream("localhost", 9999)
    // 2).监控文件或目录,没有采用Receiver接收器模式,所以local[n]不需要设置n > 1
//    val hdfsDStream: DStream[String] = ssc.textFileStream("hdfs://cdh1:9000/test")
    // 3).从kafka采集数据
//    KafkaUtils.createDirectStream()

    // DStream的transform操作
    // flatMap算子：扁平化处理
    // map算子：结构转换
    // filter算子：过滤
    // repartition算子：通过增加或减少分区改变DStream的并行度
    // union算子：取source DStream和other DStream的并集
    // count算子：统计DStream中每个RDD的元素个数
    // reduce算子：聚合DStream中每个RDD的元素
    // countByValue算子：统计DStream中每个RDD的每个key出现的频次
    // reduceByKey算子：对(K,V)类型的DStream做聚合操作
    // join算子：(K,V) + (K,W) = (K,(V,W))
    // cogroup算子：(K,V) +(K,W) = (K,Seq[V],Seq[W])
    val wordsDStream: DStream[String] = socketDStream.flatMap((line: String) => line.split(" "))
    val pairsDStream: DStream[(String, Int)] = wordsDStream.map((word: String) => (word, 1))
    // 1).DStream的无状态转化操作(点)：只计算当前时间批次内的数据,数据曲线平稳
    val wcDStream: DStream[(String, Int)] = pairsDStream.reduceByKey((x: Int, y: Int) => x + y)
    // print算子：在driver节点打印DStream中生成的每个RDD的前10个元素(调试)
    wcDStream.print()
    // 2).DStream的有状态转化操作(射线)：计算自数据采集以来的所有数据,是一个累加的过程,数据呈线性增长
    val stateDStream: DStream[(String, Int)] = pairsDStream.updateStateByKey((seq: Seq[Int], opt: Option[Int]) => {
      // seq是当前时间批次单词频度,opt是前面时间批次单词频度
      val cnt: Int = seq.sum + opt.getOrElse(0)
      Option(cnt)
    })
    // 3).DStream的窗口操作(线段)：随着滑动间隔(m个时间批次)计算当前窗口(n个时间批次)的数据,数据曲线先升后降(批数据2 3 1 -> 输出2 5 6 4 1)
    // window算子：设置窗口大小和滑动间隔
    // countByWindow算子：统计滑动窗口内的元素个数
    // reduceByWindow算子：对滑动窗口内的元素做reduce操作
    // reduceByKeyAndWindow算子：针对(K,V)类型DStream,对滑动窗口内的批数据的key的value值做reduce操作,local模式默认并行度2,cluster模式可设置spark.default.parallelism
    // countByValueAndWindow算子：针对(K,V)类型DStream,统计滑动窗口内的批数据的key的出现频次
    // 普通方案：每次都单独计算当前窗口内的数据
    val windowDStream: DStream[(String, Int)] = pairsDStream.reduceByKeyAndWindow((x: Int, y: Int) => x + y, windowDuration = Seconds(4), slideDuration = Seconds(2))
    // 优化方案：1.减小窗口长度 2.增大滑动间隔 3.用上一个window统计结果加上新的slide减去旧的slide,该方法要设置checkpoint保存之前的状态
    val window2SDtream: DStream[(String, Int)] = pairsDStream.reduceByKeyAndWindow((x: Int, y: Int) => x + y, (x: Int, y: Int) => x - y, Seconds(4), Seconds(2))

    // transform算子：对DStream中的RDD做一些transform操作,且不用执行RDD的action操作,而是通过DStream的output操作触发计算
    val wordDStream: DStream[(String, Int)] = socketDStream.transform((rdd: RDD[String]) => {
      // transform/foreachRDD算子内除了获取的RDD的算子以外的代码都是在driver端执行,利用这个特点可以动态改变广播变量
      val filterRDD: RDD[String] = rdd.filter((s: String) => !s.contains("hive"))
      val wordsRDD: RDD[(String, Int)] = filterRDD.map((s: String) => (s, 1))
      wordsRDD
    })
    wordDStream.print()

    // DStream的output操作
    // foreachRDD算子：将流中生成的每个RDD的数据推送到hdfs/数据库等外部系统(常用)
    // 1).写入hdfs文件
//    wcDStream.foreachRDD((rdd: RDD[(String, Int)]) => {
//      // 分区数过多会产生大量小文件,写入hdfs之前先合并分区只输出到一个文件,但是会增加批处理时长,视实际情况而定
//      rdd.repartition(1).saveAsTextFile("ability/output/socket")
//      rdd.saveAsHadoopFile(
//        "hdfs://cdh1:9000/user/spark/streaming/socket",
//        classOf[Text],
//        classOf[IntWritable],
//        classOf[TextOutputFormat[Text, IntWritable]],
//        classOf[org.apache.hadoop.io.compress.GzipCodec]
//      )
//    })
//    // 2).写入mysql数据库
//    wcDStream.foreachRDD((rdd: RDD[(String, Int)]) => {
//      // 遍历所有分区,foreachPartition算子是action操作,以下代码是在executor端执行
//      rdd.foreachPartition((ite: Iterator[(String, Int)]) => {
//        // 创建数据库连接对象,有几个分区就创建几次连接,在executor端初始化连接对象避免网络传输导致的序列化问题
//        val conn: Connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test", "root", "root")
//        // 遍历每个分区中的所有元素
//        try {
//          ite.foreach((t: (String, Int)) => {
//            // 关闭自动提交
//            conn.setAutoCommit(false)
//            // sql语句
////            val sql1: String = "insert into wv values(?,?)"
////            val sql2: String = "insert into wv values('"+t._1+"','"+t._2+"')"
//            val sql: String = "insert into wc values(" + t._1 + ", " + t._2 + ")"
//            // 创建PreparedStatement对象
//            val ps: PreparedStatement = conn.prepareStatement(sql)
//            // 执行更新操作
//            ps.executeUpdate()
//            ps.close()
//            // 手动提交
//            conn.commit()
//          })
//        } catch {
//          case e: Exception => println(e)
//            if (conn != null) {
//              // 如果异常就回滚
//              conn.rollback()
//            }
//        } finally {
//          // 关闭连接
//          conn.close()
//        }
//      })
//    })
//    // DStream中的RDD还可以转换成DataFrame通过SparkSql计算WordCount
//    wordsDStream.foreachRDD((rdd: RDD[String]) => {
//      // 创建SparkSession
//      val spark: SparkSession = SparkSession.builder().config(conf).enableHiveSupport().getOrCreate()
//      // 导入隐式转换
//      import spark.implicits._
//      // 将RDD转换成DataFrame
//      val df: DataFrame = rdd.toDF("word")
//      // a.通过DataFrame的api查询
//      //      df.groupBy("word").count().show()
//      // b.将DataFrame注册成视图通过sql查询
//      df.createOrReplaceTempView("words")
//      spark.sql("select word,count(*) from words group by word").show(false)
//    })

    // 启动程序
    ssc.start()
    ssc.awaitTermination()
  }
}
