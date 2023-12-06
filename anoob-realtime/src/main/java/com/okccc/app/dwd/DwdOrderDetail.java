package com.okccc.app.dwd;

import com.okccc.util.FlinkUtil;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;
import java.time.ZoneId;

/**
 * @Author: okccc
 * @Date: 2023/5/25 11:25:14
 * @Desc: 订单预处理明细(FlinkSQL - KafkaSource & MysqlSource - Join & Lookup Join - UpsertKafkaSink)
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/upsert-kafka/
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/table/functions/systemfunctions/#temporal-functions
 *
 * 下单业务的最细粒度是一个sku的下单或取消订单操作,所以将order_detail作为主表
 * order_detail数据在order_info都有对应的 - inner join
 * order_detail数据未必参加了活动order_detail_activity也未必使用了优惠券order_detail_coupon,所以要保留其独有数据 - left join
 * order_detail数据的source_type在base_dic也有对应的 - inner join
 *
 * 数据乱序
 * order_detail、order_info、order_detail_activity、order_detail_coupon不存在业务上的滞后问题,只考虑数据乱序即可
 * 比如用户下单后,order_info会插入一条数据,order_detail会插入与之对应的多条数据,由于同一个业务表的数据可能会进入不同kafka分区
 * 这样一来left join时,就会存在主表或从表数据迟到导致关联不上的情况,所以多个事实表关联要考虑数据乱序程度,让状态中的数据多保留一段时间
 *
 * A表作为主表与B表关联,A表数据进入算子但B表数据还没到,会先生成一条B表字段均为null的关联数据ab1,标记为+I
 * B表数据进入算子后会生成一条与ab1相同的数据并标记为-D,再生成一条关联后的数据标记为+I,这样生成的动态表对应的流称之为回撤流
 *
 * 先后往order_detail和order_detail_activity表插入一条数据
 * mysql> insert into order_detail values(null,1001,3,'xiaomi',null,5999,1,'2023-07-12 11:20:45',null,null,null,null,null);
 * mysql> insert into order_detail_activity values(null,1001,81816,3,null,null,'2023-07-12 11:15:29');
 *
 * idea输出结果
 * order_pre_process> +I[81816, 1001, 3, xiaomi, 5999.0, null, 2023-07-12 11:20:45]
 * order_pre_process> -D[81816, 1001, 3, xiaomi, 5999.0, null, 2023-07-12 11:20:46]
 * order_pre_process> +I[81816, 1001, 3, xiaomi, 5999.0, 3, 2023-07-12 11:20:46]
 *
 * kafka数据
 * {"id":"81816","order_id":"1001","sku_id":"3","sku_name":"xiaomi","order_price":"5999.0","activity_id":null,"create_time":"2023-07-12 11:20:45"}
 * null
 * {"id":"81816","order_id":"1001","sku_id":"3","sku_name":"xiaomi","order_price":"5999.0","activity_id":"3","create_time":"2023-07-12 11:20:46"}
 *
 * 动态表属于流处理模式,下面四种函数任选其一即可,此处选择CURRENT_ROW_TIMESTAMP()
 * LOCALTIMESTAMP: Returns the current SQL timestamp in local time zone, the return type is TIMESTAMP(3). It is evaluated for each record in streaming mode. But in batch mode, it is evaluated once as the query starts and uses the same result for every row.
 * CURRENT_TIMESTAMP: Returns the current SQL timestamp in the local time zone, the return type is TIMESTAMP_LTZ(3). It is evaluated for each record in streaming mode. But in batch mode, it is evaluated once as the query starts and uses the same result for every row.
 * NOW(): Returns the current SQL timestamp in the local time zone, this is a synonym of CURRENT_TIMESTAMP.
 * CURRENT_ROW_TIMESTAMP(): Returns the current SQL timestamp in the local time zone, the return type is TIMESTAMP_LTZ(3). It is evaluated for each record no matter in batch or streaming mode.
 */
public class DwdOrderDetail {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建表环境
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 空闲状态保留时间,取数据的最大乱序程度,防止状态常驻内存
//        System.out.println(tableEnv.getConfig().getIdleStateRetention());  // PT0S
        tableEnv.getConfig().setIdleStateRetention(Duration.ofSeconds(10));
//        System.out.println(tableEnv.getConfig().getIdleStateRetention());  // PT10S

