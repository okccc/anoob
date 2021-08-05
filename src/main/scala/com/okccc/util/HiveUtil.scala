package com.okccc.util

import java.util

import com.okccc.realtime.common.Configs
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{DataTypes, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
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
    val table: String = hiveTable.split("\\.")(1)
    // 分区数过多会产生大量小文件,写入hdfs之前先合并分区只输出到一个文件,但是会增加批处理时间,视具体数据量而定
    df.repartition(1).createOrReplaceTempView(table + "_tmp")
    // 将临时表数据插入正式表
    try {
      spark.sql("insert into table " + hiveTable + " partition(dt) select * from " + table + "_tmp")
    } catch {
      case e: Exception => e.printStackTrace()
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
//    val columns: String = Configs.get(Configs.ORDERS_HIVE_COLUMNS)
//    println(createSchema(columns))
//
//    val row: Row = Row("aa", "bb", "cc")
//    println(row)
//    println(row.getString(1))

    val spark: SparkSession = SparkSession
      .builder()
      .master("local[*]")
      .appName("mysql2hive")
      .enableHiveSupport()
      .config("spark.debug.maxToStringFields", 200)
      .config("hive.exec.dynamic.partition", "true")
      .config("hive.exec.dynamic.partition.mode", "nonstrict")
      .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()
    val columns: String = Configs.get("test")
    val rows: util.List[Row] = new util.ArrayList[Row]()
    rows.add(Row("aa","bb","cc"))
    rows.add(Row("dd","ee","ff"))
    val df: DataFrame = spark.createDataFrame(rows, createSchema(columns))
    df.show()
  }
}