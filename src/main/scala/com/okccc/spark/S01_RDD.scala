package com.okccc.spark

import java.sql.{Connection, DriverManager}
import java.util

import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.util.{AccumulatorV2, LongAccumulator}
import org.apache.spark.{HashPartitioner, SparkConf, SparkContext}

object S01_RDD {
  def main(args: Array[String]): Unit = {
    /*
     * Spark是基于内存的快速,通用,可扩展的大数据分析引擎
     * Spark Core 实现了Spark的基本功能,包含任务调度、内存管理、错误恢复、与存储系统交互等模块,以及RDD的相关api
     * Spark Sql 通过类Sql方式查询结构化数据,支持hive/parquet/json等多种数据源
     * Spark Streaming 对实时数据进行流式计算,提供了操作数据流的api,并且与RDD的api高度对应
     * Spark MlLib 提供常见的机器学习库,包括分类、回归、聚类、协同过滤等,还提供了模型评估、数据导入等额外的支持功能
     *
     * driver和executor是计算组件,master和worker是资源组件对应yarn中的RM/NM,两者不直接交互而是通过application master解耦
     * driver: 负责集群的作业调度,是执行main方法的进程 1.创建SparkContext/SparkSession/RDD 2.执行RDD的transformation和action操作
     * executor: 负责执行具体计算,节点故障时会将当前任务调度到其它节点继续运行 1.将结果返回给driver 2.缓存RDD加速运算
     *
     * RDD弹性分布式数据集,存储的不是数据而是计算逻辑,代表一个不可变可分区的集合
     * 弹性: coalesce/repartition灵活调整分区数控制并行度(分区) | checkPoint简化RDD依赖关系(血缘) | 基于内存运算也可以persist(计算)
     * 分布式: 数据源 & 计算 & 存储
     * 不可变: RDD本身不可变,transform操作会生成新的RDD
     * 可分区: 每个partition都会占用一个executor,分区之间并行计算相互独立,shuffle时可能快的分区要等慢的分区
     * 5大特性: 1.分区 2.基于分区计算的算子 3.依赖 4.K-V类型RDD的分区器(可选) 5.存储每个分区的preferred location使计算本地化(可选)
     *
     * RDD两种算子
     * transform: 转换新的数据集,lazy模式调用action算子才触发计算,可以根据DAG做相应优化,合并窄依赖的转换算子减少executor与driver间通信
     * action: 对数据集执行计算操作并将结果返回给driver,判断算子是transform还是action就看返回结果类型是不是RDD
     *
     * Spark Shuffle
     * The Shuffle is an expensive operation since it involves disk I/O, data serialization, and network I/O
     * Shuffle: 将数据打乱重新组合(洗牌),会伴随着数据的跨节点移动,数据在网络传输过程中对象的序列化和反序列化是分布式计算框架的主要性能瓶颈
     * 涉及Shuffle操作的算子: "repartition"(coalesce,repartition) | "join" | "ByKey"(groupByKey,reduceByKey) | "distinct"
     * Shuffle负责将map端(宽依赖左侧)处理的中间结果传输到reduce端(宽依赖右侧)进行聚合,中间结果会消耗大量内存,容不下时spark会将其溢出到磁盘,
     * 造成额外的磁盘io和垃圾回收,这些文件会一直保留到RDD不再使用然后被JVM GC回收,然而垃圾回收很长时间才会执行一次,所以同时也会消耗大量磁盘空间
     *
     * Spark数据倾斜
     * 本质是shuffle过程中key分布不均匀,需要针对具体算子具体分析,可以在yarn监控页面查看Stages的task列表运行时间
     * 1.提高并行度 | 2.使用map join代替reduce join | 3.给key增加随机前后缀 | 4.通过hive etl预处理
     *
     * RDD依赖类型
     * 窄依赖(narrow): 子RDD分区与父RDD分区是一对一映射关系,不存在shuffle,所有计算都在分区所在节点完成,因此分区的转换可以放在一个stage
     * 宽依赖(shuffle): 子RDD分区与父RDD分区是一对多映射关系,必然有shuffle,要等父RDD的所有依赖分区都处理完,因此宽依赖是划分stage的依据
     * DAG: transform只记录RDD的血缘关系,driver会根据lineage生成有向无环图,action操作触发计算并从头开始计算
     * 当某个分区故障时只要按照lineage重新计算即可,计算逻辑复杂时就会导致依赖链过长,checkpoint可以保存当前算好的中间结果从而缩短依赖链
     *
     * application/job -> stage -> task  每一层都是1对n的关系
     * application/job: action操作会触发SparkContext的runJob方法,此时会生成一个job在yarn中叫application
     * stage: DAG根据RDD依赖关系划分stage,宽依赖要等待shuffle执行完所以单独划分,stage个数 = 1(ResultStage) + shuffle次数
     * task: stage是一个taskSet,stage里的每个partition都会发送到executor执行,task个数 = stage最后一个RDD的partition个数
     *
     * RDD缓存与容错: RDD的血缘机制就是RDD的容错机制
     * 缓存: 为了解决CPU和内存的速率差异问题,缓存的速率比内存快很多,内存中被CPU访问最频繁的数据和指令会被复制到CPU中的缓存
     * 序列化: 存储和网络传输是通过IO流的字节序列实现,序列化就是将内存中的对象转换成字节进行持久化或网络传输,再次使用需将字节反序列化为对象
     * cache: 迭代算法中间结果多次重用可以考虑持久化,cache就是调用的persist,def cache() = persist(StorageLevel.MEMORY_ONLY)
     * LRU(least-recently-used)策略: spark会自动监控每个节点的cache使用状况并丢弃最近最少使用的数据分区,也可以unpersist()手动释放缓存
     * checkPoint: 重用/重要数据/lineage过长都可以设置检查点将RDD持久化并移除前面的血缘关系,节点故障导致分区丢失时只要从检查点处重做即可
     *
     * yarn cluster和yarn client
     * yarn中的每个Application实例都有一个ApplicationMaster进程,负责向ResourceManager请求资源,然后通知NodeManager为Application启动Container
     * In client mode, the driver runs in the client process, and the application master is only used for requesting resources from YARN.
     * In cluster mode, the driver runs inside an application master process which is managed by YARN on the cluster, and the client can go away after initiating the application.
     *
     * Partitioner
     * An object that defines how the elements in a key-value pair RDD are partitioned by key.
     * Maps each key to a partition ID, from 0 to numPartitions - 1.
     * HashPartitioner: 按照key的hashcode值分区,可能会导致数据倾斜(默认)
     * RangePartitioner: 能使数据尽量分布均匀,但是要求key必须可排序(不常用)
     *
     * Shared Variables
     * 广播变量(read-only): map join就是将小表广播到每个executor的内存中供map函数使用,使计算本地化从而避免shuffle
     * 累加器(add-only): executor运算时使用的全局变量其实是driver端变量的副本,其对变量的更新不会回传给driver,全局计算需要使用累加器
     *
     * Spark序列化
     * java.io.Serializable接口可以序列化任何类,但是比较重(字节多),不太适合大数据分布式应用场景
     * Spark2.0支持Kryo序列化机制,速度提升10倍,RDD在shuffle数据时,数组和字符串等简单数据类型已经在spark内部使用Kryo序列化
     *
     * Spark调优
     * 1.资源层面：spark-submit脚本的num-executors,executor-cores,executor-memory参数调节并行度
     * 2.算子层面：mapPartitions/foreachPartition/filter+coalesce/repartition/reduceByKey
     * 3.RDD层面：缓存和持久化/广播变量/kryo序列化/Shuffle数据倾斜/小文件处理
     */

    // 创建Spark配置信息
    val conf: SparkConf = new SparkConf()
      // "local": 单线程  "local[4]": 4个线程  "local[*]": 线程数=cpu最大核数,最大化cpu计算能力 cat /proc/cpuinfo | grep 'cores'
      // "spark://master:7077": standalone   "yarn": yarn集群,client/cluster模式
      .setMaster("local[*]")
      .setAppName("RDD")
      // 替换默认的序列化机制
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      // 注册需要使用Kryo序列化的自定义类
      .registerKryoClasses(Array(classOf[MyAccumulator]))
    // SparkContext是Spark所有功能的入口,可以创建RDD,Accumulator,Broadcast
    val sc: SparkContext = new SparkContext(conf)
    // 设置日志级别
    sc.setLogLevel("warn")
    // 创建RDD两种方式: 内存创建(测试),读取文件(常用)
    transform01(sc)
    transform02(sc)
    transform03(sc)
    action(sc)
    fileRDD(sc)
  }

