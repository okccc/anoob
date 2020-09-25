package spark

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}
import java.util

import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.{JdbcRDD, RDD}
import org.apache.spark.util.{AccumulatorV2, LongAccumulator}
import org.apache.spark.{HashPartitioner, Partitioner, SparkConf, SparkContext}

object S01_RDD {
  def main(args: Array[String]): Unit = {
    /*
     * Spark是基于内存的快速,通用,可扩展的大数据分析引擎
     * Spark Core 实现了Spark的基本功能,包含任务调度、内存管理、错误恢复、与存储系统交互等模块,以及RDD的相关api
     * Spark Sql 通过类Sql方式查询结构化数据,支持hive/parquet/json等多种数据源
     * Spark Streaming 对实时数据进行流式计算,提供了操作数据流的api,并且与RDD的api高度对应
     * Spark MlLib 提供常见的机器学习库,包括分类、回归、聚类、协同过滤等,还提供了模型评估、数据导入等额外的支持功能
     *
     * Driver：主要负责作业调度,是执行main方法的进程,负责创建SparkSession和RDD以及执行RDD的transformation和action操作
     * 启动Spark shell时也会启动一个Spark驱动器,就是预加载的叫作sc的SparkContext对象,驱动器停止则Spark应用结束
     * 1.把用户程序转为job
     * 2.跟踪Executor运行状况
     * 3.为执行器节点调度任务
     * 4.ui展示应用的运行状况
     *
     * Executor：主要负责执行具体计算任务,负责在Spark作业中执行任务且任务间相互独立,Spark会将故障节点的任务调度到其它节点继续运行
     * 1.负责运行组成Spark应用的任务并将结果返回给Driver
     * 2.通过自身块管理器Block Manager为程序中要求缓存的RDD提供内存式存储,RDD直接缓存在Executor进程内,任务运行时充分利用缓存数据加速运算
     * 一个executor同时只能执行一个计算任务,executor数量决定了同时处理任务的数量,partition数应当大于executor数,这俩决定了job的运行时间
     *
     * RDD弹性分布式数据集,存储的不是数据而是逻辑抽象,代表一个不可变可分区的集合
     * 弹性：checkPoint机制可以简化RDD之间的依赖关系(血缘)
     *      很多transform算子都提供了numPartitions可选参数,意味着在job的每一步都可以细粒度地控制并行度从而提高性能(分区)
     *      基于内存运算但也可以persist(计算)
     * 分布式：数据来源 & 计算 & 数据存储
     * 不可变：RDD本身不可变,transform操作会生成新的RDD
     * 可分区：每个partition的数据会发送给一个executor执行达到并行计算目的,增加分区数可以增大任务并行度充分利用集群资源
     * 数据集：1.分区的集合 2.基于分区计算的算子 3.依赖的集合 4.针对k-v类型RDD的Partitioner(可选) 5.读取hdfs数据块时会计算每个分片的地址集合(可选)
     *
     * RDD两种算子
     * transform：转换新的数据集,lazy模式调用action算子才触发计算,可以根据DAG做相应优化,合并窄依赖的转换算子减少executor与driver之间的通信
     * action：对数据集执行计算操作并将结果返回给driver
     *
     * Spark Shuffle
     * The Shuffle is an expensive operation since it involves disk I/O, data serialization, and network I/O
     * 很多transform算子会对数据重新分区,通常伴随着数据跨节点移动的过程,Shuffle负责将Map端(宽依赖左侧)处理的中间结果传输到Reduce端
     * (宽依赖右侧)进行聚合,数据在网络传输过程中对象的序列化和反序列化是分布式计算框架的主要性能瓶颈
     * "repartition"(coalesce,repartition) | "ByKey"(groupByKey,reduceByKey) | "join"(cogroup,join)
     * shuffle生成的中间结果会消耗大量内存,当内存容不下时spark会将其溢出到磁盘,造成额外的磁盘io和垃圾回收,这些文件会一直保留到RDD不再使用
     * 然后被JVM Garbage Collector回收,然而垃圾回收很长时间才会执行一次,也就意味着shuffle会消耗大量磁盘空间,可设置spark.local.dir参数
     *
     * RDD依赖类型
     * 窄依赖(narrow)：子RDD分区与父RDD分区是一对一映射关系,不存在shuffle,所有计算都在分区所在节点完成,因此分区的转换可以放在一个stage
     * 宽依赖(shuffle)：子RDD分区与父RDD分区是一对多映射关系,必然有shuffle,要等依赖的所有父RDD分区都处理完,因此宽依赖是划分stage的依据
     * DAG：transform只记录RDD的血缘关系,driver会根据lineage生成有向无环图,action操作触发计算并从头开始计算
     * 当某个分区故障时只要按照lineage重新计算即可,窄依赖只涉及所在节点内的计算,宽依赖会按照依赖关系由父分区计算所得,如果父分区没有持久化
     * 就会不断回溯直到找到存在的父分区为止,计算逻辑复杂时就会引起依赖链过长,此时可以checkpoint保存当前算好的中间结果从而缩短依赖链
     *
     * application/job -> stage -> task  每一层都是1对n的关系
     * application/job：action操作会触发SparkContext的runJob方法,此时会生成一个job在yarn中叫application
     * stage：DAG根据RDD依赖关系划分stage,宽依赖要等待shuffle执行完所以单独划分,stage个数 = 1(ResultStage) + shuffle次数
     * task：stage是一个taskSet,stage里每个partition都会发送到executor执行即对应一个task,task个数 = stage最后一个RDD的partition个数
     *
     * RDD缓存与容错：RDD的血缘机制就是RDD的容错机制
     * cache：迭代算法的中间结果会多次重用可以考虑持久化,StorageLevel参数指定存储策略,cache() = persist(StorageLevel.MEMORY_ONLY)
     * LRU(least-recently-used)策略：spark会自动监控每个节点的cache使用状况并丢弃最近最少使用的数据分区,也可以unpersist()手动释放缓存
     * 缓存：为了解决CPU和内存的速率差异问题,缓存的速率比内存快很多,内存中被CPU访问最频繁的数据和指令会被复制到CPU中的缓存
     * 序列化：存储和网络传输是通过IO流的字节序列实现,序列化就是将内存中的对象转换成字节进行持久化或网络传输,再次使用需将字节反序列化为对象
     * checkPoint：如果lineage过长可以设置检查点将RDD落地磁盘并移除前面的血缘关系,节点故障导致分区丢失时只要从检查点处重做lineage即可
     *
     * Partitioner
     * An object that defines how the elements in a key-value pair RDD are partitioned by key.
     * Maps each key to a partition ID, from 0 to numPartitions - 1.
     * HashPartitioner：按照key的hashcode值分区,可能会导致数据倾斜(默认)
     * RangePartitioner：能使数据尽量分布均匀,但是要求key必须可排序(不常用)
     *
     * Shared Variables
     * 广播变量(read-only)：driver创建的broadcast会在集群中广播,executor以只读形式访问
     * Spark的map join就是先将小表广播到每个executor的内存中供map函数使用,使计算本地化从而避免shuffle
     * 累加器(add-only)：executor运算时使用的全局变量其实是driver端变量的副本,其对变量的更新不会回传给driver,全局计算需要使用累加器
     *
     * Spark失败情况
     * driver失败最严重,标志着整个作业执行失败,需要手动重启
     * executor失败通常是所在节点故障,driver会将失败的task调度到另一个executor继续执行并移除报错的executor
     * task失败会根据依赖关系重试,重试3次仍然失败会导致整个作业失败
     */

    // 创建Spark配置信息
    // "local": 单线程  "local[4]": 4个线程  "local[*]": 线程数=CPU最大核数,最大化CPU计算能力 cat /proc/cpuinfo | grep 'cores'
    // "spark://master:7077": standalone   "yarn": yarn集群,client/cluster模式
    // 配置参数优先级：SparkConf > spark-submit > spark-defaults.conf
    val conf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("RDD")
    // SparkContext是Spark所有功能的入口,可以创建RDD,Accumulator,Broadcast
    val sc: SparkContext = new SparkContext(conf)

    // 创建RDD两种方式：读取文件,内存创建
    // a.从内存中创建RDD(测试)并指定分片数,如果不指定分片数默认=totalCores,makeRDD调用的就是parallelize
    val rdd1: RDD[Int] = sc.makeRDD(1 to 10, 2)
    val rdd2: RDD[String] = sc.makeRDD(Array("scala spark", "python pandas"))
    val rdd3: RDD[(String, Int)] = sc.makeRDD(List(("a", 1), ("c", 3), ("b", 2), ("c", 4), ("b", 5)), numSlices = 2)

    // RDD的transform操作：通用类、数学类、集合类、重新分区类
    // 算子(operator)：定义某种新的运算
    // map算子：将RDD的每个元素经过func转换后形成新的RDD,通常用于转换数据结构
    val mapRDD: RDD[Int] = rdd1.map((i: Int) => i * 2)
    // mapPartitions算子：map针对元素操作而mapPartitions针对分区操作,比如有N个元素M个分区,map会被调用N次,mapPartitions会被调用M次
    // mapPartitions效率会优于map,因为减少了发送到执行器的交互次数,但是一次发送一个分区的数据可能会导致内存溢出(OOM)
    val mapRDD02: RDD[Int] = rdd1.mapPartitions((datas: Iterator[Int]) => {
      // mapPartitions是spark算子,会调用executor执行有几个分区就调用几次,此处的map是scala函数不会调用executor
      datas.map((data: Int) => data * 2)
    })
    // mapPartitionsWithIndex算子：类似mapPartitions,但是func会带一个整数参数表示分片的索引值
    val mapRDD03: RDD[(Int, String)] = rdd1.mapPartitionsWithIndex((i: Int, datas: Iterator[Int]) => {
      datas.map((data: Int) => (data, "分区号：" + i))
    })
    // flatMap算子：当集合中的元素是一个个整体时,可以将每个输入元素映射为0或多个输出元素(扁平化操作)
    val flatMapRDD: RDD[String] = rdd2.flatMap((i: String) => {
      val words: Array[String] = i.split(" ")
      words
    })
    // filter算子：返回func函数结果值为true的元素
    val filterRDD: RDD[Int] = rdd1.filter((i: Int) => i % 2 == 0)
    // glom算子：查看每个分区情况
    rdd1.glom().collect().foreach((i: Array[Int]) => println(i.mkString(",")))  // 1,2,3,4,5  6,7,8,9,10
    // groupBy算子：按照func函数的结果值分组
    val groupByRDD: RDD[(Int, Iterable[Int])] = rdd1.groupBy((i: Int) => i % 2)  // (0,CompactBuffer(2,4))
    // distinct算子：去重后数据减少,相应的可以减少默认分区数
    val distinctRDD: RDD[Int] = sc.makeRDD(List(1, 3, 5, 9, 3, 1)).distinct(2)
    // sortBy算子：既是transform也是action,可通过4040端口jobs查看,jobs是action操作才会触发
    val sortByRDD: RDD[Int] = rdd1.sortBy((x: Int) => x, ascending=false, 1)
    // sample算子：对大数据集以指定随机种子seed(所以其实并不随机)随机抽样出数量为fraction的数据,withReplacement表示抽出的数据是否放回
    val sampleRDD: RDD[Int] = rdd1.sample(withReplacement = false, 0.5, 10)
    // 交集、并集(不去重)、差集、笛卡尔积(慎用)、拉链
    val int01: RDD[Int] = sc.makeRDD(1 to 3)
    val int02: RDD[Int] = sc.makeRDD(3 to 5)
    int01.intersection(int02).collect().mkString(",")  // 3
    int01.union(int02).collect().mkString(",")  // 1,2,3,3,4,5
    int01.subtract(int02).collect().mkString(",")  // 1,2
    int01.cartesian(int02).collect().mkString(",")  // (1,3),(1,4),(1,5),(2,3),(2,4),(2,5),(3,3),(3,4),(3,5)
    int01.zip(int02).collect().mkString(",")  // (1,3),(2,4),(3,5)
    // cogroup算子：(K,V)+(K,W) -> (K,(Iterable<V>,Iterable<W>))
    // join算子：(K,V)+(K,W) -> (K,(V,W))
    val t1: RDD[(String, String)] = sc.makeRDD(List(("grubby", "orc"), ("moon", "ne"), ("ted", "ud")))
    val t2: RDD[(String, Int)] = sc.makeRDD(List(("grubby", 18), ("moon", 19), ("ted", 20)))
    t1.cogroup(t2).collect().foreach(println)  // (moon,(CompactBuffer(ne),CompactBuffer(19)))...
    t1.join(t2).collect().foreach(println)  // (moon,(ne,19)) (ted,(ud,20)) (grubby,(orc,18))
    // 广播变量案例：join操作有shuffle过程性能较差,可以考虑将小表作为broadcast
    val broadcast: Broadcast[List[(String, Int)]] = sc.broadcast(List(("grubby", 18), ("moon", 19), ("ted", 20)))
    println(broadcast.value)  // List((grubby,18), (moon,19), (ted,20))
    val res: RDD[(String, (String, Int))] = t1.map((t: (String, String)) => {
      var v2: Int = 0
      for (i <- broadcast.value) {
        if (t._1 == i._1) v2 = i._2
      }
      (t._1, (t._2, v2))
    })
    res.collect().foreach(println)  // (moon,(ne,19)) (ted,(ud,20)) (grubby,(orc,18))
    // 累加器案例
    val intRDD: RDD[Int] = sc.makeRDD(List(1,2,3,4),numSlices = 2)
    val stringRDD: RDD[String] = sc.makeRDD(List("hadoop", "hive", "spark", "kafka", "flume"))
    // 在driver中创建计数器counter,对于executor来说就是闭包内的变量,是不可见的
    var counter: Int = 0
    // spark将job分解成多个task,每个task对应一个executor,执行之前spark会计算task的closure(闭包)并将其序列化发送到每个executor
    // closure is those variables and methods which must be visible for the executor to perform its computations on the RDD. This closure is serialized and sent to each executor.
    // executor操作的是发送过来的序列化的counter副本,所以driver中的counter始终=0
    intRDD.foreach((i: Int) => counter += i)
    println("counter = " + counter)  // counter = 0
    // 使用累加器求和,driver将数据传给executor,executor还能将数据回传给driver(分布式共享只写变量)
    val accumulator: LongAccumulator = sc.longAccumulator
    // 执行累加器累加功能
    intRDD.foreach((i: Int) => accumulator.add(i))
    println("counter = " + accumulator.value)  // counter = 10
    // 创建自定义累加器对象
    val myAccumulator = new MyAccumulator
    // 向SparkContext注册该累加器
    sc.register(myAccumulator)
    // 执行累计功能
    stringRDD.foreach((i: String) => myAccumulator.add(i))
    println(myAccumulator.value)  // [hadoop, hive]

    // coalesce算子：合并分区默认关闭shuffle
    val coalesceRDD: RDD[Int] = rdd1.coalesce(1)
    // repartition算子：简单封装了coalesce并开启shuffle
    val repartitionRDD: RDD[Int] = rdd1.repartition(1)
    // partitionBy算子：传入分片器对pairRDD按照key重新分区,数据跨节点移动称为shuffle,可以默认HashPartitioner也可以自定义partitioner
    val partitionByRDD: RDD[(String, Int)] = rdd3.partitionBy(new HashPartitioner(3))
    val myPartitionByRDD: RDD[(String, Int)] = rdd3.partitionBy(MyPartitioner(2))

    // groupByKey算子：按照key分组直接shuffle
    val groupByKeyRDD: RDD[(String, Iterable[Int])] = rdd3.groupByKey()
    groupByKeyRDD.collect().foreach(println)  // (a,CompactBuffer(1)) (b,CompactBuffer(2, 5)) (c,CompactBuffer(3, 4))
                  groupByKeyRDD.map((t: (String, Iterable[Int])) => {(t._1, t._2.sum)}).collect().foreach(println)  // (a,1) (b,7) (c,7)

    // reduceByKey算子：按照key分组并且在shuffle之前有combine预聚合操作提高效率,性能优于groupByKey
    val reduceByKeyRDD: RDD[(String, Int)] = rdd3.reduceByKey((x: Int, y: Int) => x + y)
    reduceByKeyRDD.collect().foreach(println)  // (a,1) (b,7) (c,7)

    // aggregateByKey算子：按照key分组,先在分区内对zeroValue和value聚合,再在分区间聚合
    // ClassTag：表示反射,可以自动推断类型
    // zeroValue：给每个分区中的每一个key一个初始值,比如 0 或者 ""
    // seqOp：在每个分区内用初始值逐步迭代value
    // combOp：在分区间合并结果
    rdd3.glom().collect().foreach((i: Array[(String, Int)]) => println(i.mkString(",")))  // (a,1),(b,2)  (c,3),(c,4),(b,5)
    // 需求：取出每个分区相同key的最大值,然后相加
    val aggregateByKeyRDD: RDD[(String, Int)] = rdd3.aggregateByKey(0)(seqOp = math.max, combOp = (_: Int)+(_: Int))
    aggregateByKeyRDD.collect().foreach(println)  // (a,1) (b,7) (c,4)
    // 需求：先在分区内累加,再在分区间累加(aggregateByKey版WordCount)
    val aggregateByKeyRDD2: RDD[(String, Int)] = rdd3.aggregateByKey(0)(seqOp = (_: Int)+(_: Int), combOp = (_: Int)+(_: Int))
    aggregateByKeyRDD2.collect().foreach(println)  // (a,1) (b,7) (c,7)
    // foldByKey算子：是aggregateByKey的简化,seqOp和combOp相同
    val foldByKeyRDD: RDD[(String, Int)] = rdd3.foldByKey(0)(func = (_: Int)+(_: Int))
    foldByKeyRDD.collect().foreach(println)  // (a,1) (b,7) (c,7)

    // combineByKey算子：按照key分组,先在分区内聚合,再在分区间聚合,并统计聚合次数(累加器)
    // createCombiner: 创建key对应的累加器的初始值
    // mergeValue: 将key的累加器对应的当前值与新的值进行合并
    // mergeCombiners: 将各个分区之间同一个key的累加器对应的结果进行合并
    val combineByKeyRDD: RDD[(String, (Int, Int))] = rdd3.combineByKey((i: Int) => (i,1), (acc:(Int,Int), v: Int) => (acc._1 + v, acc._2 + 1), (acc1:(Int,Int), acc2:(Int,Int))=>(acc1._1 + acc2._1, acc1._2 + acc2._2))
    combineByKeyRDD.collect().foreach(println)  // (b,(7,2)) (a,(1,1)) (c,(7,2))
    val average: RDD[(String, Int)] = combineByKeyRDD.map((t: (String, (Int, Int))) => {(t._1, t._2._1/t._2._2)})
    average.collect().foreach(println)  // (b,3) (a,1) (c,3)

    // sortByKey算子：按照key排序,key本身必须实现Ordered接口
    rdd3.sortByKey().collect().mkString(",")  // (a,1),(b,2),(b,5),(c,3),(c,4)
    // mapValues算子：key不变只操作value
    println(rdd3.mapValues((i: Int) => i + 1).collect().mkString(","))  // (a,2),(c,4),(b,3),(c,5),(b,6)

    // RDD的action操作：driver类、executor类
    // driver类：返回值通常是driver端的内存变量,collect/count/foreach,远端executor计算完成后会回传数据给driver展示,数据太大可能会OOM
    // collect算子：返回包含RDD所有元素的数组,所有数据都会被加载到driver节点有可能导致OOM,建议使用take(n)
    // foreach算子：遍历元素
    // foreachPartition算子：遍历分区
    // first算子：返回第一个元素,如果是文件则返回第一行
    // take算子：返回前n个元素
    // takeOrdered算子：返回排序后最小的几个元素
    // top算子：返回排序后最大的几个元素
    // count算子：统计元素个数
    // countByKey算子：统计每个key的元素个数返回Map集合
    // reduce算子：归约,化简
    val res1: Int = rdd1.reduce((x: Int, y: Int) => x + y)
    val res2: (String, Int) = rdd3.reduce((x: (String, Int), y: (String, Int)) => (x._1 + y._1, x._2 + y._2))
    // aggregate算子：先在分区内对zeroValue和value聚合,再在分区间聚合
    // ClassTag：表示反射,可以自动推断类型
    // zeroValue：初始值,比如 0 或者 ""
    // seqOp：在每个分区内用初始值逐步迭代value
    // combOp：在分区间合并结果,分区间聚合也会加上初始值和aggregateByKey不同,所以结果是58不是57
    val i1: Int = rdd1.aggregate(1)(seqOp = (_: Int)+(_: Int), combOp = (_: Int)+(_: Int))
    println(i1)  // 58
    // fold算子：是aggregate的简化,seqOp和combOp一样
    val i2: Int = rdd1.fold(1)(op = (_: Int)+(_: Int))
    println(i2)  // 58

    // executor类：会在集群的每个executor节点就地执行输出到相应文件
    // saveAsTextFile()：将元素以文本格式保存到文件,spark会对元素调用toString方法,output目录下.crc文件是验证,生成的文件是part-0000*
    // saveAsSequenceFile()：将元素以Hadoop sequenceFile格式保存,只针对PairRDD
    // saveAsObjectFile()：将元素序列化成对象保存到文件

    // b.从本地/hdfs等文件系统创建RDD(常用),spark会为文件的每个block创建partition,所以分片数不能小于文件的分块数
    // local/standalone模式 -> file:///input/aaa.txt
    // yarn模式 -> hdfs://cdh1:9000/user/spark/input/aaa.txt 或者 hdfs:///user/spark/input/aaa.txt
    val lines: RDD[String] = sc.textFile("ability/input/aaa.txt", minPartitions=2)
    // 设置检查点保存目录,通常存hdfs有副本更安全
//    sc.setCheckpointDir("hdfs://cdh1:9000/checkpoint")
    sc.setCheckpointDir("scala/cp")
    // 扁平化
    val words: RDD[String] = lines.flatMap((line: String) => line.split(" "))
    // 结构转换
    val word_one: RDD[(String, Int)] = words.map((word: String) => (word, 1))
    word_one.cache()
    // 查看RDD的lineage和依赖类型
    println(word_one.toDebugString)
    /**
     * (1) MapPartitionsRDD[71] at map at S01_RDD.scala:222 [Memory Deserialized 1x Replicated]
     * |  MapPartitionsRDD[70] at flatMap at S01_RDD.scala:220 [Memory Deserialized 1x Replicated]
     * |  ability/input/aaa.txt MapPartitionsRDD[69] at textFile at S01_RDD.scala:215 [Memory Deserialized 1x Replicated]
     * |  ability/input/aaa.txt HadoopRDD[68] at textFile at S01_RDD.scala:215 [Memory Deserialized 1x Replicated]
     */
    println(word_one.dependencies)  // List(org.apache.spark.OneToOneDependency@2e3f79a2)
    // 分组聚合
    val word_sum: RDD[(String, Int)] = word_one.reduceByKey((x: Int, y: Int) => x + y)
    // 在此处做检查点容错
    word_sum.checkpoint()
    // action操作
    word_sum.foreach(println)
    // 注意对比做检查点前后该RDD的lineage
    println(word_sum.toDebugString)
    /**
     * checkpoint之前
     * (2) ShuffledRDD[72] at reduceByKey at S01_RDD.scala:234 []
     * +-(2) MapPartitionsRDD[71] at map at S01_RDD.scala:222 []
     * |      CachedPartitions: 2; MemorySize: 896.0 B; ExternalBlockStoreSize: 0.0 B; DiskSize: 0.0 B
     * |  MapPartitionsRDD[70] at flatMap at S01_RDD.scala:220 []
     * |  ability/input/aaa.txt MapPartitionsRDD[69] at textFile at S01_RDD.scala:215 []
     * |  ability/input/aaa.txt HadoopRDD[68] at textFile at S01_RDD.scala:215 []
     *
     * checkpoint之后
     * (2) ShuffledRDD[72] at reduceByKey at S01_RDD.scala:234 []
     * |  ReliableCheckpointRDD[73] at foreach at S01_RDD.scala:238 []
     */

    // 案例：统计log.txt文件中每一个省份被点击次数top3的广告
    // 数据结构：时间戳,省份,城市,用户,广告
    // 分析：以(省份+广告)作为key分组聚合再排序,最终结果是 (pro, List((ad1,cnt1),(ad2,cnt2),(ad3,cnt3)))
    // 读取文件
    val file: RDD[String] = sc.textFile("ability/input/log.txt")
    // 结构转换
    val pro_ad_1: RDD[((String, String), Int)] = file.map((lines: String) => {
      val words: Array[String] = lines.split(" ")
      // 将(pro,ad)作为key, 1作为value
      ((words(1), words(4)), 1)
    })  // ((4,12),1)...
    // 计算每个省份的每个广告被点击次数
    val pro_ad_sum: RDD[((String, String), Int)] = pro_ad_1.reduceByKey((x: Int, y: Int) => {x+y})  // ((4,12),25)...
    // 将pro作为key,(ad,sum)作为value
    val pro_ad_sum1: RDD[(String, (String, Int))] = pro_ad_sum.map((t: ((String, String), Int)) => {(t._1._1, (t._1._2,t._2))})  // (4,(12,25))...
    // 以省份为key,对同一省份所有广告做分组聚合
    val pro_group: RDD[(String, Iterable[(String, Int)])] = pro_ad_sum1.groupByKey()  // (4,CompactBuffer((12,25),(15,17)...))...
    // 将pro对应的value排序
    val pro_ad_sort: RDD[(String, List[(String, Int)])] = pro_group.mapValues((t: Iterable[(String, Int)]) => {
      // tuple本身无法排序,需要转换成list排序
      val tuples: List[(String, Int)] = t.toList.sortWith((x:(String, Int), y: (String, Int)) => {x._2 > y._2}).take(3)
      tuples
    })
    //    pro_ad_sort.collect().foreach(println)  // (4,List((12,25), (2,22), (16,22)))...

    // c.通过jdbc读取外部数据源mysql数据,和sqoop类似
    // mysql配置信息
//    val driver = "com.mysql.jdbc.Driver"
    val url = "jdbc:mysql://localhost:3306/test"
    val user = "root"
    val password = "root"
    // 创建JdbcRDD访问数据库
    val jdbcRDD: JdbcRDD[Unit] = new JdbcRDD(
      sc = sc,
      // () => connection 无参函数返回一个数据库连接对象,RDD会负责关闭连接
      getConnection = () => DriverManager.getConnection(url, user, password),
      // 查询sql,必须包含两个?占位符即上下限边界
      sql = "select * from user where id > ? and id < ?",
      // 上下限边界限制查询数据范围
      lowerBound = 1,
      upperBound = 100,
      // 利用多个executor同时查询互不交叉的数据范围达到并行抽取目的,该抽取方式受限于mysql自身的并发读性能
      // hbase是分布式数据库,存储数据时采用了分区思想(region),基于region的导入方式是真正的并行导入
      numPartitions = 2,
      // 处理结果集
      mapRow = (rs: ResultSet) => println(rs.getInt("id") + "," + rs.getString("name"))
    )
    // action操作
    jdbcRDD.collect()

    // 保存数据
    val dataRDD: RDD[(String, Int)] = sc.makeRDD(List(("grubby",18),("moon",19),("fly",20)),2)
    // 1).在driver端预先创建好连接对象,但是driver端变量传递到executor端需要序列化,而Connection没有实现Serializable接口 org.apache.spark.SparkException: Task not serializable
//    val conn: Connection = DriverManager.getConnection(url, user, password)
//    // 遍历RDD所有元素
//    dataRDD.foreach{
//      case (name, age) =>
//        // 执行sql
//    }

    // 2).遍历RDD所有元素,foreach算子是action操作,以下代码是在executor端执行
//    dataRDD.foreach{
//      case (name, age) =>
//        // 每次执行sql都会创建连接对象,java连接mysql是JVM和Mysql两个进程之间的交互,性能较差,并且mysql的连接数也是有限制的
//        val conn: Connection = DriverManager.getConnection(url, user, password)
//        // 执行sql
//    }

    // 3).遍历RDD所有分区,foreachPartition算子是action操作,以下代码是在executor端执行
    dataRDD.foreachPartition((datas: Iterator[(String, Int)]) => {
      // 创建连接对象,有几个分区就创建几次连接,在executor端初始化连接对象避免网络传输导致的序列化问题
      val conn: Connection = DriverManager.getConnection(url, user, password)
      // 遍历每个分区中的所有元素
      datas.foreach{
        case (name, age) =>
          // 关闭自动提交
          conn.setAutoCommit(false)
          // 插入sql,包含一个或多个?占位符
          val sql = "insert into user values(null,?,?)"
          /**
           * PreparedStatement优点
           * 1.预编译sql放入缓冲   区提高效率,且下次执行相同sql时直接使用数据库缓冲区
           * 2.预编译sql可以防止sql注入
           * 普通拼接 -> sql经过解析器编译并执行,传递的参数也会参与编译,select * from user where name = 'tom' or '1=1'; 这里的 or '1=1' 会被当成sql指令运行,or被当成关键字了
           * 预编译 -> sql预先编译好等待传参执行,传递的参数就只是变量值,select * from user where name = "tom' or '1=1"; 这里的 tom' or '1=1 是一个变量整体,or也就失效了
           */
          // 创建PreparedStatement对象
          val ps: PreparedStatement = conn.prepareStatement(sql)
          // 给参数赋值
          ps.setString(1, name)
          ps.setInt(2, age)
          // 执行更新操做
          ps.executeUpdate()
          // 查看在数据库中具体执行的sql
//          println(ps.toString)  // com.mysql.jdbc.JDBC4PreparedStatement@3973462d: insert into user values(null,'grubby',18)
          // 手动提交
          conn.commit()
          ps.close()
      }
      conn.close()
    })
  }

  // 自定义分片器
  case class MyPartitioner(partitions: Int) extends Partitioner {
    override def numPartitions: Int = partitions
    override def getPartition(key: Any): Int = key match {
      // 自定义逻辑
      case null => 0
      case _ => 1
    }
  }

  // 自定义累加器
  class MyAccumulator extends AccumulatorV2[String, util.ArrayList[String]] {
    // 创建保存数据的集合
    private val list = new util.ArrayList[String]()
    // 判断当前累加器是否为初始状态
    override def isZero: Boolean = list.isEmpty
    // 复制累加器对象
    override def copy(): AccumulatorV2[String, util.ArrayList[String]] = {
      val myAcc: MyAccumulator = new MyAccumulator
      myAcc
    }
    // 重置累加器对象
    override def reset(): Unit = list.clear()
    // 往累加器添加数据
    override def add(v: String): Unit = {
      if (v.contains("h")) list.add(v)
    }
    // 合并累加器
    override def merge(other: AccumulatorV2[String, util.ArrayList[String]]): Unit = list.addAll(other.value)
    // 获取累加器结果
    override def value: util.ArrayList[String] = list
  }

}