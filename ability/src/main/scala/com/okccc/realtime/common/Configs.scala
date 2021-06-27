package com.okccc.realtime.common

import java.util.Properties

/**
 * Author: okccc
 * Date: 2021/6/15 上午9:37
 * Desc: 项目中用到的配置常量
 */
object Configs {

  // jdbc
  val JDBC_DRIVER: String = "jdbc.driver"
  val JDBC_URL: String = "jdbc.url"
  val JDBC_USER: String = "jdbc.user"
  val JDBC_PASSWORD: String = "jdbc.password"

  // clickHouse
  val CK_DRIVER: String = "ck.driver"
  val CK_URL: String = "ck.url"
  val CK_USER: String = "ck.user"
  val CK_PASSWORD: String = "ck.password"

  // redis
  val REDIS_HOST: String = "redis.host"
  val REDIS_PORT: String = "redis.port"

  // es
  val ES_SERVER: String = "es.server"

  // hbase
//  val HBASE_DRIVER: String = "hbase.driver"
//  val HBASE_URL: String = "hbase.url"

  // kafka
  val BOOTSTRAP_SERVERS: String = "bootstrap.servers"
  val NGINX_TOPICS: String = "nginx.topics"
  val MYSQL_TOPICS: String = "mysql.topics"
  val GROUP_ID: String = "group.id"
  val RESTART: String = "restart"
  val ACK: String = "ack"
  val IDEMPOTENCE: String = "idempotence"
  val ENABLE_AUTO_COMMIT: String = "enable.auto.commit"
  val AUTO_OFFSET_RESET: String = "auto.offset.reset"

  // hive
  val HIVE_TABLE: String = "nginx.hive.table"
  val HIVE_COLUMNS: String = "nginx.hive.columns"

  // 加载配置文件
  private val prop: Properties = new Properties()
  prop.load(ClassLoader.getSystemClassLoader.getResourceAsStream("config.properties"))
  // 获取属性值
  def get(key: String): String = {
    prop.getProperty(key)
  }

}
