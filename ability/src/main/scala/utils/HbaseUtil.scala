package utils

import java.sql.{Connection, DriverManager}
import java.util.Properties

import com.alibaba.fastjson.JSONObject

/**
 * Author: okccc
 * Date: 2021/4/21 上午11:15
 * Desc: 通过phoenix读写hbase数据的工具类
 */
object HbaseUtil {

  /**
   * 获取数据库连接
   */
  def getConnection: Connection = {
    // 1.加载配置文件
    val prop: Properties = PropertiesUtil.load("config.properties")

    // 2.获取连接信息
    val driver: String = prop.getProperty("hbase_driver")
    val url: String = prop.getProperty("hbase_url")

    // 3.通过反射加载驱动
    Class.forName(driver)

    // 4.建立连接
    val connection: Connection = DriverManager.getConnection(url)
    connection
  }

  /**
   * 查询多条记录
   */
//  def queryList(sql: String): List[JSONObject] = {
//
//  }

  def main(args: Array[String]): Unit = {
    println(getConnection)
  }
}
