package bigdata.spark

import org.apache.spark.rdd.RDD
import org.apache.spark.sql._
import org.apache.spark.sql.expressions.Aggregator
import org.apache.spark.sql.functions.{col, when}
import org.apache.spark.{SparkConf, SparkContext}

object S02_DataFrame {
  def main(args: Array[String]): Unit = {
    /*
     * 早期数据统计分析只有2种方式：MapReduce和Sql
     * MapReduce弊端：运行速度慢且编写复杂(要写Driver、Mapper、Reducer三个类)
     * Sql优点：性能好且编写简单,但是面向关系型数据库,所以hadoop领域的hive框架应运而生,类sql语法大大简化编码难度,但是底层计算框架还是mr
     * RDD：由于MapReduce速度实在太慢,所以基于内存的运算框架Spark领域的核心数据结构RDD出现了
     * SparkSql："集合"(RDD)偏底层过于抽象,分析师更习惯使用"表"(Sql),于是Spark借鉴pandas推出了DataFrame、Dataset和SparkSql
     *
     * SparkSql是Spark处理结构化数据的模块,提供了数据抽象DataFrame和Dataset
     * RDD/DataFrame/Dataset都是弹性分布式数据集,都有transform和action操作,都可以缓存和持久化
     * RDD：处理非结构化数据,RDD[Person]的每条记录都是Person对象,算子直接操作对象而不知道其内部是啥,适合对数据做一些偏底层的操作
     * DataFrame：给数据添加Schema结构信息比如name和age,并且将数据按列存储方便Catalyst更好的优化,算子可以单独操作指定列减少数据读取
     * Dataset：df没有类型而ds是强类型,运行时类型安全(runtime type-safety),df是ds的Untyped特例,DataFrame = Dataset[Row]
     * RDD和Dataset是强类型的Java对象集合,会在编译时做类型检查,DataFrame是弱类型的Row对象集合
     *
     * DataFrame比RDD性能更好
     * 1.执行计划优化：DataFrame和Dataset都是以SparkSql作为引擎,Catalyst优化器会优化逻辑计划和物理计划,可通过explain查看
     * 比如filter下推 RDD -> rdd1.join(rdd2).filter(...) | SparkSql -> select * from t1 join t2 on t1.id=t2.id where ...
     * 2.减少数据读取：SparkSql可以直接读取ORC/PARQUET等列式存储的数据源,仅查询需要的列避免全表扫描
     * 3.内存占用：RDD是函数式编程,强调数据不可变性,倾向于创建新对象而不是修改旧对象,RDD在转换过程中会生成大量临时对象,对GC造成很大压力
     *           SparkSql虽然最终返回的也是不可变的DataFrame对象,但是在内部实现过程中会尽量重用对象减少内存占用
     * RDD使用java序列化,Dataset使用Encoder编码器,将指定类型[T]映射成SparkSql的内部类型,比如Person对象有name和age字段,编码器会告诉
     * Spark在运行时动态生成代码将Person对象序列化成二进制形式且不需要反序列化就可以使用,内存占用更低并且优化处理效率,可通过'schema'查看
     *
     * Catalyst优化器
     * 1.Analysis：分析要处理的关系生成逻辑计划,可能是Sql解析器返回的AST(Abstract Syntax Tree)抽象语法树,也可能是DataFrame API
     * 2.Logical Optimization：逻辑优化,对逻辑计划使用基于规则的优化
     * 3.Physical plan：采用逻辑计划并通过与Spark执行引擎匹配的算子生成一个或多个物理计划,然后使用基于成本的模型选择要执行的计划
     * 4.Code generation：最终生成java代码字节码在每台机器上运行
     *
     * Tungsten项目
     * 固态硬盘和大功率交换机的普及大幅提升了I/O性能,使得CPU和内存成了大数据处理的新瓶颈
     * JVM GC适用于在线事务处理(OLTP)系统,JVM对象开销很大容易引起内存不足,导致CPU访问数据的吞吐量降低,损失性能
     * Spark偏向于在线分析处理(OLAP)系统,大规模并行计算对性能要求很高,Tungsten目的就是摆脱JVM垃圾回收器自己管理内存,从而避免GC带来的性能损失
     *
     * 自定义函数包括UDF和UDAF
     * UDF：类似map操作的行处理,一行输入一行输出
     * UDAF：聚合操作,多行输入一行输出,包括un-type配合DataFrame使用和safe-type配合Dataset使用
     */

    // Spark2.0以后使用SparkSession代替SqlContext作为SparkSql所有功能的入口,可以创建DataFrame,注册临时视图,执行sql,缓存表...
    // 创建SparkSession,查看源码发现SparkSession类的构造器是private,只能通过其伴生对象创建,并且使用了Builder模式
    val spark: SparkSession = SparkSession
      .builder()
      .master("local[*]")
      .appName("Spark Sql")
      .enableHiveSupport()
      .getOrCreate()
    // 创建DataFrame/Dataset三种方式：读取文件,从RDD转换,查询hive

    readFile(spark)
//    convertRDD(spark)
//    selectHive(spark)
  }

