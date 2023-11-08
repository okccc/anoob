package com.okccc.app.dwd;

import com.okccc.util.FlinkUtil;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * @Author: okccc
 * @Date: 2023/6/30 18:10:10
 * @Desc: 注册明细(FlinkSQL - KafkaSource - KafkaSink)
 */
public class DwdRegisterInfo {

    public static void main(String[] args) {
        // 1.创建流处理执行环境和表环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 2.读取ods层业务数据(maxwell)
        tableEnv.executeSql(FlinkUtil.getOdsBaseDb("dwd_register_info_g"));

        // 3.过滤注册数据
        Table registerInfo = tableEnv.sqlQuery(
            "SELECT\n" +
               "    data['id'] user_id,\n" +
               "    data['create_time'] create_time,\n" +
               "    ts\n" +
               "FROM ods_base_db\n" +
               "WHERE `table` = 'user_info' AND type = 'insert'"
        );

        // 4.将注册数据写入kafka
        tableEnv.executeSql(
                "CREATE TABLE dwd_register_info (\n" +
                "    user_id        STRING,\n" +
                "    create_time    STRING,\n" +
                "    ts             BIGINT\n" +
                ")" + FlinkUtil.getKafkaSinkDdl("dwd_register_info")
        );
        tableEnv.executeSql("INSERT INTO dwd_register_info SELECT * FROM " + registerInfo);
    }
}
