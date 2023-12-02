package com.okccc.app.dwd;

import com.okccc.util.FlinkUtil;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2023/6/14 11:00:34
 * @Desc: 支付成功明细(FlinkSQL - KafkaSource & MysqlSource - Join & Lookup Join - UpsertKafkaSink)
 *
 * 业务背景
 * 支付成功明细数据是将payment_info数据与order_detail关联,订单明细数据在下单时生成,经过一系列处理进入dwd_order_detail
 * 通常下单后15min内支付即可,所以支付明细数据最多比订单明细数据滞后15min,结合可能存在的数据乱序问题,ttl设置为15min + 5s = 905s
 * 用户支付后,支付表会插入一条数据,此时回调时间和回调内容为空,调用第三方支付接口支付成功后会更新支付表的支付状态,并补全回调时间和回调内容
 *
 * 支付业务的最细粒度是一个sku的支付成功记录,所以将dwd_order_detail作为主表
 * payment_info数据在dwd_order_detail都有对应的 - inner join
 * payment_info数据的payment_type在base_dic也有对应的 - inner join
 */
public class DwdPaymentDetail {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        tableEnv.getConfig().setIdleStateRetention(Duration.ofSeconds(905));

        // 2.读取ods层业务数据(maxwell)
        // {"database":"mock","table":"order_info","type":"update","data":{"user_id":"1493",...},"old":{...},"ts":1686551814}
        tableEnv.executeSql(
                "CREATE TABLE IF NOT EXISTS ods_base_db (\n" +
                "    database     STRING,\n" +
                "    `table`      STRING,\n" +
                "    type         STRING,\n" +
                "    data         MAP<STRING, STRING>,\n" +
                "    `old`        MAP<STRING, STRING>,\n" +
                "    proc_time    AS PROCTIME()\n" +
                ")" + FlinkUtil.getKafkaSourceDdl("ods_base_db", "dwd_payment_detail_g")
        );

