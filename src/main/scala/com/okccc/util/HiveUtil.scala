package com.okccc.util

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{DataTypes, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import java.util

import com.okccc.realtime.common.Configs
import org.slf4j.{Logger, LoggerFactory}

/**
 * Author: okccc
 * Date: 2021/1/29 4:55 下午
 * Desc: spark读写hive的工具类
 */
object HiveUtil {

  private val logger: Logger = LoggerFactory.getLogger(HiveUtil.getClass)

  /**
   * spark写数据到hive
   */
  def write(rowRdd: RDD[Row], spark: SparkSession, hiveTable: String, hiveColumns: String): Unit = {
    // 根据列创建表结构
    val schema: StructType = createSchema(hiveColumns)
    // 根据RDD和对应的StructType创建DataFrame
    val df: DataFrame = spark.createDataFrame(rowRdd, schema)
    // 生成临时表
    df.repartition(1).createOrReplaceTempView("RealTimeEvent")
    // 将临时表数据插入正式表
    try {
      spark.sql("insert into table " + hiveTable + " partition(dt) select * from RealTimeEvent")
    } catch {
      case e: Exception => logger.error("error", e)
        df.show()
    }
  }

  /**
   * hive根据列创建表结构
   */
  def createSchema(columns: String): StructType = {
    // 存放结构字段的列表
    val structFields: util.ArrayList[StructField] = new util.ArrayList[StructField]()
    // 遍历所有列
    for (column <- columns.split(",")) {
      // 创建结构字段
      val structField: StructField = DataTypes.createStructField(column, DataTypes.StringType, true)
      // 添加到列表
      structFields.add(structField)
    }
    // 创建结构类型
    DataTypes.createStructType(structFields)
  }


  def main(args: Array[String]): Unit = {
    val columns: String = Configs.get(Configs.NGINX_HIVE_COLUMNS)
    println(createSchema(columns))

    val row: Row = Row("aa", "bb", "cc")
    println(row.getString(1))
  }
}