        // 设置时区
        tableEnv.getConfig().setLocalTimeZone(ZoneId.of("Asia/Shanghai"));
//        System.out.println(tableEnv.getConfig().getLocalTimeZone());  // Asia/Shanghai

        // 测试系统时间函数,写入kafka时会转换成带"T"和"Z"格式的时间戳
        tableEnv.sqlQuery("select LOCALTIMESTAMP,CURRENT_TIMESTAMP,PROCTIME() pt,NOW() now,CURRENT_ROW_TIMESTAMP() crt").execute().print();
//        +----+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
//        | op |          LOCALTIMESTAMP |       CURRENT_TIMESTAMP |                      pt |                     now |                     crt |
//        +----+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+
//        | +I | 2023-07-04 10:54:26.599 | 2023-07-04 10:54:26.599 | 2023-07-04 10:54:26.599 | 2023-07-04 10:54:26.599 | 2023-07-04 10:54:26.599 |
//        +----+-------------------------+-------------------------+-------------------------+-------------------------+-------------------------+

        // 2.读取ods层业务数据(maxwell)
        tableEnv.executeSql(FlinkUtil.getOdsBaseDb("dwd_order_detail_g"));

        // 3.过滤order_detail数据
        Table orderDetail = tableEnv.sqlQuery(
                "SELECT\n" +
                        "    data['id'] id,\n" +
                        "    data['order_id'] order_id,\n" +
                        "    data['sku_id'] sku_id,\n" +
                        "    data['sku_name'] sku_name,\n" +
                        "    data['order_price'] order_price,\n" +
                        "    data['sku_num'] sku_num,\n" +
                        "    data['create_time'] create_time,\n" +
                        "    data['source_type'] source_type,\n" +
                        "    data['split_total_amount'] split_total_amount,\n" +
                        "    data['split_activity_amount'] split_activity_amount,\n" +
                        "    data['split_coupon_amount'] split_coupon_amount,\n" +
                        "    proc_time\n" +
                        "FROM ods_base_db\n" +
                        "WHERE `table` = 'order_detail' AND type = 'insert'"  // order_detail是增量数据
        );
        tableEnv.createTemporaryView("order_detail", orderDetail);

