package com.okccc.realtime.common;

/**
 * Author: okccc
 * Date: 2021/10/20 下午2:50
 * Desc: 实时数仓配置常量
 */
public class MyConfig {

    // mysql
    public static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    public static final String MYSQL_URL = "jdbc:mysql://localhost:3306/test?rewriteBatchedStatements=true&useServerPrepStmts=false";
    public static final String MYSQL_USER = "root";
    public static final String MYSQL_PASSWORD = "root@123";

    // hive
    // beeline命令行连接方式 beeline -u jdbc:hive2://${host}:10000 -n ${user} -p ${password}
    public static final String HIVE_DRIVER = "org.apache.hive.jdbc.HiveDriver";
    public static final String HIVE_URL = "jdbc:hive2://localhost:10000";
    public static final String HIVE_USER = "hive";
    public static final String HIVE_PASSWORD = "xxx";

    // hbase
    public static final String HBASE_SCHEMA = "realtime";
    public static final String PHOENIX_DRIVER = "org.apache.phoenix.jdbc.PhoenixDriver";
    public static final String PHOENIX_SERVER = "jdbc:phoenix:localhost:2181";

    // clickhouse
    public static final String CLICKHOUSE_DRIVER = "ru.yandex.clickhouse.ClickHouseDriver";
    public static final String CLICKHOUSE_URL = "jdbc:clickhouse://localhost/default";
    public static final String CLICKHOUSE_USER = "default";
    public static final String CLICKHOUSE_PASSWORD = "xxx";

    // redis
    public static final String REDIS_HOST = "localhost";
    public static final Integer REDIS_PORT = 6379;

    // hdfs
    public static final String HDFS_URL = "hdfs://localhost:8020";

    // es
    public static final String ES_SERVER = "http://localhost:9200";
}
