package com.okccc.util

import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Properties

/**
 * Author: okccc
 * Date: 2020/12/12 17:16
 * Desc: 读取配置文件的工具类
 */
object PropertiesUtil {

  def load(filename: String): Properties = {
    // 创建Properties对象
    val prop: Properties = new Properties()
    // 加载resources配置文件
    prop.load(Thread.currentThread().getContextClassLoader.getResourceAsStream(filename))
    prop
  }

  def main(args: Array[String]): Unit = {
    val prop: Properties = PropertiesUtil.load("config.properties")
    val value: String = prop.getProperty("bootstrap.servers")
    println(value)
  }

}
