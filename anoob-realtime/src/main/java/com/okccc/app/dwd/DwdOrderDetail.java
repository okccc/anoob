package com.okccc.app.dwd;

/**
 * @Author: okccc
 * @Date: 2023/5/25 11:25:14
 * @Desc: 订单预处理明细(FlinkSQL - KafkaSource & MysqlSource - Join & Lookup Join - UpsertKafkaSink)
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/upsert-kafka/
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
 */