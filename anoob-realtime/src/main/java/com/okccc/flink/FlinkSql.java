package com.okccc.flink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.TableDescriptor;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2021/9/20 下午12:47
 * @Desc: flink sql像mysql和hive一样也对标准sql语法做了些扩展,可以实现简单需求,复杂的还得用DataStream提供的api
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/table/concepts/time_attributes/
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

        // 创建表描述器
        TableDescriptor tableDescriptor = TableDescriptor.forConnector("datagen")
                .schema(Schema.newBuilder()
                        .column("f0", DataTypes.STRING())
                        .build())
                .option("fields.f0.kind", "random")
                .build();
        // 创建临时视图
        tableEnv.createTemporaryTable("t1", tableDescriptor);
        // 查询数据
        tableEnv.sqlQuery("select * from t1 limit 10").execute().print();
    }
}
