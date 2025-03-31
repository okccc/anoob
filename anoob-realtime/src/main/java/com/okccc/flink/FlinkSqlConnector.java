package com.okccc.flink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * @Author: okccc
 * @Date: 2021/9/20 15:21:47
 * @Desc: Flink Table API Connectors
 */
public class FlinkSqlConnector {

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境,env执行DataStream相关操作
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建表环境,tableEnv执行Table相关操作
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // flink-sql调优
        Configuration conf = tableEnv.getConfig().getConfiguration();
        // 1.设置空闲状态保留时间：left join的左右表数据、distinct的重复数据都会一直存在状态里,需要手动清除防止状态常驻内存
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
//        getJdbcConnector(tableEnv);
//        getElasticsearchConnector(tableEnv);
//        getKafkaConnector(tableEnv);
        getUpsertKafkaConnector(tableEnv);

        // 启动任务
        env.execute();
    }

    /**
     * DataGen SQL Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/datagen/
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
     * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/filesystem/
     * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/concepts/time_attributes/
     * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/sql/queries/window-agg/
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
     * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/jdbc/
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

    /**
     * Elasticsearch SQL Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/elasticsearch/
     * The Elasticsearch connector allows for writing into an index of the Elasticsearch engine.
     */
    private static void getElasticsearchConnector(StreamTableEnvironment tableEnv) {
        // 写es
        tableEnv.executeSql(
                "CREATE TABLE sink_es (\n" +
                        "    user_id      BIGINT,\n" +
                        "    user_name    STRING,\n" +
                        "    race         STRING,\n" +
                        "PRIMARY KEY (user_id) NOT ENFORCED\n" +
                        ") WITH (\n" +
                        "  'connector' = 'elasticsearch-7',\n" +
                        "  'hosts' = 'http://localhost:9200',\n" +
                        "  'index' = 'users'\n" +
                        ");"
        );
        tableEnv.executeSql("INSERT INTO sink_es VALUES (1001, 'grubby',' orc')");
    }

    /**
     * Kafka SQL Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/kafka/
     * The Kafka connector allows for reading data from and writing data into Kafka topics.
     */
    private static void getKafkaConnector(StreamTableEnvironment tableEnv) {
        // 读取ods层业务数据,并创建动态表
        tableEnv.executeSql(
                "CREATE TABLE ods_base_db (\n" +
                        "    `table`     STRING,\n" +
                        "    type        STRING,\n" +
                        "    data        MAP<STRING,STRING>\n" +
                        ") WITH (\n" +
                        "  'connector' = 'kafka',\n" +
                        "  'topic' = 'ods_base_db',\n" +
                        "  'properties.bootstrap.servers' = 'localhost:9092',\n" +
                        "  'properties.group.id' = 'gg',\n" +
                        "  'scan.startup.mode' = 'latest-offset',\n" +
                        "  'format' = 'json',\n" +
                        "  'json.ignore-parse-errors' = 'true'\n" +  // 过滤非json数据
                        ")"
        );

        // 从动态表过滤用户数据
        Table userInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                "    data['id'] id,\n" +
                "    data['name'] name,\n" +
                "    data['phone_num'] phone,\n" +
                "    data['gender'] gender,\n" +
                "    data['create_time'] create_time,\n" +
                "    data['operate_time'] update_time\n" +
                "FROM ods_base_db\n" +
                "WHERE `table` = 'user_info'"
        );
        // 打印测试,sqlQuery("")是批处理一次只能执行一个查询,后面代码就执行不到了
