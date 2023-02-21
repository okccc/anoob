package com.okccc.util

import net.ipip.ipdb.City

/**
 * @Author: okccc
 * @Date: 2021/7/7 下午3:36
 * @Desc: 解析IP地址
 */
object IPUtil {

  // 简单解析IP地址

  def find(ip: String): Array[String] = {
    try {
      val city = new City(ClassLoader.getSystemResource("ipipfree.ipdb").getPath)
      return city.find(ip, "CN")
    } catch {
      case e: Exception =>
        e.printStackTrace()
    }
    null
  }


  def main(args: Array[String]): Unit = {
    val arr: Array[String] = find("49.83.75.64")
    println(arr.mkString(",")) // 中国,江苏,盐城
    println(arr(1)) // 江苏
  }
}