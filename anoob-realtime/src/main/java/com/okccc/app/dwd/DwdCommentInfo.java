package com.okccc.app.dwd;

import com.okccc.util.FlinkUtil;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * @Author: okccc
 * @Date: 2023/6/14 18:23:20
 * @Desc: 互动域 - 评论(FlinkSQL - KafkaSource - MysqlSource - Lookup Join - KafkaSink)
 * {
 *     "id":"1669239334266576898",
 *     "user_id":"3368",
 *     "sku_id":"8",
 *     "order_id":"28177",
 *     "create_time":"2023-06-15 15:04:04",
 *     "appraise_code":"1204",
 *     "appraise_name":"好评"
 * }
 */
public class DwdCommentInfo {

    public static void main(String[] args) {
        // 1.创建流处理执行环境和表环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 2.读取ods层业务数据(maxwell)
        tableEnv.executeSql(FlinkUtil.getOdsBaseDb("dwd_comment_info_g"));

        // 3.读取mysql字典表base_dic
        tableEnv.executeSql(FlinkUtil.getBaseDic());

        // 4.过滤评论数据
        Table commentInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                "    data['id'] id,\n" +
                "    data['user_id'] user_id,\n" +
                "    data['sku_id'] sku_id,\n" +
                "    data['order_id'] order_id,\n" +
                "    data['create_time'] create_time,\n" +
                "    data['appraise'] appraise,\n" +
                "    proc_time\n" +
                "FROM ods_base_db\n" +
                "WHERE `table` = 'comment_info' AND type = 'insert'"
        );
        tableEnv.createTemporaryView("comment_info", commentInfo);

        // 5.关联两张表
        Table dwdCommentInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                "    ci.id,\n" +
                "    ci.user_id,\n" +
                "    ci.sku_id,\n" +
                "    ci.order_id,\n" +
                "    ci.create_time,\n" +
                "    ci.appraise,\n" +
                "    dic.dic_name\n" +
                "FROM comment_info ci\n" +
                "JOIN base_dic FOR SYSTEM_TIME AS OF ci.proc_time AS dic on ci.appraise = dic.dic_code"
        );

        // 6.将评论数据写入kafka
        tableEnv.executeSql(
                "CREATE TABLE dwd_comment_info (\n" +
                "    id               STRING,\n" +
                "    user_id          STRING,\n" +
                "    sku_id           STRING,\n" +
                "    order_id         STRING,\n" +
                "    create_time      STRING,\n" +
                "    appraise_code    STRING,\n" +
                "    appraise_name    STRING\n" +
                ")" + FlinkUtil.getKafkaSinkDdl("dwd_comment_info")
        );
        tableEnv.executeSql("INSERT INTO dwd_comment_info SELECT * FROM " + dwdCommentInfo);
    }
}
