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
         * OLTP - OLAP - LAMBDA - Flink(有状态的流处理,在本地维护状态并定期存储到远端hdfs等文件系统)
         * OLTP：初期数据很少,mysql搞定一切
         * OLAP：数据越来越多,业务也越来越复杂,将mysql数据导入hive做统计分析,mysql只负责最基本的增删改查功能,解耦
         * LAMBDA：ss保证低延迟,hive保证结果准确(ss无法处理迟到数据),相同的计算逻辑要维护实时离线两套代码,冗余,所以流批一体的flink应运而生

