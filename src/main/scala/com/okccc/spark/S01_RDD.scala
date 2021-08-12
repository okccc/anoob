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
