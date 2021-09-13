package com.okccc.flink;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Random;

/**
 * Author: okccc
 * Date: 2021/9/1 下午2:35
 * Desc: WordCount案例、自定义数据源SourceFunction
 */
public class Flink01 {
    public static void main(String[] args) throws Exception {
        /*
         * 数据处理架构演变
         * OLTP - OLAP - LAMBDA - Flink(有状态的流处理,在本地维护状态并定期checkpoint到远端hdfs等文件系统)
         * OLTP：初期数据很少,mysql搞定一切
         * OLAP：数据越来越多,业务也越来越复杂,将mysql数据导入hive做统计分析,mysql只负责最基本的增删改查功能,解耦
         * LAMBDA：ss保证低延迟,hive保证结果准确(ss无法处理迟到数据),相同的计算逻辑要维护实时离线两套代码,冗余,所以流批一体的flink应运而生
         *
         * flink主要特点
         * 1.事件驱动：以事件的发生来驱动程序运行,通过event-log而不是rest调用进行通信,并将应用程序的数据保存为本地状态而不是写入外部数据库
         * flink是来一条数据就触发一次计算而ss是攒一批数据再计算,redis的多路io复用和nginx负载均衡底层都使用了linux内核的epoll(event-poll)
         * 2.基于流的世界观：flink认为一切都是由流组成,离线数据是有界流,实时数据是无界流,流批统一架构
         * 3.支持时间语义：EventTime + Watermark可以处理迟到和乱序数据保证结果准确,sparkStreaming没有时间语义概念,无法处理延迟数据
         * 4.支持精准消费：exactly-once的状态一致性保证
         * 5.分层api：顶层的table api和sql使用方便,中间层的DataStream是核心api,底层的ProcessFunction可以搞定一切
         *
         * 为什么flink比spark延迟低？
         * spark采用RDD数据结构,是离线数据集,所以spark是批处理,RDD将数据划分到不同分区进行计算,分区间由于数据量不一致等原因会存在速度差异,
         * 比如同一个Stage内部多个分区间的map算子有快有慢,必须要等当前Stage内的所有算子全部执行完才能继续下一个Stage,所以spark有秒级延迟
         * flink采用Integer/String/Long/POJO类等数据结构,是标准的流处理模式,map - keyBy - reduce一直往下运行不用等,所以flink没有延迟
         *
         * flink架构
         * JobManager：作业管理器,对应一个jvm进程,包括ResourceManager、Dispatcher和JobMaster三个线程
         * ResourceManager：管理TaskManager的静态资源slot(插槽)
         * Dispatcher：在web界面提交flink应用程序,并为提交的作业启动一个新的JobMaster,命令行提交不需要
         * JobMaster：负责单个JobGraph的执行,flink可以同时运行多个作业,每个作业都有自己的JobMaster
         * TaskManager：任务管理器,对应一个jvm进程,由task slot控制任务数,即并发执行能力,子任务可以共享slot,子任务就是程序中的各种算子
         *
         * 并行度
         * 算子的子任务subtask个数,流的并行度通常是所有算子的最大并行度,一个任务槽最多运行一个并行度,parallelism(动态) <= task slot(静态)
         * one-to-one：map/filter/flatMap基本转换算子,元素个数和顺序保持不变,相同并行度的one-to-one算子可以形成任务链,减少网络io
         * redistributing：keyBy键控流转换算子,基于hashCode按键分区,broadcast和rebalance随机重分区,类似spark的shuffle
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 配置参数优先级：算子并行度(代码写死) > 全局并行度(代码写死) > flink run -p(动态指定) > flink-conf.yaml(集群配置)
        // Source算子并行度设置为1可以保证数据有序
        // reduce这种聚合算子最好是能通过提交脚本-p动态扩展,所以代码一般不设置全局并行度,不然会覆盖动态指定,而具体的算子并行度则不会
        env.setParallelism(1);


