package com.okccc.util

import com.alibaba.fastjson.JSONObject

import scala.collection.mutable

/**
 * Author: okccc
 * Date: 2021/5/26 上午10:43
 * Desc: 
 */
object StringUtil {

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

  /**
   * 校验数据中的指定字段,是否在指定范围内
   * @param data 数据
   * @param dataField 数据字段
   * @param parameter 参数
   * @param startParamField 起始参数字段
   * @param endParamField 结束参数字段
   * @return 校验结果
   */
  def between(data: String, dataField: String, parameter: String, startParamField: String, endParamField: String): Boolean = {

    val startParamFieldStr: String = getFieldFromConcatString(parameter, "\\|", startParamField)
    val endParamFieldStr: String = getFieldFromConcatString(parameter, "\\|", endParamField)
    if (startParamFieldStr == null || endParamFieldStr == null) {
      return true
    }

    val startParamFieldValue: Int = startParamFieldStr.toInt
    val endParamFieldValue: Int = endParamFieldStr.toInt

    val dataFieldStr: String = getFieldFromConcatString(data, "\\|", dataField)
    if (dataFieldStr != null) {
      val dataFieldValue: Int = dataFieldStr.toInt
      if (dataFieldValue >= startParamFieldValue && dataFieldValue <= endParamFieldValue) {
        return true
      } else {
        return false
      }
    }
    false
  }

  /**
   * 校验数据中的指定字段,是否有值与参数字段的值相同
   * @param data 数据
   * @param dataField 数据字段
   * @param parameter 参数
   * @param paramField 参数字段
   * @return 校验结果
   */
  def in(data: String, dataField: String, parameter: String, paramField: String): Boolean = {
    val paramFieldValue: String = getFieldFromConcatString(parameter, "\\|", paramField)
    if (paramFieldValue == null) {
      return true
    }
    val paramFieldValueSplited: Array[String] = paramFieldValue.split(",")

    val dataFieldValue: String = getFieldFromConcatString(data, "\\|", dataField)
    if (dataFieldValue != null && dataFieldValue != "-1") {
      val dataFieldValueSplited: Array[String] = dataFieldValue.split(",")
      for (singleDataFieldValue <- dataFieldValueSplited) {
        for(singleParamFieldValue <- paramFieldValueSplited) {
          if(singleDataFieldValue.compareTo(singleParamFieldValue) ==0) {
            return true
          }
        }
      }
    }
    false
  }

  /**
   * 校验数据中的指定字段,是否在指定范围内
   * @param data 数据
   * @param dataField 数据字段
   * @param parameter 参数
   * @param paramField 参数字段
   * @return 校验结果
   */
  def equal(data: String, dataField: String, parameter: String, paramField: String): Boolean = {
    val paramFieldValue: String = getFieldFromConcatString(parameter, "\\|", paramField)
    if (paramFieldValue == null) {
      return true
    }

    val dataFieldValue: String = getFieldFromConcatString(data, "\\|", dataField)
    if (dataFieldValue != null) {
      if (dataFieldValue.compareTo(paramFieldValue) == 0) {
        return true
      }
    }
    false
  }
}
