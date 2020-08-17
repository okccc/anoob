package spark

import java.sql.{Connection, DriverManager, PreparedStatement}

import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.streaming.dstream.{DStream, ReceiverInputDStream}
//import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}

object S03_DStream {
  def main(args: Array[String]): Unit = {
    /**
     * Spark -> RDD | SparkSql -> DataFrame/DataSet | SparkStreaming -> DStream(discretized stream)
     * 实时处理是来一条处理一条,SparkStreaming是将live streaming data切分成batches处理(准实时),类似map/mapPartitions算子
     *
     * DStream(离散化流)：a continuous series of RDDs 连续的RDD序列,一个时间批次的数据对应一个RDD,是SparkStreaming的基本抽象
     * batchDuration(批处理时间间隔)：the time interval at which streaming data will be divided into batches
     * windowDuration(窗口大小)：一个窗口覆盖的批数据个数
     * slideDuration(滑动间隔)：前后两个窗口的间隔
     *
     * DStreams are executed lazily by the output operations, just like RDDs are lazily executed by RDD actions.
     * Specifically, RDD actions inside the DStream output operations force the processing of the received data.
     * Hence, if your application does not have any output operation, or has output operations like foreachRDD without
     * any RDD action inside them, then nothing will get executed. The system will simply receive the data and discard it.
     * DStream由output操作延迟执行,就像RDD由action操作延迟执行一样,具体来说,DStream的output操作内部的RDD的action操作会强制处理
     * 接收到的数据,因此,如果没有任何output操作或者foreachRDD内部不包含RDD的action操作,那么什么都不会执行,系统只是接收数据并丢弃它
     *
     * SparkStreaming性能优化
     * 1.减少批数据的处理时间 -> 有效利用集群资源
     * 2.设置合适的批数据间隔 -> 保证数据处理能跟上数据摄取的步伐
     */

    // scala中的滑动窗口函数
//    val ints: List[Int] = List(1,2,3,4,5,6)
//    val ints1: Iterator[List[Int]] = ints.sliding(size = 3)
//    val ints2: Iterator[List[Int]] = ints.sliding(size = 3, step = 3)
//    println(ints1.mkString(","))  // List(1, 2, 3),List(2, 3, 4),List(3, 4, 5),List(4, 5, 6)
//    println(ints2.mkString(","))  // List(1, 2, 3),List(4, 5, 6)

//    if (args.length < 2) {
//      System.err.println("Usage: ClassName <> <>")
//      System.exit(1)
//    }
//    官方案例：nc -lk 9999 & run-example org.apache.spark.examples.streaming.NetworkWordCount localhost 9999

    // 创建spark配置信息,local[n]模式n要大于1,因为基于receiver的DStream(socket/flume/kafka)运行Receiver对象要单独占一个线程,
    // 处理接收的数据在另一个线程,cluster模式分配给application的cores也要大于receivers
    val conf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("SparkStreaming")
    // 创建StreamingContext对象,指定批处理时间间隔(采集周期)
    val ssc: StreamingContext = new StreamingContext(conf, batchDuration=Seconds(1))
    // 有状态的转化操作需要设置检查点目录
    ssc.checkpoint("scala/cp")

    // 创建DStream两种方式：接收输入数据流,从其他DStream转换
    // 1).socket套接字, yum -y install nc 开启nc -lk 9999写入数据
    val socketDStream: ReceiverInputDStream[String] = ssc.socketTextStream("cdh1", 9999)
    // 2).hdfs文件/目录
    val hdfsDStream: DStream[String] = ssc.textFileStream("hdfs://cdh1:9000/test")
    // 3).从kafka采集数据
//    val kafkaDStream: ReceiverInputDStream[(String, String)] = KafkaUtils.createStream(ssc, "cdh1:2181", "spark", topics = Map("spark" -> 3))

    // DStream的transform操作
    // flatMap算子：扁平化处理
    // map算子：结构转换
    // filter算子：过滤
    // repartition算子：通过增加或减少分区改变DStream的并行度
    // union算子：取source DStream和other DStream的并集
    // count算子：统计DStream中每个RDD的元素个数
    // reduce算子：聚合DStream中每个RDD的元素
    // countByValue算子：统计DStream找那个每个RDD中每个key出现的频次
    // reduceByKey算子：对(K,V)类型的DStream做聚合操作
    // join算子：(K,V) + (K,W) = (K,(V,W))
    // cogroup算子：(K,V) +(K,W) = (K,Seq[V],Seq[W])
    // transform算子：可以对DStream中的RDD做随时间变化的操作
    val wordsDStream: DStream[String] = socketDStream.flatMap((line: String) => line.split(" "))
    val pairsDStream: DStream[(String, Int)] = wordsDStream.map((word: String) => (word, 1))
    // DStream的无状态转化操作(点)：只计算当前时间批次内的数据,数据曲线平稳
    val wcDStream: DStream[(String, Int)] = pairsDStream.reduceByKey((x: Int, y: Int) => x + y)
    // DStream的有状态转化操作(射线)：计算自数据采集以来的所有数据,是一个累加的过程,数据呈线性增长
    val stateDStream: DStream[(String, Int)] = pairsDStream.updateStateByKey((seq: Seq[Int], opt: Option[Int]) => {
      // seq是当前时间批次单词频度,opt是前面时间批次单词频度
      val cnt: Int = seq.sum + opt.getOrElse(0)
      Option(cnt)
    })
    // DStream的窗口操作(线段)：随着滑动间隔(m个时间批次)计算当前窗口(n个时间批次)的数据,数据曲线先升后降(批数据2 3 1 -> 输出2 5 6 4 1)
    // window算子：设置窗口大小和滑动间隔
    // countByWindow算子：统计滑动窗口内的元素个数
    // reduceByWindow算子：对滑动窗口内的元素做reduce操作
    // reduceByKeyAndWindow算子：针对(K,V)类型DStream,对滑动窗口内的批数据的key的value值做reduce操作,local模式默认并行度2,cluster模式可设置spark.default.parallelism
    // countByValueAndWindow算子：针对(K,V)类型DStream,统计滑动窗口内的批数据的key的出现频次
    val windowDStream: DStream[(String, Int)] = pairsDStream.reduceByKeyAndWindow((x: Int, y: Int) => x + y, windowDuration=Seconds(3), slideDuration=Seconds(2))

    // DStream的output操作
    // saveAsTextFiles：将DStream内容保存到文本文件
    // saveAsObjectFiles：将DStream内容保存到序列化的java对象SequenceFiles
    // saveAsHadoopFiles：将DStream内容保存到Hadoop文件
    // print算子：在driver节点打印DStream中生成的每个批数据(RDD)的前10个元素,用于调试代码
//    windowDStream.print()
    // foreachRDD(func)算子：最通用的输出操作,将流中生成的每个RDD的数据推送到外部系统,比如保存到文件或者通过网络写入数据库
    // 注意：func函数是运行在driver节点,通常伴随RDD的action操作从而强制计算DStream中的RDDs,创建连接对象的操作要放到executor端执行
//    wcDStream.foreachRDD((rdd: RDD[(String, Int)]) => {
//      // 遍历所有分区,foreachPartition算子是action操作,在executor端执行
//      rdd.foreachPartition((ite: Iterator[(String, Int)]) => {
//        // 创建数据库连接对象
//        val conn: Connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test", "root", "root")
//        // 遍历每个分区中的元素
//        ite.foreach((t: (String, Int)) => {
//          val sql = "insert into wc values(?,?)"
//          val statement: PreparedStatement = conn.prepareStatement(sql)
//          statement.setString(1, t._1)
//          statement.setInt(2, t._2)
//          statement.executeUpdate()
//          statement.close()
//        })
//        conn.close()
//      })
//    })

    // DStream中的RDD可以转换成DataFrame通过SparkSql计算WordCount
    wordsDStream.foreachRDD((rdd: RDD[String]) => {
      // 创建SparkSession
      val spark: SparkSession = SparkSession.builder().config(conf).enableHiveSupport().getOrCreate()
      // 导入隐式转换
      import spark.implicits._
      // 将RDD转换成DataFrame
      val df: DataFrame = rdd.toDF("word")
      // a.通过DataFrame的api查询
//      df.groupBy("word").count().show()
      // b.将DataFrame注册成视图通过sql查询
      df.createOrReplaceTempView("words")
      spark.sql("select word,count(*) from words group by word").show(false)
    })

    // 启动程序
    ssc.start()
    // 等待程序终止
    ssc.awaitTermination()
  }

}

// 模仿socketTextStream写一个自定义采集器(???)
//class MyReceiver(host: String, port: Int) extends Receiver[String](StorageLevel.MEMORY_ONLY) {
//  var socket: java.net.Socket = _
//  def receive(): Unit = {
//    socket = new java.net.Socket(host, port)
//    val reader: BufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream, "utf8"))
//    var line: String = null
//    while ((line = reader.readLine()) != null) {
//      // 将采集的数据放到采集器内部进行转换
//      if ("END".equals(line)) {
//        return
//      } else {
//        this.store(line)
//      }
//    }
//  }
//
//  override def onStart(): Unit = {
//    new Thread(new Runnable {
//      override def run(): Unit = {
//        receive()
//      }
//    }).start()
//  }
//
//  override def onStop(): Unit = {
//    if (socket != null) {
//      socket.close()
//      socket = null
//    }
//  }
//}