        // 3.过滤支付成功数据
        Table paymentInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                "    data['order_id'] order_id,\n" +
                "    data['user_id'] user_id,\n" +
                "    data['trade_no'] trade_no,\n" +  // 交易编号
                "    data['payment_type'] payment_type,\n" +  // 支付类型(微信、支付宝、银行卡)
                "    data['payment_status'] payment_status,\n" +  // 支付状态
                "    data['callback_time'] callback_time,\n" +  // 回调时间(支付成功时间)
                "    data['callback_content'] callback_content,\n" +  // 回调内容
                "    proc_time\n" +
                "FROM ods_base_db\n" +
                "WHERE `table` = 'payment_info' AND type = 'update' AND data['payment_status']='1602'"
        );
        tableEnv.createTemporaryView("payment_info", paymentInfo);

        // 读取dwd_order_detail数据
        tableEnv.executeSql(
                "CREATE TABLE dwd_order_detail (\n" +
                        "    id                       STRING  COMMENT '订单明细id',\n" +
                        "    order_id                 STRING  COMMENT '订单id',\n" +
                        "    sku_id                   STRING  COMMENT '商品id',\n" +
                        "    sku_name                 STRING  COMMENT '商品名称',\n" +
                        "    order_price              STRING  COMMENT '商品价格',\n" +
                        "    sku_num                  STRING  COMMENT '商品数量',\n" +
                        "    create_time              STRING  COMMENT '创建时间',\n" +
                        "    source_type              STRING  COMMENT '来源类型',\n" +
                        "    dic_name                 STRING  COMMENT '编码名称',\n" +
                        "    split_total_amount       STRING  COMMENT '减免后总金额',\n" +
                        "    split_activity_amount    STRING  COMMENT '活动减免金额',\n" +
                        "    split_coupon_amount      STRING  COMMENT '优惠券减免金额',\n" +
                        "    user_id                  STRING  COMMENT '用户id',\n" +
                        "    province_id              STRING  COMMENT '省份id',\n" +
                        "    activity_id              STRING  COMMENT '活动id',\n" +
                        "    coupon_id                STRING  COMMENT '购物券id',\n" +
                        "    crt                      TIMESTAMP_LTZ(3)\n" +
                ")" + FlinkUtil.getKafkaSourceDdl("dwd_order_detail", "dwd_payment_detail_gg")
        );

        // 4.读取mysql字典表base_dic
        tableEnv.executeSql(FlinkUtil.getBaseDic());

        // 5.多表关联
        Table dwdPaymentDetail = tableEnv.sqlQuery(
                "SELECT\n" +
                "    od.id,\n" +
                "    od.order_id,\n" +
                "    od.user_id,\n" +
                "    od.sku_id,\n" +
                "    od.sku_name,\n" +
                "    od.sku_num,\n" +
                "    od.order_price,\n" +
                "    od.province_id,\n" +
                "    od.activity_id,\n" +
                "    od.coupon_id,\n" +
                "    od.source_type,\n" +
                "    od.split_total_amount,\n" +
                "    od.split_activity_amount,\n" +
                "    od.split_coupon_amount,\n" +
                "    pi.trade_no trade_no,\n" +
                "    pi.payment_type payment_type_code,\n" +
                "    dic.dic_name payment_type_name,\n" +
                "    pi.payment_status payment_status,\n" +
                "    pi.callback_time,\n" +
                "    pi.callback_content,\n" +
                "    od.crt\n" +  // 取dwd_order_detail的crt作为处理时间字段,方便dws层数据去重时比较时间大小
                "FROM dwd_order_detail od\n" +
                "JOIN payment_info pi ON od.order_id = pi.order_id\n" +
                "JOIN base_dic FOR SYSTEM_TIME AS OF pi.proc_time AS dic ON pi.payment_type = dic.dic_code"
        );
        tableEnv.toDataStream(dwdPaymentDetail).print("dwdPaymentDetail");

        // 6.将支付数据写入kafka
        tableEnv.executeSql(
                "CREATE TABLE dwd_payment_detail (\n" +
                "    id                       STRING  COMMENT '订单明细id',\n" +
                "    order_id                 STRING  COMMENT '订单id',\n" +
                "    user_id                  STRING  COMMENT '用户id',\n" +
                "    sku_id                   STRING  COMMENT '商品id',\n" +
                "    sku_name                 STRING  COMMENT '商品名称',\n" +
                "    sku_num                  STRING  COMMENT '商品数量',\n" +
                "    order_price              STRING  COMMENT '商品价格',\n" +
                "    province_id              STRING  COMMENT '省份id',\n" +
                "    activity_id              STRING  COMMENT '活动id',\n" +
                "    coupon_id                STRING  COMMENT '购物券id',\n" +
                "    source_type              STRING  COMMENT '来源类型',\n" +
                "    split_total_amount       STRING  COMMENT '减免后总金额',\n" +
                "    split_activity_amount    STRING  COMMENT '活动减免金额',\n" +
                "    split_coupon_amount      STRING  COMMENT '优惠券减免金额',\n" +
                "    trade_no                 STRING  COMMENT '交易编号',\n" +
                "    payment_type_code        STRING  COMMENT '支付类型编号',\n" +
                "    payment_type_name        STRING  COMMENT '支付类型名称',\n" +
                "    payment_status           STRING  COMMENT '支付状态',\n" +
                "    callback_time            STRING  COMMENT '回调时间',\n" +
                "    callback_content         STRING  COMMENT '回调信息',\n" +
                "    crt                      TIMESTAMP_LTZ(3),\n" +
                "PRIMARY KEY(id) NOT ENFORCED\n" +
                ")" + FlinkUtil.getUpsertKafkaSinkDdl("dwd_payment_detail")
        );
        tableEnv.executeSql("INSERT INTO dwd_payment_detail SELECT * FROM " + dwdPaymentDetail);

        // 启动任务
        env.execute("DwdPaymentDetail");
    }
}
