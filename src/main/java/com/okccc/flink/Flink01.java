package com.okccc.flink;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

/**
 * Author: okccc
 * Date: 2021/9/1 下午2:35
 * Desc: WordCount
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

