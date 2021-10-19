package com.okccc.flink;

import com.alibaba.ververica.cdc.connectors.mysql.MySQLSource;
import com.alibaba.ververica.cdc.connectors.mysql.table.StartupOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * Author: okccc
 * Date: 2021/8/27 上午10:22
 * Desc: 动态读取mysql数据
 */
public class FlinkCdc {
    public static void main(String[] args) throws Exception {
        /*
         * CDC(ChangeDataCapture): 监控并捕获数据库的insert/update/delete记录,按顺序写入消息队列供下游服务订阅和消费
         * Flink-CDC: 通过flink-cdc-connectors组件直接从mysql和pg等数据库读取全量或增量的变更数据,连kafka中间件都省了
         * github地址：https://github.com/ververica/flink-cdc-connectors
         *
         * console输出日志
         * 十月 07, 2021 6:41:20 下午 com.github.shyiko.mysql.binlog.BinaryLogClient connect
         * 信息: Connected to localhost:3306 at mysql-bin.000002/154 (sid:6388, cid:7)
         * 使用flink-cdc时要关闭canal/maxwell,不然可能抓不到数据
         */

        // 创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);

        // DataStream-api方式(常用)
        demo01(env);
        // Table/Sql-api方式,1.12版本可以,1.13版本有点问题
//        demo02(env);

        // 启动任务
        env.execute();
    }

    private static void demo01(StreamExecutionEnvironment env) {
        // 获取数据源
        SourceFunction<String> sourceFunction = MySQLSource.<String>builder()
                .hostname("localhost")
                .port(3306)
                .databaseList("realtime")
                .tableList("realtime.t_user, realtime.table_process")
                .username("root")
                .password("root@123")
                .startupOptions(StartupOptions.initial())  // initial启动时会扫描历史数据,然后继续读取最新的binlog
//                .deserializer(new StringDebeziumDeserializationSchema()) // 默认反序列化方式返回的数据格式不太友好
                .build();
        env
                .addSource(sourceFunction)
                .print();
    }

    private static void demo02(StreamExecutionEnvironment env) {
        // 创建表执行环境
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        // 转换动态表
        tableEnv.executeSql(
                "CREATE TABLE user_info (" +
                        " id INT NOT NULL," +
                        " name STRING," +
                        " age INT" +
                        ") WITH (" +
                        " 'connector' = 'mysql-cdc'," +
                        " 'hostname' = 'localhost'," +
                        " 'port' = '3306'," +
                        " 'username' = 'root'," +
                        " 'password' = 'root@123'," +
                        " 'database-name' = 'realtime'," +
                        " 'table-name' = 't_user'" +
                        ")"
        );

        // 查询数据
        tableEnv
                .executeSql("select * from user_info")
                .print();
    }

}