        // 过滤order_info数据
        Table orderInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                        "    data['id'] id,\n" +
                        "    data['consignee'] consignee,\n" +
                        "    data['consignee_tel'] consignee_tel,\n" +
                        "    data['total_amount'] total_amount,\n" +
                        "    data['order_status'] order_status,\n" +
                        "    data['user_id'] user_id,\n" +
                        "    data['payment_way'] payment_way,\n" +
                        "    data['delivery_address'] delivery_address,\n" +
                        "    data['order_comment'] order_comment,\n" +
                        "    data['out_trade_no'] out_trade_no,\n" +
                        "    data['trade_body'] trade_body,\n" +
                        "    data['create_time'] create_time,\n" +
                        "    data['operate_time'] operate_time,\n" +
                        "    data['expire_time'] expire_time,\n" +
                        "    data['process_status'] process_status,\n" +
                        "    data['tracking_no'] tracking_no,\n" +
                        "    data['parent_order_id'] parent_order_id,\n" +
                        "    data['province_id'] province_id,\n" +
                        "    data['activity_reduce_amount'] activity_reduce_amount,\n" +
                        "    data['coupon_reduce_amount'] coupon_reduce_amount,\n" +
                        "    data['original_total_amount'] original_total_amount,\n" +
                        "    data['freight'] freight,\n" +
                        "    data['freight_reduce'] freight_reduce,\n" +
                        "    data['refundable_time'] refundable_time,\n" +
                        "    type,\n" +
                        "    `old`\n" +
                        "FROM ods_base_db\n" +
                        "WHERE `table` = 'order_info' AND (type = 'insert' OR type = 'update')"  // order_info是新增和变化数据
        );
        tableEnv.createTemporaryView("order_info", orderInfo);

        // 过滤order_detail_activity数据
        Table orderDetailActivity = tableEnv.sqlQuery(
                "SELECT\n" +
                        "    data['id'] id,\n" +
                        "    data['order_id'] order_id,\n" +
                        "    data['order_detail_id'] order_detail_id,\n" +
                        "    data['activity_id'] activity_id,\n" +
                        "    data['activity_rule_id'] activity_rule_id,\n" +
                        "    data['sku_id'] sku_id,\n" +
                        "    data['create_time'] create_time\n" +
                        "FROM ods_base_db\n" +
                        "WHERE `table` = 'order_detail_activity' AND type = 'insert'"  // order_detail_activity是增量数据
        );
        tableEnv.createTemporaryView("order_detail_activity", orderDetailActivity);

        // 过滤order_detail_coupon数据
        Table orderDetailCoupon = tableEnv.sqlQuery(
                "SELECT\n" +
                        "    data['id'] id,\n" +
                        "    data['order_id'] order_id,\n" +
                        "    data['order_detail_id'] order_detail_id,\n" +
                        "    data['coupon_id'] coupon_id,\n" +
                        "    data['coupon_use_id'] coupon_use_id,\n" +
                        "    data['sku_id'] sku_id,\n" +
                        "    data['create_time'] create_time\n" +
                        "FROM ods_base_db\n" +
                        "WHERE `table` = 'order_detail_coupon' AND type = 'insert'"  // order_detail_coupon是增量数据
        );
        tableEnv.createTemporaryView("order_detail_coupon", orderDetailCoupon);

        // 4.读取mysql字典表base_dic
        tableEnv.executeSql(FlinkUtil.getBaseDic());

        // 5.多表关联,根据需要取每个表相应字段
        Table dwdOrderDetail = tableEnv.sqlQuery(
                "SELECT\n" +
                        "    od.id,\n" +
                        "    od.order_id,\n" +
                        "    od.sku_id,\n" +
                        "    od.sku_name,\n" +
                        "    od.order_price,\n" +
                        "    od.sku_num,\n" +
                        "    od.create_time,\n" +
                        "    od.source_type,\n" +
                        "    dic.dic_name,\n" +
                        "    od.split_total_amount,\n" +
                        "    od.split_activity_amount,\n" +
                        "    od.split_coupon_amount,\n" +
                        "    oi.user_id,\n" +
                        "    oi.province_id,\n" +
                        "    oa.activity_id,\n" +
                        "    oc.coupon_id,\n" +
                        "    CURRENT_ROW_TIMESTAMP() crt\n" +  // 预留处理时间字段,方便dws层下单业务和支付业务数据去重时比较时间大小
                        "FROM order_detail od\n" +
                        "JOIN order_info oi ON od.order_id = oi.id\n" +
                        "LEFT JOIN order_detail_activity oa ON od.id = oa.order_detail_id\n" +
                        "LEFT JOIN order_detail_coupon oc ON od.id = oc.order_detail_id\n" +
                        "JOIN base_dic FOR SYSTEM_TIME AS OF od.proc_time AS dic ON od.source_type = dic.dic_code\n" +
                        "WHERE type = 'insert'"  // 下单
//                "WHERE type = 'update' AND `old`['order_status'] is not null AND order_status = '1003'"  // 取消订单
        );
        // Table sink '*anonymous_datastream_sink$1*' doesn't support consuming update and delete changes which is produced by node Join(joinType=[LeftOuterJoin],
//        tableEnv.toDataStream(dwdOrderDetail).print("dwdOrderDetail");
        // left join会生成回撤数据,得用更新流,连接器也得用upsert-kafka
        tableEnv.toChangelogStream(dwdOrderDetail).print("dwdOrderDetail");

        // 6.将订单预处理数据写入kafka
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
                        "    crt                      TIMESTAMP_LTZ(3),\n" +
                        "PRIMARY KEY (id) NOT ENFORCED\n" +
                        ")" + FlinkUtil.getUpsertKafkaSinkDdl("dwd_order_detail")
        );
        tableEnv.executeSql("INSERT INTO dwd_order_detail SELECT * FROM " + dwdOrderDetail);

        // 启动任务
        env.execute();
    }
}
