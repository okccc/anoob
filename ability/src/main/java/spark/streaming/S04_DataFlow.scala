package spark.streaming

import org.apache.spark.SparkConf
import org.apache.spark.sql.streaming.StreamingQuery
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}

object S04_DataFlow {
  def main(args: Array[String]): Unit = {
    /*
     *
     *
     */

    // 创建spark程序之前设置hadoop用户名,不然访问hdfs没有权限
    System.setProperty("HADOOP_USER_NAME", "root")

    // 创建Spark配置信息
    val conf: SparkConf = new SparkConf().setMaster("local[2]").setAppName("Structured Streaming")
    // 创建SparkSession,查看源码发现SparkSession类的构造器都是private,只能通过其伴生对象创建
    val spark: SparkSession = SparkSession.builder().config(conf).enableHiveSupport().getOrCreate()
    // 导入隐式转换
    import spark.implicits._

    // 读取数据流并加载为DataFrame
    // 1).socket套接字
    val socketDF: DataFrame = spark.readStream
      .format("socket")
      .option("host", "cdh1")
      .option("port", 9999)
      .load()
    println(socketDF.isStreaming)
    socketDF.printSchema()
    val lines: Dataset[String] = socketDF.as[String]
    val words: Dataset[String] = lines.flatMap((s: String) => s.split(" "))
    val wc: DataFrame = words.groupBy("value").count()
    // 输出结果,输出模式包括complete/append/update
    val query: StreamingQuery = wc.writeStream
      .outputMode("complete")
      .format("console")
      .start()
    // 等待程序终止
    query.awaitTermination()

    // 2).从kafka采集数据
    //    val kafkaDF: DataFrame = spark.readStream
    //      .format("kafka")
    //      .option("kafka.bootstrap.servers", "cdh1:9092")
    //      .option("subscribe", "topic01")
    //      .load()
    //    val lines: Dataset[String] = kafkaDF.selectExpr( "CAST(value AS STRING)").as[String]
    //    val words: Dataset[String] = lines.flatMap((s: String) => s.split(" "))
    //    val wc: DataFrame = words.groupBy("value").count()
    //    // 输出结果
    //    val query: StreamingQuery = wc.writeStream
    //      .outputMode("complete")
    //      .format("console")
    //      .start()
    //    query.awaitTermination()

  }

}
