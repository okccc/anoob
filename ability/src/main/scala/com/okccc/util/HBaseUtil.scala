package com.okccc.util

import java.sql.{Connection, DriverManager, ResultSet, ResultSetMetaData, Statement}

import com.alibaba.fastjson.JSONObject

import scala.collection.mutable.ListBuffer

/**
 * Author: okccc
 * Date: 2021/4/21 上午11:15
 * Desc: 通过phoenix读写hbase数据的工具类
 */
object HBaseUtil {
  /**
   * Caused by: org.apache.hadoop.hbase.ipc.RemoteWithExtrasErubxception(org.apache.hadoop.hbase.DoNotRetryIOException): org.apache.hadoop.hbase.DoNotRetryIOException: Unable to load configured region split policy 'org.apache.phoenix.schema.MetaDataSplitPolicy' for table 'SYSTEM.CATALOG' Set hbase.table.sanity.checks to false at conf or table descriptor if you want to bypass sanity checks
   */

  // 很多问题...,还是用HBaseConfiguration吧
  def queryList(sql: String): List[JSONObject] ={
    val rsList:ListBuffer[JSONObject] = new ListBuffer[JSONObject]()
    //注册驱动
    Class.forName("org.apache.phoenix.jdbc.PhoenixDriver")
    //创建连接
    val conn: Connection = DriverManager.getConnection("jdbc:phoenix:10.18.3.20,10.18.3.21,10.18.3.22:2181")
    println(conn)
    //创建数据库操作对象
    val st: Statement = conn.createStatement()
    //执行SQL语句
    val rs: ResultSet = st.executeQuery(sql)
    val metaData: ResultSetMetaData = rs.getMetaData
    //处理结果集
    while(rs.next){
      val rowData: JSONObject = new JSONObject()
      //获取列名,获取列的数量，进行循环，从第一列开始
      for(i <- 1 to metaData.getColumnCount){
        rowData.put(metaData.getColumnName(i),rs.getString(i))
      }
      //将当前行的数据放到List集合中
      rsList.append(rowData)
    }
    //释放资源
    rs.close()
    st.close()
    conn.close()
    rsList.toList
  }

  def main(args: Array[String]): Unit = {
    val res: List[JSONObject] = queryList("select * from offset:jlgl_mysql_stream_knowledge_record_hive_offset")
    println(res)
  }

}