  def transform01(sc: SparkContext): Unit = {
    // a.从内存中创建RDD并指定分片数,如果不指定分片数默认=totalCores,makeRDD调用的就是parallelize
    // 1.RDD的通用类transform操作
    val rdd1: RDD[Int] = sc.makeRDD(1 to 10, 2)
    val rdd2: RDD[String] = sc.makeRDD(Array("scala spark", "python pandas"))
    // map算子: 操作元素,通常用于转换数据结构
    val mapRDD: RDD[Int] = rdd1.map((i: Int) => i * 2)
    // mapPartitions算子: 操作分区,效率优于map,因为减少了驱动器和执行器的交互次数,但是一次发送一个分区的数据可能会OOM
    val mapRDD02: RDD[Int] = rdd1.mapPartitions((iterator: Iterator[Int]) => {
      // 这里的map是scala函数不会调用executor
      iterator.map((data: Int) => data * 2)
    })
    // mapPartitionsWithIndex算子: 类似mapPartitions,但是func会带一个整数参数表示分片的索引值
    val mapRDD03: RDD[(Int, String)] = rdd1.mapPartitionsWithIndex((i: Int, iterator: Iterator[Int]) => {
      iterator.map((data: Int) => (data, "分区号: " + i))
    })
    // flatMap算子: 当集合中的元素是一个个整体时,可以将每个输入元素映射为0个或多个输出元素(扁平化操作)
    val flatMapRDD: RDD[String] = rdd2.flatMap((i: String) => i.split(" "))
    flatMapRDD.foreach(println)
    // distinct算子: 去重后数据减少,相应的可以减少默认分区数
    val distinctRDD: RDD[Int] = sc.makeRDD(List(1, 3, 5, 9, 3, 1)).distinct(2)
    // filter算子: 返回func函数结果值为true的元素,过滤数据后可能导致数据倾斜,可以coalesce重组数据
    val filterRDD: RDD[Int] = rdd1.filter((i: Int) => i % 2 == 0)
    // glom算子: 查看每个分区情况
    rdd1.glom().collect().foreach((i: Array[Int]) => println(i.mkString(",")))  // 1,2,3,4,5  6,7,8,9,10
    // groupBy算子: 按照func函数的结果值分组
    val groupByRDD: RDD[(Int, Iterable[Int])] = rdd1.groupBy((i: Int) => i % 2)  // (0,CompactBuffer(2,4))
    // sortBy算子: 既是transform也是action,可通过4040端口jobs查看,jobs是action操作才会触发
    val sortByRDD: RDD[Int] = rdd1.sortBy((x: Int) => x, ascending = false, 1)
    // sample算子: 对大数据集以指定随机种子seed(所以其实并不随机)抽样出数量为fraction的数据,withReplacement表示抽出的数据是否放回
    val sampleRDD: RDD[Int] = rdd1.sample(withReplacement = false, 0.5, 10)
  }

