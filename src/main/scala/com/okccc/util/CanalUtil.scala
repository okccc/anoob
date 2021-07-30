package com.okccc.util

import java.util

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import com.okccc.realtime.common.Configs

import scala.collection.mutable.ArrayBuffer

/**
 * Author: okccc
 * Date: 2021/7/21 下午6:28
 * Desc: 解析canal数据的工具类
 */
object CanalUtil {

  def main(args: Array[String]): Unit = {
    val str: String = "tmp.orders"
    // 注意：split切割字符串 "," ":" "@" "#"不需要转义, "." "|" "$" "*"是需要转义的,多个分隔符可以用"|"隔开,但是该转义的还得转义
    val arr: Array[String] = str.split("\\.")
    for (elem <- arr) {
      println(elem)
    }
  }
}
