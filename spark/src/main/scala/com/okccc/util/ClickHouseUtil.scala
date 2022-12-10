package com.okccc.util

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, ResultSetMetaData}
import java.util.Properties

import com.alibaba.fastjson.{JSON, JSONObject}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer

/**
 * @Author: okccc
 * @Date: 2021/6/3 下午4:31
 * @Desc: 读写ClickHouse的工具类,类似mysql
 */
object ClickHouseUtil {

  private val logger: Logger = LoggerFactory.getLogger(ClickHouseUtil.getClass)

  /**
   * 获取数据库连接
   */
  def getConnection: Connection = {
    // 1.加载配置文件
    val prop: Properties = PropertiesUtil.load("config.properties")
    // 2.获取连接信息
    val driver: String = prop.getProperty("ck.driver")
    val url: String = prop.getProperty("ck.url")
    val user: String = prop.getProperty("ck.user")
    val password: String = prop.getProperty("ck.password")
    // 3.通过反射加载驱动
    Class.forName(driver)
    // 4.获取连接
    val connection: Connection = DriverManager.getConnection(url, user, password)
    connection
  }

  /**
   * 关闭数据库连接
   */
  def close(conn: Connection, ps: PreparedStatement, rs: ResultSet): Unit = {
    if (conn != null) conn.close()
    if (ps != null) ps.close()
    if (rs != null) rs.close()
  }

  /**
   * 查询
   */
  def queryList(sql: String): List[JSONObject] = {
    var conn: Connection = null
    var ps: PreparedStatement = null
    var rs: ResultSet = null
    val listBuffer: ListBuffer[JSONObject] = new ListBuffer[JSONObject]
    try {
      // 1.建立连接
      conn = getConnection
      // 2.预编译sql
      ps = conn.prepareStatement(sql)
      // 3.执行查询,返回结果集
      rs = ps.executeQuery()
      // 4.解析结果集
      // 获取结果集的元数据信息
      val metaData: ResultSetMetaData = rs.getMetaData
      // 从元数据获取列数
      val count: Int = metaData.getColumnCount
      while (rs.next()) {
        // 将数据封装成JSONObject
        val rowData: JSONObject = new JSONObject()
        for (i <- 1 to count) {
          // 从元数据获取字段名称,从结果集获取字段值
          rowData.put(metaData.getColumnName(i), rs.getObject(i))
        }
        // 添加到列表
        listBuffer.append(rowData)
      }
      // 5.返回查询结果
      return listBuffer.toList
    } catch {
      case e: Exception => logger.error("get connection failed ", e)
    } finally {
      // 6.关闭连接
      close(conn, ps, rs)
    }
    null
  }


  def main(args: Array[String]): Unit = {
    val list: List[JSONObject] = queryList("select * from events")
    println(list)
  }
}