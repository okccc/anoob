package com.okccc.realtime.common;

/**
 * Author: okccc
 * Date: 2021/10/20 下午2:50
 * Desc: 实时数仓配置常量
 */
public class MyConfig {

    // hbase
    public static final String HBASE_SCHEMA = "realtime";
    public static final String PHOENIX_DRIVER = "org.apache.phoenix.jdbc.PhoenixDriver";
    public static final String PHOENIX_SERVER = "jdbc:phoenix:localhost:2181";

    // redis
    public static final String REDIS_HOST = "localhost";
    public static final Integer REDIS_PORT = 6379;

    // hdfs
    public static String HDFS_URL = "hdfs://dev-bigdata-cdh1:8020";

    // es
    public static String ES_SERVER = "http://localhost:9200";
}
