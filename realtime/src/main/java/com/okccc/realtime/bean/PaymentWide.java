package com.okccc.realtime.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.beanutils.BeanUtils;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

/**
 * Author: okccc
 * Date: 2021/11/29 下午3:02
 * Desc: 支付宽表实体类
 */
@Data
@AllArgsConstructor
public class PaymentWide {

    // 支付id
    Long payment_id;
    // 主题
    String subject;
    // 支付类型
    String payment_type;
    // 支付创建时间
    String payment_create_time;
    // 回调时间
    String callback_time;
    // 订单明细id
    Long detail_id;
    // 订单id
    Long order_id;
    // 商品id
    Long sku_id;
    // 订单价格
    BigDecimal order_price;
    // 商品数量
    Long sku_num;
    // 商品名称
    String sku_name;
    // 省份id
    Long province_id;
    // 订单状态
    String order_status;
    // 用户id
    Long user_id;
    // 总金额
    BigDecimal total_amount;
    // 实时减免金额
    BigDecimal activity_reduce_amount;
    // 优惠券减免金额
    BigDecimal coupon_reduce_amount;
    // 初始总金额
    BigDecimal original_total_amount;
    // 运费
    BigDecimal feight_fee;
    // 分摊运费
    BigDecimal split_feight_fee;
    // 分摊总金额
    BigDecimal split_total_amount;
    // 分摊促销金额
    BigDecimal split_activity_amount;
    // 分摊优惠金额
    BigDecimal split_coupon_amount;
    // 订单创建时间
    String order_create_time;

    // 用户维度
    Integer user_age;
    String user_gender;

    // 地区维度
    String province_name;
    String province_area_code;
    String province_iso_code;
    String province_3166_2_code;

    // sku维度
    Long spu_id;
    Long tm_id;
    Long category3_id;
    // spu维度
    String spu_name;
    // 品牌维度
    String tm_name;
    // 类别维度
    String category3_name;

    public PaymentWide(PaymentInfo paymentInfo, OrderWide orderWide) {
        mergeOrderWide(orderWide);
        mergePaymentInfo(paymentInfo);
    }

    public void mergeOrderWide(OrderWide orderWide) {
        if (orderWide != null) {
            try {
                BeanUtils.copyProperties(this, orderWide);
                order_create_time=orderWide.create_time;
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    public void  mergePaymentInfo(PaymentInfo paymentInfo) {
        if (paymentInfo != null) {
            try {
                BeanUtils.copyProperties(this, paymentInfo);
                payment_create_time = paymentInfo.create_time;
                payment_id = paymentInfo.id;
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

}
