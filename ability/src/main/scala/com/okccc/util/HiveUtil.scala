package com.okccc.util

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{DataTypes, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}

import java.util

/**
 * Author: okccc
 * Date: 2021/1/29 4:55 下午
 * Desc: spark读写hive的工具类
 */
object HiveUtil {

  /**
   * hive根据列创建表
   */
  def createSchema(columns: String): StructType = {
    // 获取所有列
    val colArray: Array[String] = columns.split(",")
    // 存放结构字段的列表
    val fields: util.ArrayList[StructField] = new util.ArrayList[StructField]()
    // 遍历所有列
    colArray.foreach((col: String) => {
      // 创建结构字段
      val field: StructField = DataTypes.createStructField(col, DataTypes.StringType, true)
      fields.add(field)
    })
    // 创建结构类型
    DataTypes.createStructType(fields)
  }

  /**
   * spark写数据到hive
   */
  def write(rowRdd: RDD[Row], spark: SparkSession, hiveTable: String, hiveColumns: String): Unit = {
    // 根据列创建表结构
    val schema: StructType = createSchema(hiveColumns)
    // 将RDD转换成DataFrame
    val df: DataFrame = spark.createDataFrame(rowRdd, schema)
    // 生成临时表
    df.repartition(1).createOrReplaceTempView(hiveTable + "_tmp")
    // 将临时表数据插入正式表
    val sqlStr: String = "insert into table " + hiveTable + " partition(dt) select * from " + hiveTable + "_tmp"
    // 执行sparkSql
    spark.sql(sqlStr)
  }

}
