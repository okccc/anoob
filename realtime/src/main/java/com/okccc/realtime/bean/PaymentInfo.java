package com.okccc.realtime.bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author: okccc
 * @Date: 2021/11/29 下午3:00
 * @Desc: 支付实体类
 */
@Data
public class PaymentInfo {
    // 支付id
    Long id;
    // 订单id
    Long order_id;
    // 用户id
    Long user_id;
    // 总金额
    BigDecimal total_amount;
    // 主题
    String subject;
    // 支付类型
    String payment_type;
    // 创建时间
    String create_time;
    // 回调时间
    String callback_time;
}
