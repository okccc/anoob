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