  def transform02(sc: SparkContext): Unit = {
    // 2.RDD的集合类transform操作
    val rdd1: RDD[Int] = sc.makeRDD(1 to 3)
    val rdd2: RDD[Int] = sc.makeRDD(3 to 5)

    // 交集、并集(不去重)、差集、笛卡尔积(慎用)、拉链
    rdd1.intersection(rdd2).collect().mkString(",")  // 3
    rdd1.union(rdd2).collect().mkString(",")  // 1,2,3,3,4,5
    rdd1.subtract(rdd2).collect().mkString(",")  // 1,2
    rdd1.cartesian(rdd2).collect().mkString(",")  // (1,3),(1,4),(1,5),(2,3),(2,4),(2,5),(3,3),(3,4),(3,5)
    rdd1.zip(rdd2).collect().mkString(",")  // (1,3),(2,4),(3,5)

    // cogroup算子: (K,V)+(K,W) -> (K,(Iterable<V>,Iterable<W>))
    // join算子: (K,V)+(K,W) -> (K,(V,W))
    val t1: RDD[(String, String)] = sc.makeRDD(List(("grubby", "orc"), ("moon", "ne"), ("ted", "ud")))
    val t2: RDD[(String, Int)] = sc.makeRDD(List(("grubby", 18), ("moon", 19), ("ted", 20)))
    t1.cogroup(t2).collect().foreach(println)  // (moon,(CompactBuffer(ne),CompactBuffer(19)))...
    t1.join(t2).collect().foreach(println)  // (moon,(ne,19)) (ted,(ud,20)) (grubby,(orc,18))

    // 广播变量案例: join操作有shuffle过程性能较差,可以考虑将小表作为broadcast
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
    // 在driver中创建计数器cnt,对于executor来说就是闭包内的变量,是不可见的
    var cnt: Int = 0
    // spark将job分解成多个task,每个task对应一个executor,执行之前spark会计算task的closure(闭包)并将其序列化发送到每个executor
    // closure is those variables and methods which must be visible for the executor to perform its computations on the RDD. This closure is serialized and sent to each executor.
    // executor操作的是发送过来的序列化的counter副本,所以driver中的cnt始终为0
    intRDD.foreach((i: Int) => cnt += i)
    println("cnt = " + cnt)  // cnt = 0
    // 使用累加器,driver将数据传给executor,executor还能将数据回传给driver(分布式共享只写变量)
    val accumulator: LongAccumulator = sc.longAccumulator
    // 执行累加功能
    intRDD.foreach((i: Int) => accumulator.add(i))
    println("cnt = " + accumulator.value)  // cnt = 10
    // 创建自定义累加器对象
    val myAccumulator: MyAccumulator = new MyAccumulator
    // 向SparkContext注册该累加器
    sc.register(myAccumulator)
    // 执行累计功能
    stringRDD.foreach((i: String) => myAccumulator.add(i))
    println(myAccumulator.value)  // [hadoop, hive]
  }

