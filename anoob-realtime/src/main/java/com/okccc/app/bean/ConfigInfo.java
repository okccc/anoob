package com.okccc.app.bean;

/**
 * @Author: okccc
 * @Date: 2023/3/6 15:20:37
 * @Desc: 实时数仓配置信息
 */
public class ConfigInfo {

    // mysql
    public static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    public static final String MYSQL_URL = "jdbc:mysql://localhost:3306/mock?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8&allowMultiQueries=true";
    public static final String MYSQL_USER = "root";
    public static final String MYSQL_PASSWORD = "root@123";

    // hive
    // beeline命令行连接方式 beeline -u jdbc:hive2://${host}:10000 -n ${user} -p ${password}
    public static final String HIVE_DRIVER = "org.apache.hive.jdbc.HiveDriver";
    public static final String HIVE_URL = "jdbc:hive2://{ip}:{port}";
    public static final String HIVE_USER = "hive";
    public static final String HIVE_PASSWORD = "hive@123";

    // presto
    public static final String PRESTO_DRIVER = "com.facebook.presto.jdbc.PrestoDriver";
    public static final String PRESTO_URL = "jdbc:presto://{ip}:9000/hive";
    public static final String PRESTO_USER = "presto";
    public static final String PRESTO_PASSWORD = null;

    // hbase
    public static final String PHOENIX_DRIVER = "org.apache.phoenix.jdbc.PhoenixDriver";
    public static final String PHOENIX_SERVER = "jdbc:phoenix:localhost:2181";
    public static final String HBASE_SCHEMA = "dim";

    // clickhouse
    public static final String CLICKHOUSE_DRIVER = "ru.yandex.clickhouse.ClickHouseDriver";
    public static final String CLICKHOUSE_URL = "jdbc:clickhouse://localhost:8123";
    public static final String CLICKHOUSE_USER = "default";
    public static final String CLICKHOUSE_PASSWORD = null;
}
