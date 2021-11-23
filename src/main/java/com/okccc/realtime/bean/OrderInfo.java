package com.okccc.realtime.bean;

import lombok.Data;
import java.math.BigDecimal;

/**
 * Author: okccc
 * Date: 2021/10/28 下午6:02
 * Desc: 订单实体类
 */
@Data
public class OrderInfo {
    // 订单id
    Long id;
    // 订单状态
    String order_status;
    // 用户id
    Long user_id;
    // 省份id
    Long province_id;
    // 总金额
    BigDecimal total_amount;
    // 促销金额
    BigDecimal activity_reduce_amount;
    // 优惠券
    BigDecimal coupon_reduce_amount;
    // 原价金额
    BigDecimal original_total_amount;
    // 运费
    BigDecimal feight_fee;
    // 创建时间
    String create_time;
    // 操作时间
    String operate_time;
    // 失效时间
    String expire_time;
    // 由其他字段处理得到
    String create_date;
    String create_hour;
    Long create_ts;
}
