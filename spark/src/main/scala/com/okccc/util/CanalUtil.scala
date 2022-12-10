package com.okccc.util

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import com.okccc.realtime.common.Configs
import org.apache.spark.sql.Row

import java.util
import scala.collection.mutable.ArrayBuffer

/**
 * @Author: okccc
 * @Date: 2021/7/21 下午6:28
 * @Desc: 解析canal数据的工具类
 */
object CanalUtil {

  def parseJSONObjectToArrayBuffer(jsonObj: JSONObject, hiveTable: String): ArrayBuffer[ArrayBuffer[String]] = {
    // 存放所有记录的可变数组
    val records: ArrayBuffer[ArrayBuffer[String]] = new ArrayBuffer[ArrayBuffer[String]]()
    // 获取表名/操作记录/操作类型/操作时间
    val db: String = jsonObj.getString("database")
    val table: String = jsonObj.getString("table")
    val data: JSONArray = jsonObj.getJSONArray("data")
    val operation: String = jsonObj.getString("type")
    val ts: String = jsonObj.getString("ts")
    val dt: String = DateUtil.parseUnixToDate(ts.toLong).replace("-", "")
    // 判断当前表是否是目标表
    if (table == hiveTable.split("\\.")(1)) {
      // 解析json数组
      for (i <- 0 until data.size()) {
        // json数组包含多个小json串
        val jsonObj: JSONObject = JSON.parseObject(data.get(i).toString)
        // 存放当前记录的字段值
        val record: ArrayBuffer[String] = new ArrayBuffer[String]()
        // 获取该表所有字段
        val columns: String = Configs.get(table + ".hive.columns")
        val arr: Array[String] = columns.split(",")
        for (column <- util.Arrays.copyOf(arr, arr.length - 5)) {
          // 获取每个字段对应的值
          record.append(jsonObj.getOrDefault(column, "").toString)
        }
        record.append(db, table, operation, ts, dt)
        records.append(record)
      }
    }
    records
  }

  def parseJSONObjectToList(jsonObj: JSONObject): util.List[Row] = {
    // 存放所有记录的可变数组
    //    val records: ArrayBuffer[ArrayBuffer[String]] = new ArrayBuffer[ArrayBuffer[String]]()
    val rows: util.List[Row] = new util.ArrayList[Row]()
    // 获取表名/操作记录/操作类型/操作时间
    val db: String = jsonObj.getString("database")
    val table: String = jsonObj.getString("table")
    val data: JSONArray = jsonObj.getJSONArray("data")
    val operation: String = jsonObj.getString("type")
    val ts: String = jsonObj.getString("ts")
    val dt: String = DateUtil.parseUnixToDate(ts.toLong).replace("-", "")
    // 判断当前表是否是目标表
    // 解析json数组
    for (i <- 0 until data.size()) {
      // json数组包含多个小json串
      val jsonObj: JSONObject = JSON.parseObject(data.get(i).toString)
      // 存放当前记录的字段值
      val record: ArrayBuffer[String] = new ArrayBuffer[String]()
      // 获取该表所有字段
      val columns: String = Configs.get(table + ".hive.columns")
      val arr: Array[String] = columns.split(",")
      for (column <- util.Arrays.copyOf(arr, arr.length - 5)) {
        // 获取每个字段对应的值
        record.append(jsonObj.getOrDefault(column, "").toString)
      }
      record.append(db, table, operation, ts, dt)
      rows.add(Row.fromSeq(record))
    }
    rows
  }


  def main(args: Array[String]): Unit = {
    val str: String = "tmp.orders"
    // 注意：split切割字符串 "," ":" "@" "#"不需要转义, "." "|" "$" "*"是需要转义的,多个分隔符可以用"|"隔开,但是该转义的还得转义
    val arr: Array[String] = str.split("\\.")
    println(arr(1))
  }
}
