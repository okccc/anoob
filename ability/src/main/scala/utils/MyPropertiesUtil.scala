package utils

import java.io.{File, FileReader, InputStreamReader}
import java.nio.charset.StandardCharsets
import java.util.Properties

/**
 * @author okccc
 * @date 2020/12/12 17:16
 * @desc 读取配置文件的工具类
 */
object MyPropertiesUtil {

  def load(filename: String): Properties = {
    // 创建Properties对象
    val prop: Properties = new Properties()
    // 读取配置文件
    prop.load(new InputStreamReader(
      Thread.currentThread().getContextClassLoader.getResourceAsStream(filename), StandardCharsets.UTF_8
    ))
    prop
  }

  def main(args: Array[String]): Unit = {
    val prop: Properties = MyPropertiesUtil.load("config.properties")
    val value: String = prop.getProperty("kafka.broker.list")
    println(value)
  }

}
