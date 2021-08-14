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
