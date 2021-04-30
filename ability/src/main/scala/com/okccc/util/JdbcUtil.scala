package com.okccc.util

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, ResultSetMetaData}
import java.util.Properties

import com.alibaba.fastjson.JSONObject

import scala.collection.mutable.ListBuffer

/**
 * Author: okccc
 * Date: 2021/4/19 上午9:44
 * Desc: 读写mysql的工具类
 */
object JdbcUtil {

  /**
   * 获取数据库连接
   */
  def getConnection: Connection = {
    // 1.加载配置文件
    val prop: Properties = PropertiesUtil.load("config.properties")

    // 2.获取连接信息
    val driver: String = prop.getProperty("driver")
    val url: String = prop.getProperty("url")
    val user: String = prop.getProperty("user")
    val password: String = prop.getProperty("password")

    // 3.通过反射加载驱动
    Class.forName(driver)

    // 4.建立连接
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
   * 查询多条记录
   */
  def queryList(sql: String): List[JSONObject] = {
    // 1.建立连接
    val conn: Connection = getConnection
//    println(conn)  // com.mysql.jdbc.JDBC4Connection@32a068d1
    // 2.预编译sql
    val ps: PreparedStatement = conn.prepareStatement(sql)
//    println(ps)  // com.mysql.jdbc.JDBC4PreparedStatement@5e25a92e: select * from user_info
    // 3.执行查询,返回结果集
    val rs: ResultSet = ps.executeQuery()
//    println(rs)  // com.mysql.jdbc.JDBC4ResultSet@b59d31

    // 4.解析结果集
    // 创建存放JSONObject的可变列表
    val resultList: ListBuffer[JSONObject] = new ListBuffer[JSONObject]()
    // 获取结果集的元数据信息
    val metaData: ResultSetMetaData = rs.getMetaData
//    println(metaData)  // com.mysql.jdbc.ResultSetMetaData@80ec1f8 - Field level information: ...
    // 从元数据获取列数
    val columns: Int = metaData.getColumnCount
    // 遍历结果集
    while (rs.next()) {
      // 将行数据封装成JSONObject
      val rowData: JSONObject = new JSONObject()
      for (i <- 1 to columns) {
        // 从元数据获取字段名称,从结果集获取字段值
        rowData.put(metaData.getColumnName(i), rs.getObject(i))
      }
      // 添加到列表
      resultList += rowData
    }

    // 5.关闭连接
    close(conn, ps, rs)
    // 6.返回结果集
    resultList.toList
  }

  def main(args: Array[String]): Unit = {
    val list: List[JSONObject] = queryList("select * from user_info")
    println(list)  // {"gender":"F","name":"毕琴蕊","phone_num":"13755344362","id":1,"email":"do3zo23@gmail.com"},{...}
  }
}
