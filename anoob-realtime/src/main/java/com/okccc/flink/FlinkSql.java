package com.okccc.flink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableDescriptor;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2021/9/20 下午12:47
 * @Desc: flink sql像mysql和hive一样也对标准sql语法做了些扩展,可以实现简单需求,复杂的还得用DataStream提供的api
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/table/concepts/time_attributes/
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/table/sql/queries/window-agg/
 */
public class FlinkSql {

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境,env执行DataStream相关操作
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
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

//        // 创建表描述器
//        TableDescriptor tableDescriptor = TableDescriptor.forConnector("datagen")
//                .schema(Schema.newBuilder()
//                        .column("f0", DataTypes.STRING())
//                        .build())
//                .option("fields.f0.kind", "random")
//                .build();
//        // 创建临时视图
//        tableEnv.createTemporaryTable("t1", tableDescriptor);
//        // 查询数据
//        tableEnv.sqlQuery("select * from t1 limit 10").execute().print();

        // 读取文件数据
        tableEnv.executeSql(
                "CREATE TEMPORARY TABLE user_behavior (\n" +
                        "    `user_id`        STRING,\n" +
                        "    `item_id`        STRING,\n" +
                        "    `category_id`    STRING,\n" +
                        "    `behavior`       STRING,\n" +
                        "    `ts`             BIGINT,\n" +
                        // 涉及window的操作创建表时必须提供TIMESTAMP(3)或TIMESTAMP_LTZ(3)类型的时间列
                        // 处理时间可以直接用PROCTIME()函数定义,返回TIMESTAMP_LTZ类型
//                        "     `ts_ltz` AS PROCTIME()\n" +
                        // 事件时间通常是将表中某个时间列转换成TIMESTAMP(3)或TIMESTAMP_LTZ(3)类型,并且要设置水位线
                        // TO_TIMESTAMP是转换成UTC时间戳,TO_TIMESTAMP_LTZ是转换成本地时区的时间戳(UTC+8)
                        "    `ts_ltz` AS TO_TIMESTAMP_LTZ(ts, 3),\n" +
                        "    WATERMARK FOR ts_ltz AS ts_ltz - INTERVAL '3' SECOND\n" +
                        ") WITH (\n" +
                        "  'connector' = 'filesystem',\n" +
                        "  'path' = '/Users/okc/projects/anoob/anoob-realtime/input/UserBehavior.csv',\n" +
                        "  'format' = 'csv'\n" +
                        ")"
        );
        // 查询测试
        tableEnv.sqlQuery("select * from user_behavior limit 10").execute().print();
        // 查看表结构
        Table table = tableEnv.sqlQuery("select * from user_behavior");
        table.printSchema();  // (`user_id` STRING,`item_id` STRING,`category_id` STRING,`behavior` STRING,`ts` BIGINT,`ts_ltz` TIMESTAMP_LTZ(3) *ROWTIME*)

        // 先按商品分组开窗
        String sql01 = "SELECT item_id,window_start,window_end,count(*) cnt \n" +
                "FROM TABLE(\n" +
                "    HOP(TABLE user_behavior, DESCRIPTOR(ts_ltz), INTERVAL '5' MINUTES, INTERVAL '1' HOUR)) \n" +
                "GROUP BY item_id,window_start,window_end";

        // 再按窗口分组排序
        String sql02 = "SELECT *,ROW_NUMBER() OVER(PARTITION BY window_start,window_end ORDER BY cnt DESC) AS rn FROM (" + sql01 + ")";

        // 取topN
        String sql03 = "SELECT * FROM (" + sql02 + ") WHERE rn <= 3";

        // 查询结果
        tableEnv.sqlQuery(sql03).execute().print();
    }
}
