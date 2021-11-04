package com.okccc.realtime.bean;

import lombok.Data;
import java.math.BigDecimal;

/**
 * Author: okccc
 * Date: 2021/10/28 下午6:02
 * Desc: 订单明细实体类
 */
@Data
public class OrderDetail {
    // id
    Long id;
    // 订单id
    Long order_id;
    // sku_id
    Long sku_id;
    // 购买数量
    Long sku_num ;
    // sku名称(冗余)
    String sku_name;
    // 购买价格(下单时sku价格)
    BigDecimal order_price ;
    // 创建时间
    String create_time;
    // 分摊总金额
    BigDecimal split_total_amount;
    // 分摊促销金额
    BigDecimal split_activity_amount;
    // 分摊优惠金额
    BigDecimal split_coupon_amount;
    // 由其它字段处理得到
    Long create_ts;
}
