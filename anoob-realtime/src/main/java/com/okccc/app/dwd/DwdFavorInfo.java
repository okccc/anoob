package com.okccc.app.dwd;

import com.okccc.util.FlinkUtil;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * @Author: okccc
 * @Date: 2023/6/14 18:11:08
 * @Desc: 互动域 - 收藏(FlinkSQL - KafkaSource - KafkaSink)
 * {
 *     "id":"1668926404132151298",
 *     "user_id":"228",
 *     "sku_id":"31",
 *     "create_time":"2023-06-14 18:20:36"
 * }
 */
public class DwdFavorInfo {
    public static void main(String[] args) {
        // 1.创建流处理执行环境和表环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 2.读取ods层业务数据(maxwell)
        tableEnv.executeSql(FlinkUtil.getOdsBaseDb("dwd_favor_info_g"));

        // 3.过滤收藏数据,用户收藏商品时收藏表会插入一条数据,因此筛选操作类型为insert的数据即可
        Table favorInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                "    data['id'] id,\n" +
                "    data['user_id'] user_id,\n" +
                "    data['sku_id'] sku_id,\n" +
                "    data['create_time'] create_time\n" +
                "FROM ods_base_db\n" +
                "WHERE `table` = 'favor_info'\n" +
                "AND (type = 'insert' OR (type = 'update' AND data['is_cancel'] = '0'))"
        );

        // 4.将收藏数据写入kafka
        tableEnv.executeSql(
                "CREATE TABLE dwd_favor_info (\n" +
                "    id             STRING,\n" +
                "    user_id        STRING,\n" +
                "    sku_id         STRING,\n" +
                "    create_time    STRING\n" +
                ")" + FlinkUtil.getKafkaSinkDdl("dwd_favor_info")
        );
        tableEnv.executeSql("INSERT INTO dwd_favor_info SELECT * FROM " + favorInfo);
    }

}
