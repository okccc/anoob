package com.okccc.app.dwd;

import com.okccc.util.FlinkUtil;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * @Author: okccc
 * @Date: 2023/6/14 16:35:07
 * @Desc: 退款明细(FlinkSQL - KafkaSource & MysqlSource - Join & Lookup Join - KafkaSink)
 */
public class DwdRefundDetail {

    public static void main(String[] args) {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 2.读取ods层业务数据(maxwell)
        // {"database":"mock","table":"order_info","type":"update","data":{"user_id":"1493",...},"old":{...},"ts":1686551814}
        tableEnv.executeSql(
                "CREATE TABLE IF NOT EXISTS ods_base_db (\n" +
                "    database     STRING,\n" +
                "    `table`      STRING,\n" +
                "    type         STRING,\n" +
                "    data         MAP<STRING, STRING>,\n" +
                "    `old`        MAP<STRING, STRING>,\n" +
                "    ts           BIGINT,\n" +
                "    proc_time    AS PROCTIME()\n" +
                ")" + FlinkUtil.getKafkaSourceDdl("ods_base_db", "dwd_refund_detail_g")
        );

        // 3.读取mysql字典表base_dic
        tableEnv.executeSql(FlinkUtil.getBaseDic());

        // 4.读取退款表数据并过滤退款成功的数据
        Table refundPayment = tableEnv.sqlQuery(
                "SELECT\n" +
                "    data['id'] id,\n" +
                "    data['order_id'] order_id,\n" +
                "    data['sku_id'] sku_id,\n" +
                "    data['payment_type'] payment_type,\n" +
                "    data['callback_time'] callback_time,\n" +
                "    data['total_amount'] total_amount,\n" +
                "    proc_time,\n" +
                "    ts\n" +
                "FROM ods_base_db\n" +
                "WHERE `table` = 'refund_payment'"
        );
        tableEnv.createTemporaryView("refund_payment", refundPayment);

        // 读取订单表数据并过滤退款成功的数据
        Table orderInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                "    data['id'] id,\n" +
                "    data['user_id'] user_id,\n" +
                "    data['province_id'] province_id,\n" +
                "    `old`\n" +
                "FROM ods_base_db\n" +
                "WHERE `table` = 'order_info'\n" +
                "AND type = 'update' AND data['order_status'] = '1006' AND `old`['order_status'] is not null"
        );
        tableEnv.createTemporaryView("order_info", orderInfo);

        // 读取退单表数据并过滤退款成功的数据
        Table orderRefundInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                "    data['order_id'] order_id,\n" +
                "    data['sku_id'] sku_id,\n" +
                "    data['refund_num'] refund_num,\n" +
                "    `old`\n" +
                "FROM ods_base_db\n" +
                "WHERE `table` = 'order_refund_info'"
        );
        tableEnv.createTemporaryView("order_refund_info", orderRefundInfo);

        // 5.多表关联
        Table resultTable = tableEnv.sqlQuery(
                "SELECT\n" +
                "    rp.id,\n" +
                "    oi.user_id,\n" +
                "    rp.order_id,\n" +
                "    rp.sku_id,\n" +
                "    oi.province_id,\n" +
                "    rp.payment_type,\n" +
                "    dic.dic_name payment_type_name,\n" +
                "    DATE_FORMAT(rp.callback_time,'yyyy-MM-dd') date_id,\n" +
                "    rp.callback_time,\n" +
                "    ri.refund_num,\n" +
                "    rp.total_amount,\n" +
                "    rp.ts,\n" +
                "    current_row_timestamp() crt\n" +
                "FROM refund_payment rp\n" +
                "JOIN order_info oi ON rp.order_id = oi.id\n" +
                "JOIN order_refund_info ri ON rp.order_id = ri.order_id AND rp.sku_id = ri.sku_id\n" +
                "JOIN base_dic FOR SYSTEM_TIME AS OF rp.proc_time AS dic ON rp.payment_type = dic.dic_code"
        );

        // 6.将退款数据写入kafka
        tableEnv.executeSql(
                "CREATE TABLE dwd_refund_detail (\n" +
                "    id                   STRING,\n" +
                "    user_id              STRING,\n" +
                "    order_id             STRING,\n" +
                "    sku_id               STRING,\n" +
                "    province_id          STRING,\n" +
                "    payment_type_code    STRING,\n" +
                "    payment_type_name    STRING,\n" +
                "    date_id              STRING,\n" +
                "    callback_time        STRING,\n" +
                "    refund_num           STRING,\n" +
                "    refund_amount        STRING,\n" +
                "    ts                   STRING,\n" +
                "    crt                  TIMESTAMP_LTZ(3)\n" +
                ")" + FlinkUtil.getKafkaSinkDdl("dwd_refund_detail")
        );
        tableEnv.executeSql("INSERT INTO dwd_refund_detail SELECT * FROM " + resultTable);
    }
}
