package com.okccc.flink.tableapi

import java.util.Properties

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.table.api.EnvironmentSettings
import org.apache.flink.table.api.bridge.scala.StreamTableEnvironment



/**
 * Author: okccc
 * Date: 2021/4/28 下午6:20
 * Desc: 
 */
object FlinkSql {
  def main(args: Array[String]): Unit = {
    // 1.创建表执行环境
    // 创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 流处理环境设置
    val bsSettings: EnvironmentSettings = EnvironmentSettings.newInstance().useBlinkPlanner().inStreamingMode().build()
    // 批处理环境设置
    val bbSettings: EnvironmentSettings = EnvironmentSettings.newInstance().useBlinkPlanner().inBatchMode().build()
    // 创建表执行环境,指定批处理或流处理
    val tableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env, bsSettings)

    // 2.Source操作
    // 读取kafka数据
//    val prop: Properties = new Properties()
//    prop.put("bootstrap.servers", "localhost:9092")
//    prop.put("group.id", "consumer-group")
//    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))
//    // 基于输入流创建表
//    val table: Table = tableEnv.fromDataStream(inputStream)

    // 3.Transform操作
    // 调用table api
//    table.printSchema()
    // 直接使用sql查询

    // 4.Sink操作

  }
}
