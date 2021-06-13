package com.okccc.common

/**
 * 项目中用到的常量
 */
object Constants {

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
	val HBASE_DRIVER: String = "hbase.driver"
	val HBASE_URL: String = "hbase.url"

	// kafka
	val BOOTSTRAP_SERVERS: String = "bootstrap.servers"
	val NGINX_TOPIC: String = "nginx.topic"
	val MYSQL_TOPIC: String = "mysql.topic"
	val GROUP_ID: String = "group.id"
}