//        tableEnv.createTemporaryView("user_info", userInfo);
//        tableEnv.sqlQuery("SELECT * FROM user_info").execute().print();
        // 将动态表转换成流打印测试
        tableEnv.toDataStream(userInfo).print();  // +I[1, 顾凡, 13441279232, F, 2020-11-23 20:03:49, 2023-07-11 15:58:37]

        // 将用户数据写入kafka
        tableEnv.executeSql(
                "CREATE TABLE user_info (\n" +
                        "    id             STRING,\n" +
                        "    name           STRING,\n" +
                        "    phone          STRING,\n" +
                        "    gender         STRING,\n" +
                        "    create_time    STRING,\n" +
                        "    update_time    STRING\n" +
                        ") WITH (\n" +
                        "  'connector' = 'kafka',\n" +
                        "  'topic' = 'user_info',\n" +
                        "  'properties.bootstrap.servers' = 'localhost:9092',\n" +
                        "  'format' = 'json'\n" +
                        ")"
        );
        tableEnv.executeSql("INSERT INTO user_info SELECT * FROM " + userInfo);
    }

    /**
     * Upsert-Kafka SQL Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/upsert-kafka/
     * The Upsert Kafka connector allows for reading data from and writing data into Kafka topics in the upsert fashion.
     *
     * As a source, the upsert-kafka connector produces a changelog stream, where each data record represents an update or delete event.
     * a data record in a changelog stream is interpreted as an UPSERT aka INSERT/UPDATE because any existing row with the same key is overwritten.
     * Also, null values are interpreted in a special way: a record with a null value represents a "DELETE".
     *
     * As a sink, the upsert-kafka connector can consume a changelog stream.
     * It will write INSERT/UPDATE_AFTER data as normal Kafka messages value, and write DELETE data as Kafka messages with null values (indicate tombstone for the key).
     */
    private static void getUpsertKafkaConnector(StreamTableEnvironment tableEnv) {
        // 读取ods层业务数据,并创建动态表
        tableEnv.executeSql(
                "CREATE TABLE ods_base_db (\n" +
                        "    `table`     STRING,\n" +
                        "    type        STRING,\n" +
                        "    data        MAP<STRING,STRING>\n" +
                        ") WITH (\n" +
                        "  'connector' = 'kafka',\n" +
                        "  'topic' = 'ods_base_db',\n" +
                        "  'properties.bootstrap.servers' = 'localhost:9092',\n" +
                        "  'properties.group.id' = 'gg',\n" +
                        "  'scan.startup.mode' = 'latest-offset',\n" +
                        "  'format' = 'json',\n" +
                        "  'json.ignore-parse-errors' = 'true'\n" +  // 过滤非json数据
                        ")"
        );

        // 过滤订单明细和订单活动数据
        Table orderDetail = tableEnv.sqlQuery(
                "SELECT\n" +
                "    data['id'] id,\n" +
                "    data['order_id'] order_id,\n" +
                "    data['sku_id'] sku_id,\n" +
                "    data['sku_name'] sku_name,\n" +
                "    data['order_price'] order_price,\n" +
                "    data['create_time'] create_time\n" +
                "FROM ods_base_db\n" +
                "WHERE `table` = 'order_detail' AND type = 'insert'"
        );
        tableEnv.createTemporaryView("order_detail", orderDetail);

        Table orderDetailActivity = tableEnv.sqlQuery(
                "SELECT\n" +
                "    data['order_detail_id'] order_detail_id,\n" +
                "    data['activity_id'] activity_id\n" +
                "FROM ods_base_db\n" +
                "WHERE `table` = 'order_detail_activity' AND type = 'insert'"
        );
        tableEnv.createTemporaryView("order_detail_activity", orderDetailActivity);

        Table orderPreProcess = tableEnv.sqlQuery(
                "SELECT\n" +
                "    od.id,\n" +
                "    od.order_id,\n" +
                "    od.sku_id,\n" +
                "    od.sku_name,\n" +
                "    od.order_price,\n" +
                "    oa.activity_id,\n" +
                "    od.create_time\n" +
                "FROM order_detail od\n" +
                "LEFT JOIN order_detail_activity oa ON od.id = oa.order_detail_id"
        );
        tableEnv.toChangelogStream(orderPreProcess).print("order_pre_process");
        // 先后往order_detail和order_detail_activity表插入一条数据
        // mysql> insert into order_detail values(null,1001,3,'xiaomi',null,5999,1,'2023-07-12 11:20:45',null,null,null,null,null);
        // mysql> insert into order_detail_activity values(null,1001,81816,3,null,null,'2023-07-12 11:15:29');
        // idea输出结果
        // order_pre_process> +I[81816, 1001, 3, xiaomi, 5999.0, null, 2023-07-12 11:20:45]
        // order_pre_process> -D[81816, 1001, 3, xiaomi, 5999.0, null, 2023-07-12 11:20:45]
        // order_pre_process> +I[81816, 1001, 3, xiaomi, 5999.0, 3, 2023-07-12 11:20:45]
        // kafka数据
        // {"id":"81816","order_id":"1001","sku_id":"3","sku_name":"xiaomi","order_price":"5999.0","activity_id":null,"create_time":"2023-07-12 11:20:45"}
        // null
        // {"id":"81816","order_id":"1001","sku_id":"3","sku_name":"xiaomi","order_price":"5999.0","activity_id":"3","create_time":"2023-07-12 11:20:45"}

        // left join会生成回撤数据,所以得用upsert-kafka
        tableEnv.executeSql(
                "CREATE TABLE order_pre_process (\n" +
                        "    id             STRING,\n" +
                        "    order_id       STRING,\n" +
                        "    sku_id         STRING,\n" +
                        "    sku_name       STRING,\n" +
                        "    order_price    STRING,\n" +
                        "    activity_id    STRING,\n" +
                        "    create_time    STRING,\n" +
                        // 'upsert-kafka' tables require to define a PRIMARY KEY constraint.
                        "PRIMARY KEY (id) NOT ENFORCED\n" +
                        ") WITH (\n" +
                        "  'connector' = 'upsert-kafka',\n" +
                        "  'topic' = 'order_pre_process',\n" +
                        "  'properties.bootstrap.servers' = 'localhost:9092',\n" +
                        "  'key.format' = 'json',\n" +
                        "  'value.format' = 'json'\n" +
                        ")"
        );
        tableEnv.executeSql("INSERT INTO order_pre_process SELECT * FROM " + orderPreProcess);
    }
}