  def readFile(spark: SparkSession): Unit = {
    // a.Spark可以读写json/csv/parquet/orc/text/jdbc等多种格式数据源,返回DataFrame对象
    // 通用读数据方法：spark.read.format("").load(path),可简写如下格式
    val df: DataFrame = spark.read.json("ability/input/people.json")
    // 通过jdbc读取外部数据源数据
    val jdbcDF: DataFrame = spark.read
      .format("jdbc")
      .option("url", "jdbc:mysql://localhost:3306/test")
      .option("user", "root")
      .option("password", "root")
      .option("dbtable", "user")
      .load()
    // 通用写数据方法：df.write.format("").mode("").save(path),可简写如下格式,重复写数据会报错,需指定模式 append/overwrite/ignore
    df.write.mode(SaveMode.Append).option("header",value = true).csv("ability/output/people.csv")
    df.write.mode(SaveMode.Overwrite).saveAsTable("people")  // overwrite模式下第一次写入数据会报错路径不存在
//    df.write.mode(SaveMode.Append).insertInto("ods.people")

    // 导入隐式转换,将Scala对象或RDD转换成DataFrame/Dataset
    import spark.implicits._
    // 将DataFrame转换成Dataset
    val ds: Dataset[Person] = df.as[Person]

    // DataFrame的属性
    //    println(ds.schema)  // StructType(StructField(age,LongType,true), StructField(name,StringType,true))
    //    println(ds.isLocal)  // false
    //    println(ds.isStreaming)  // false
    //    println(ds.columns.mkString(","))  // age,name
    //    println(ds.dtypes.mkString(","))  // (age,LongType),(name,StringType)
    //    println(ds.na)  // org.apache.spark.sql.DataFrameNaFunctions@5b7b0ada
    //    ds.na.drop().show()
    //    println(ds.stat)  // org.apache.spark.sql.DataFrameStatFunctions@e460ca1
    //    ds.stat.freqItems(Seq("name")).show()
    //    println(ds.storageLevel)  // StorageLevel(1 replicas)

    // DataFrame的Column
    // 字符串操作：contains/startsWith/endsWith/substr
    // 条件表达式：when/otherwise

    // DataFrame的transform操作
    // select算子：选取列,可以基于列操作
    ds.select($"name", $"age" + 1).show()
    // distinct/filter/drop/limit算子：去重,过滤,丢弃列,限制行数
    ds.filter($"age" > 25).show()
    // groupBy/cube/rollup算子：分组
    ds.groupBy("name").count().show()
    ds.groupBy("name").pivot("name").sum("age").show()
    ds.cube("name").count().show()
    // agg算子：聚合,ds.agg(...)是ds.groupBy().agg(...)的简写
    ds.agg("name" -> "max", "age" -> "avg").show(false)
    ds.agg(Map("name" -> "max", "age" -> "avg")).show()
    // orderBy/sort/sortWithinPartitions：排序,orderBy == sort全局排序,sortWithinPartitions分区内排序,相当于hive的sort by
    ds.orderBy("name", "age").show()
    ds.orderBy(col("name"), col("age").desc).show()
    // coalesce/repartition算子：重新分区
    // intersect/union/crossjoin/join：交集、并集(不去重)、笛卡尔积(慎用)、关联
    // describe算子：描述基本的统计信息
    ds.describe().show()
    // toJson算子：以json字符串形式返回内容
    ds.toJSON.show()
    // withColumn/withColumnRenamed算子：添加列,重命名列
    ds.withColumn("age_new", $"age" + 1).show()
    ds.withColumn("level", when(col("age") < 20, "middle")
      .when(col("age") > 25, "high").otherwise("other")).show()
    ds.withColumnRenamed("name", "name_new").show()

    // DataFrame的action操作
    // show算子：以表格形式展示数据
    // collect算子：返回包含Dataset所有行(Row)的数组,所有数据都会被移动到driver节点有可能导致OOM
    // foreach算子：遍历所有行
    // foreachPartition：遍历所有分区
    // first/head算子：返回第一行
    // take/takeAsList算子：返回前n行,以列表形式返回前n行
    // count算子：统计行数

    // 除了API查询还可以创建临时视图(read-only)通过sql查询,View和RDD类似都是不可变的,临时表是session-scoped,可使用全局表global_temp.user
    ds.createOrReplaceTempView("user")
    // explain：true查看logical plan和physical plan | false只查看physical plan
//    spark.sql("select avg(age) from user").as("avg").explain(true)
  }

