package com.okccc.util

import java.util

import com.alibaba.fastjson.JSONObject

import scala.collection.mutable

/**
 * Author: okccc
 * Date: 2021/5/26 上午10:43
 * Desc: 字符串工具类
 */
object StringUtil {

  // 将main方法传入的字符串参数解析成键值对,类似hive函数str_to_map
  def strToMap(args: String): util.HashMap[String, String] = {
    // orc=grubby&ne=moon&hum=sky&ud=ted
    val hashMap: util.HashMap[String, String] = new util.HashMap[String, String]()
    for (elem <- args.split("&")) {
      val arr: Array[String] = elem.split("=")
      hashMap.put(arr(0), arr(1))
    }
    hashMap
  }

  // 对小数四舍五入格式化
  def formatDouble(num: Double, scale: Int): Double = {
    val bd: BigDecimal = BigDecimal(num)
    bd.setScale(scale, BigDecimal.RoundingMode.HALF_UP).doubleValue()
  }

  // 从json对象中提取参数
  def getParam(jsonObject: JSONObject, field: String): String = {
    jsonObject.getString(field)
  }

  // 截断字符串两侧逗号
  def trimComma(str: String): String = {
    var result: String = ""
    if (str.startsWith(",")) {
      result = str.substring(1)
    }
    if (str.endsWith(",")) {
      result = str.substring(0, str.length() - 1)
    }
    result
  }

  // 从拼接的字符串中提取字段值
  def getFieldFromConcatString(str: String, delimiter: String, field: String): String = {
    try {
      val fields: Array[String] = str.split(delimiter)
      for (i <- fields) {
        if (i.split("=").length == 2) {
          val key: String = i.split("=")(0)
          val value: String = i.split("=")(1)
          if (key.equals(field)) {
            return value
          }
        }
      }
    } catch {
      case e: Exception => e.printStackTrace()
    }
    null
  }

  // 从拼接的字符串中给字段设置值
  def setFieldInConcatString(str: String, delimiter: String, field: String, newFieldValue: String): String = {
    val hm: mutable.HashMap[String, String] = new mutable.HashMap[String, String]()
    val fields: Array[String] = str.split(delimiter)
    for (i <- fields){
      val arr: Array[String] = i.split("=")
      if (arr(0).compareTo(field) == 0)
        hm += (field -> newFieldValue)
      else
        hm += (arr(0) -> arr(1))
    }
    hm.map((item: (String, String)) => item._1 + "=" + item._2).mkString(delimiter)
  }


  def main(args: Array[String]): Unit = {
    val map: util.HashMap[String, String] = strToMap("jobName=userLabel&envType=online&topic=thrall&groupId=g01&parallelism=6")
    println(map)  // {jobName=userLabel, envType=online, groupId=g01, parallelism=6, topic=thrall}
    println(map.get("topic"))  // thrall
  }
}