  def transform03(sc: SparkContext): Unit = {
    // 3.RDD的shuffle类transform操作
    val rdd1: RDD[Int] = sc.makeRDD(1 to 10, 2)
    val rdd2: RDD[(String, Int)] = sc.makeRDD(List(("a", 1), ("c", 3), ("b", 2), ("c", 4), ("b", 5)), numSlices = 2)

    // coalesce算子: 合并分区默认关闭shuffle,即不会将数据打乱重新组合,这样数据容易分布不均匀导致数据倾斜,需要手动开启shuffle
    val coalesceRDD: RDD[Int] = rdd1.coalesce(1)
    // repartition算子: coalesce可以扩大分区,但是不开启shuffle是不起作用的,为了简化操作repartition将其封装了一下
    val repartitionRDD: RDD[Int] = rdd1.repartition(1)
    // partitionBy算子: 传入分片器对pairRDD按照key重新分区,默认HashPartitioner也可以自定义partitioner
    val partitionByRDD: RDD[(String, Int)] = rdd2.partitionBy(new HashPartitioner(3))

    // groupByKey算子: 按照key分组直接shuffle
    val groupByKeyRDD: RDD[(String, Iterable[Int])] = rdd2.groupByKey()
    groupByKeyRDD.collect().foreach(println)  // (a,CompactBuffer(1)) (b,CompactBuffer(2, 5)) (c,CompactBuffer(3, 4))
    groupByKeyRDD.map((t: (String, Iterable[Int])) => {(t._1, t._2.sum)}).collect().foreach(println)  // (a,1) (b,7) (c,7)
    // reduceByKey算子: 按照key分组并且在shuffle之前有combine预聚合操作提高效率,性能优于groupByKey
    val reduceByKeyRDD: RDD[(String, Int)] = rdd2.reduceByKey((x: Int, y: Int) => x + y)
    reduceByKeyRDD.collect().foreach(println)  // (a,1) (b,7) (c,7)
    // sortByKey算子: 按照key排序,key本身必须实现Ordered接口
    rdd2.sortByKey().collect().mkString(",")  // (a,1),(b,2),(b,5),(c,3),(c,4)
    // mapValues算子: key不变只操作value
    println(rdd2.mapValues((i: Int) => i + 1).collect().mkString(","))  // (a,2),(c,4),(b,3),(c,5),(b,6)

    // aggregateByKey算子: 按照key分组,先在分区内对zeroValue和value聚合,再在分区间聚合
    // ClassTag: 表示反射,可以自动推断类型
    // zeroValue: 给每个分区中的每一个key一个初始值,比如 0 或者 ""
    // seqOp: 在每个分区内用初始值逐步迭代value
    // combOp: 在分区间合并结果
    rdd2.glom().collect().foreach((i: Array[(String, Int)]) => println(i.mkString(",")))  // (a,1),(b,2)  (c,3),(c,4),(b,5)
    // 需求: 取出每个分区相同key的最大值,然后相加
    val aggregateByKeyRDD: RDD[(String, Int)] = rdd2.aggregateByKey(0)(seqOp = math.max, combOp = (_: Int)+(_: Int))
    aggregateByKeyRDD.collect().foreach(println)  // (a,1) (b,7) (c,4)
    // 需求: 先在分区内累加,再在分区间累加(aggregateByKey版WordCount)
    val aggregateByKeyRDD2: RDD[(String, Int)] = rdd2.aggregateByKey(0)(seqOp = (_: Int)+(_: Int), combOp = (_: Int)+(_: Int))
    aggregateByKeyRDD2.collect().foreach(println)  // (a,1) (b,7) (c,7)
    // foldByKey算子: 是aggregateByKey的简化,seqOp和combOp相同
    val foldByKeyRDD: RDD[(String, Int)] = rdd2.foldByKey(0)(func = (_: Int)+(_: Int))
    foldByKeyRDD.collect().foreach(println)  // (a,1) (b,7) (c,7)

    // combineByKey算子: 按照key分组,先在分区内聚合,再在分区间聚合,并统计聚合次数(累加器)
    // createCombiner: 创建key对应的累加器的初始值
    // mergeValue: 将key的累加器对应的当前值与新的值进行合并
    // mergeCombiners: 将各个分区之间同一个key的累加器对应的结果进行合并
    val combineByKeyRDD: RDD[(String, (Int, Int))] = rdd2.combineByKey((i: Int) => (i,1), (acc:(Int,Int), v: Int) => (acc._1 + v, acc._2 + 1), (acc1:(Int,Int), acc2:(Int,Int))=>(acc1._1 + acc2._1, acc1._2 + acc2._2))
    combineByKeyRDD.collect().foreach(println)  // (b,(7,2)) (a,(1,1)) (c,(7,2))
    val average: RDD[(String, Int)] = combineByKeyRDD.map((t: (String, (Int, Int))) => {(t._1, t._2._1/t._2._2)})
    average.collect().foreach(println)  // (b,3) (a,1) (c,3)
  }