  def convertRDD(spark: SparkSession): Unit = {
    // b.从RDD转换
    // SparkSession包含Spark上下文对象
    val sc: SparkContext = spark.sparkContext
    val rdd: RDD[Array[String]] = sc.textFile("ability/input/people.txt").map((row: String) => row.split(","))
    // 导入隐式转换,将Scala对象或RDD转换成DataFrame/Dataset
    import spark.implicits._
    // 1).先通过样例类反射schema,将RDD[Array]映射成RDD[Person],再隐式转换成DataFrame/Dataset
    val personRDD: RDD[Person] = rdd.map[Person]((arr: Array[String]) => Person(arr(0), arr(1).trim.toInt))
    val df1: DataFrame = personRDD.toDF()
    val ds1: Dataset[Person] = personRDD.toDS()
    // 2).手动指定schema,只能转换tuple类型的RDD,且由于字段只有名称没有类型,所以只有toDF(String*)没有toDS(String*)
    val df2: DataFrame = rdd.map((arr: Array[String]) => (arr(0), arr(1).trim.toInt)).toDF("name", "age")
    // df/ds转换回rdd,df转换回来的rdd只能通过索引取值,ds转换回来的rdd可通过属性取值
    val rdd1: RDD[Row] = df1.rdd
    val rdd2: RDD[Person] = ds1.rdd
    rdd1.foreach((row: Row) => println(row.getString(0) + "," + row.getDecimal(1)))
    rdd2.foreach((person: Person) => println(person.name + "," + person.age))
    // 将聚合函数转换为查询列
    val avgCol: TypedColumn[Person, Double] = MyAverage.toColumn.name("avgAge")
    // 应用聚合函数
    ds1.select(avgCol).show()
  }

  def selectHive(spark: SparkSession): Unit = {
    // c.查询hive
    // spark操作内置hive,会在当前工作目录创建自己的hive元数据仓库 metastore_db
    // spark操作集群hive,需要将hive-site.xml添加到spark的conf目录,删除metastore_db目录并重启集群
    spark.sql("show tables").show()
  }

}

// 创建样例类
case class Person (name: String, age: BigInt)
case class AvgBuffer (var sum: BigInt, var count: BigInt)

// UDAF求平均年龄案例
object MyAverage extends Aggregator[Person, AvgBuffer, Double] {
  // 初始化
  override def zero: AvgBuffer = AvgBuffer(0, 0)
  // 聚合数据
  override def reduce(buffer: AvgBuffer, person: Person): AvgBuffer = {
    buffer.sum += person.age
    buffer.count += 1
    buffer
  }
  // 合并缓冲区中间结果
  override def merge(b1: AvgBuffer, b2: AvgBuffer): AvgBuffer = {
    b1.sum += b2.sum
    b1.count += b2.count
    b1
  }
  // 输出结果
  override def finish(reduction: AvgBuffer): Double = (reduction.sum / reduction.count).toDouble
  // 指定中间结果和最终结果的Encoder
  override def bufferEncoder: Encoder[AvgBuffer] = Encoders.product
  override def outputEncoder: Encoder[Double] = Encoders.scalaDouble
}