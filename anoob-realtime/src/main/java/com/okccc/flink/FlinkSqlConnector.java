package com.okccc.flink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2021/9/20 下午12:47
 * @Desc: Flink Table API Connectors
 */
@SuppressWarnings("unused")
public class FlinkSqlConnector {

    /**
     * DataGen SQL Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/datagen/
     * The DataGen connector allows for creating tables based on in-memory data generation.
     */
    private static void getDataGenConnector(StreamTableEnvironment tableEnv) {
        // 读DataGen
        tableEnv.executeSql(
                "CREATE TABLE orders (\n" +
                        "    id             String,\n" +
                        "    price          DECIMAL(10, 2),\n" +
                        "    create_time    TIMESTAMP_LTZ(3)\n" +
                        ") WITH (\n" +
                        "  'connector' = 'datagen',\n" +
                        "  'number-of-rows' = '10'" +
                        ")"
        );
        // 查询测试
        Table table = tableEnv.sqlQuery("SELECT * FROM orders");
        table.execute().print();
        // 查看表结构
        table.printSchema();
        // 查看执行计划
        System.out.println(table.explain());
    }

    /**
     * FileSystem SQL Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/filesystem/
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/table/concepts/time_attributes/
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/table/sql/queries/window-agg/
     */
    private static void getFileSystemConnector(StreamTableEnvironment tableEnv) {
        // 读文件
        tableEnv.executeSql(
                "CREATE TABLE user_behavior (\n" +
                        "    user_id        STRING,\n" +
                        "    item_id        STRING,\n" +
                        "    category_id    STRING,\n" +
                        "    behavior       STRING,\n" +
                        "    ts             BIGINT,\n" +
                        // ProcessingTime可以直接调用PROCTIME()函数,返回TIMESTAMP_LTZ类型
//                        "    ts_ltz         AS PROCTIME()\n" +
                        // EventTime通常是将表中某个时间列转换成TIMESTAMP/TIMESTAMP_LTZ类型,并且要指定水位线策略
                        "    ts_ltz         AS TO_TIMESTAMP_LTZ(ts, 3),\n" +
                        "    WATERMARK FOR ts_ltz AS ts_ltz - INTERVAL '3' SECOND\n" +
                        ") WITH (\n" +
                        "  'connector' = 'filesystem',\n" +
                        "  'path' = '/Users/okc/projects/anoob/anoob-realtime/input/UserBehavior.csv',\n" +
                        "  'format' = 'csv'\n" +
                        ")"
        );
        // 先按商品分组开窗聚合
        String sql01 = "SELECT item_id,window_start,window_end,count(*) cnt\n" +
                "FROM TABLE(\n" +
                "    HOP(TABLE user_behavior, DESCRIPTOR(ts_ltz), INTERVAL '5' MINUTES, INTERVAL '1' HOUR))\n" +
                "GROUP BY item_id,window_start,window_end";
        // 再按窗口分组排序
        String sql02 = "SELECT *,ROW_NUMBER() OVER(PARTITION BY window_start,window_end ORDER BY cnt DESC) AS rn FROM (" + sql01 + ")";
        // 取topN
        String sql03 = "SELECT * FROM (" + sql02 + ") WHERE rn <= 3";
        // 查询结果
        tableEnv.sqlQuery(sql03).execute().print();

        // 写文件
        tableEnv.executeSql(
                "CREATE TABLE sink_fs (\n" +
                        "    user_id        STRING,\n" +
                        "    item_id        STRING,\n" +
                        "    category_id    STRING,\n" +
                        "    behavior       STRING,\n" +
                        "    ts             BIGINT,\n" +
                        "    dt             STRING\n" +  // dt是分区字段
                        ") PARTITIONED BY (dt) WITH (\n" +
                        "  'connector' = 'filesystem',\n" +
                        "  'path' = '/Users/okc/projects/anoob/anoob-realtime/output',\n" +
                        "  'format' = 'csv'\n" +
                        ")"
        );
        tableEnv.executeSql("INSERT INTO sink_fs SELECT user_id,item_id,category_id,behavior,ts,DATE_FORMAT(ts_ltz, 'yyyyMMdd') FROM user_behavior");
    }

    /**
     * JDBC SQL Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/jdbc/
     * The JDBC connector allows for reading data from and writing data into any relational databases with a JDBC driver.
     * The connector operates in upsert mode if the primary key was defined, otherwise, the connector operates in append mode.
     */
    private static void getJdbcConnector(StreamTableEnvironment tableEnv) {
        // 读mysql
        tableEnv.executeSql(
                "CREATE TABLE base_dic (\n" +
                        "    dic_code        STRING,\n" +
                        "    dic_name        STRING,\n" +
                        "    parent_code     STRING,\n" +
                        "    create_time     STRING,\n" +
                        "    operate_time    STRING,\n" +
                        "PRIMARY KEY (dic_code) NOT ENFORCED" +
                        ") WITH (\n" +
                        "  'connector' = 'jdbc',\n" +
                        "  'driver' = 'com.mysql.cj.jdbc.Driver',\n" +
                        "  'url' = 'jdbc:mysql://localhost:3306/mock',\n" +
                        "  'table-name' = 'base_dic',\n" +
                        "  'username' = 'root',\n" +
                        "  'password' = 'root@123',\n" +
                        // By default, lookup cache is not enabled. You can enable it by setting lookup.cache to PARTIAL.
                        "  'lookup.cache' = 'PARTIAL',\n" +
                        "  'lookup.partial-cache.max-rows' = '100',\n" +
                        "  'lookup.partial-cache.expire-after-write' = '10 min',\n" +
                        "  'lookup.partial-cache.cache-missing-key' = 'false'\n" +
                        ")"
        );
        // 维度表数据是一次性查出来的,可以用sqlQuery()打印测试
        tableEnv.sqlQuery("SELECT * FROM base_dic").execute().print();
    }

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境,env执行DataStream相关操作
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建表环境,tableEnv执行Table相关操作
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // flink-sql调优
        Configuration conf = tableEnv.getConfig().getConfiguration();
        // 1.设置空闲状态保留时间：join操作的左右表数据、distinct操作的重复数据都会一直存在状态里,需要定时清除
        tableEnv.getConfig().setIdleStateRetention(Duration.ofHours(1));
        conf.setString("table.exec.state.ttl", "1 h");
        // 2.开启微批处理：缓存一定数据再触发处理,减少对state的访问,通过增加延迟提高吞吐量并减少数据输出量,聚合场景下能显著提升性能
        conf.setString("table.exec.mini-batch.enabled", "true");
        // 批量输出的间隔时间
        conf.setString("table.exec.mini-batch.allow-latency", "5 s");
        // 防止OOM设置每个批次最多缓存的数据条数
        conf.setString("table.exec.mini-batch.size", "20000");
        // 3.开启LocalGlobal：两阶段聚合解决数据倾斜问题,针对SUM/COUNT/MAX/MIN/AVG等普通聚合
        conf.setString("table.optimizer.agg-phase-strategy", "TWO_PHASE");
        // 4.开启Split Distinct：针对COUNT DISTINCT
        conf.setString("table.optimizer.distinct-agg.split.enabled", "true");
        // 第一层打散的bucket数目
        conf.setString("table.optimizer.distinct-agg.split.bucket-num", "1024");
        // 5.指定时区
        conf.setString("table.local-time-zone", "Asia/Shanghai");

//        getDataGenConnector(tableEnv);
//        getFileSystemConnector(tableEnv);
        getJdbcConnector(tableEnv);
    }
}