  def action(sc: SparkContext): Unit = {
    // RDD的action操作: driver类、executor类
    // driver类: 返回值通常是driver端的内存变量,collect/count/foreach,远端executor计算完成后会回传数据给driver展示,数据太大可能OOM
    // collect算子: 返回包含RDD所有元素的数组,所有数据都会被加载到driver节点有可能导致OOM,建议使用take(n)
    // foreach算子: 遍历元素
    // foreachPartition算子: 遍历分区
    // first算子: 返回第一个元素,如果是文件则返回第一行
    // take算子: 返回前n个元素
    // takeOrdered算子: 返回排序后最小的几个元素
    // top算子: 返回排序后最大的几个元素
    // count算子: 统计元素个数
    // countByKey算子: 统计每个key的元素个数返回Map集合
    // reduce算子: 归约,化简
    val rdd1: RDD[Int] = sc.makeRDD(1 to 10, 2)
    val rdd2: RDD[(String, Int)] = sc.makeRDD(List(("a", 1), ("c", 3), ("b", 2), ("c", 4), ("b", 5)), numSlices = 2)
    val res1: Int = rdd1.reduce((x: Int, y: Int) => x + y)
    val res2: (String, Int) = rdd2.reduce((x: (String, Int), y: (String, Int)) => (x._1 + y._1, x._2 + y._2))
    // aggregate算子: 先在分区内对zeroValue和value聚合,再在分区间聚合
    // ClassTag: 表示反射,可以自动推断类型
    // zeroValue: 初始值,比如 0 或者 ""
    // seqOp: 在每个分区内用初始值逐步迭代value
    // combOp: 在分区间合并结果,分区间聚合也会加上初始值和aggregateByKey不同,所以结果是58不是57
    val i1: Int = rdd1.aggregate(1)(seqOp = (_: Int)+(_: Int), combOp = (_: Int)+(_: Int))
    println(i1)  // 58
    // fold算子: 是aggregate的简化,seqOp和combOp一样
    val i2: Int = rdd1.fold(1)(op = (_: Int)+(_: Int))
    println(i2)  // 58

    // executor类: 会在集群的每个executor节点就地执行输出到相应文件
    // saveAsTextFile(): 将元素以文本格式保存到文件,spark会对元素调用toString方法,output目录下.crc文件是验证,生成的文件是part-0000*
    // saveAsSequenceFile(): 将元素以Hadoop sequenceFile格式保存,只针对PairRDD
    // saveAsObjectFile(): 将元素序列化成对象保存到文件

    // 操作数据库
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
    rdd2.foreachPartition((iterator: Iterator[(String, Int)]) => {
      // 创建连接对象,有几个分区就创建几次连接,在executor端初始化连接对象避免网络传输导致的序列化问题
      val conn: Connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/test", "root", "root@123")
      // 遍历每个分区中的所有元素
      iterator.foreach{
        case (name, age) => // 执行sql
      }
    })
  }

  def fileRDD(sc: SparkContext): Unit = {
    // b.从本地或hdfs等文件系统创建RDD(常用),spark会为文件的每个block创建partition,所以分片数不能小于文件的分块数
    // local/standalone模式 -> file:///input/aaa.txt | yarn模式 -> hdfs://cdh1:9000/user/spark/input/aaa.txt
    val fileRDD: RDD[String] = sc.textFile("input/aaa.txt", minPartitions = 2)
    // 设置检查点保存目录,通常存hdfs有副本更安全
    //    sc.setCheckpointDir("hdfs://cdh1:9000/checkpoint")
    sc.setCheckpointDir("cp")
    // 扁平化
    val wordsRDD: RDD[String] = fileRDD.flatMap((line: String) => line.split(" "))
    // 结构转换
    val oneRDD: RDD[(String, Int)] = wordsRDD.map((word: String) => (word, 1))
    oneRDD.cache()
    // 查看RDD的lineage和依赖类型
    println(oneRDD.toDebugString)
    println(oneRDD.dependencies) // List(org.apache.spark.OneToOneDependency@2e3f79a2)
    // 分组聚合
    val sumRDD: RDD[(String, Int)] = oneRDD.reduceByKey((x: Int, y: Int) => x + y)
    // 在此处做检查点容错
    sumRDD.checkpoint()
    // action操作
    sumRDD.foreach(println)
    // 注意对比做检查点前后该RDD的lineage
    println(sumRDD.toDebugString)
  }
}

// 自定义累加器
class MyAccumulator extends AccumulatorV2[String, util.ArrayList[String]] {
  // 创建保存数据的集合
  private val list: util.ArrayList[String] = new util.ArrayList[String]()
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