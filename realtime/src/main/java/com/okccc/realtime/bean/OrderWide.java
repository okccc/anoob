package com.okccc.realtime.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.math.BigDecimal;

/**
 * @Author: okccc
 * @Date: 2021/10/28 下午6:20
 * @Desc: 订单宽表实体类
 */
@Data
@AllArgsConstructor
public class OrderWide {

    // 订单表字段
    Long order_id;
    String order_status;
    Long user_id;
    Long province_id;
    BigDecimal total_amount;
    BigDecimal activity_reduce_amount;
    BigDecimal coupon_reduce_amount;
    BigDecimal original_total_amount;
    BigDecimal feight_fee;
    String create_time;
    String create_date;

    // 订单明细表字段
    Long detail_id;
    Long sku_id;
    Long sku_num;
    BigDecimal order_price;
    String sku_name;
    BigDecimal split_total_amount;
    BigDecimal split_activity_amount;
    BigDecimal split_coupon_amount;

    // 用户维度
    String user_gender;
    Integer user_age;

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

    public OrderWide(OrderInfo orderInfo, OrderDetail orderDetail) {
        mergeOrderInfo(orderInfo);
        mergeOrderDetail(orderDetail);
    }

    public void mergeOrderInfo(OrderInfo orderInfo) {
        if (orderInfo != null) {
            this.order_id = orderInfo.id;
            this.order_status = orderInfo.order_status;
            this.user_id = orderInfo.user_id;
            this.province_id = orderInfo.province_id;
            this.total_amount =  orderInfo.total_amount;
            this.activity_reduce_amount = orderInfo.activity_reduce_amount;
            this.coupon_reduce_amount = orderInfo.coupon_reduce_amount;
            this.original_total_amount = orderInfo.original_total_amount;
            this.feight_fee = orderInfo.feight_fee;
            this.create_time = orderInfo.create_time;
            this.create_date = orderInfo.create_date;
        }
    }

    public void mergeOrderDetail(OrderDetail orderDetail) {
        if (orderDetail != null) {
            this.detail_id = orderDetail.id;
            this.sku_id = orderDetail.sku_id;
            this.sku_name = orderDetail.sku_name;
            this.order_price = orderDetail.order_price;
            this.sku_num = orderDetail.sku_num;
            this.split_total_amount = orderDetail.split_total_amount;
            this.split_activity_amount = orderDetail.split_activity_amount;
            this.split_coupon_amount = orderDetail.split_coupon_amount;
        }
    }

    public void mergeOtherOrderWide(OrderWide otherOrderWide) {
        this.order_status = ObjectUtils.firstNonNull( this.order_status ,otherOrderWide.order_status);
        this.create_time = ObjectUtils.firstNonNull(this.create_time,otherOrderWide.create_time);
        this.create_date = ObjectUtils.firstNonNull(this.create_date,otherOrderWide.create_date);
        this.coupon_reduce_amount = ObjectUtils.firstNonNull(this.coupon_reduce_amount,otherOrderWide.coupon_reduce_amount);
        this.activity_reduce_amount = ObjectUtils.firstNonNull(this.activity_reduce_amount,otherOrderWide.activity_reduce_amount);
        this.original_total_amount = ObjectUtils.firstNonNull(this.original_total_amount,otherOrderWide.original_total_amount);
        this.feight_fee = ObjectUtils.firstNonNull( this.feight_fee,otherOrderWide.feight_fee);
        this.total_amount = ObjectUtils.firstNonNull( this.total_amount,otherOrderWide.total_amount);
        this.user_id = ObjectUtils.<Long>firstNonNull(this.user_id,otherOrderWide.user_id);
        this.sku_id = ObjectUtils.firstNonNull( this.sku_id,otherOrderWide.sku_id);
        this.sku_name = ObjectUtils.firstNonNull(this.sku_name,otherOrderWide.sku_name);
        this.order_price = ObjectUtils.firstNonNull(this.order_price,otherOrderWide.order_price);
        this.sku_num = ObjectUtils.firstNonNull( this.sku_num,otherOrderWide.sku_num);
        this.split_activity_amount = ObjectUtils.firstNonNull(this.split_activity_amount);
        this.split_coupon_amount = ObjectUtils.firstNonNull(this.split_coupon_amount);
        this.split_total_amount = ObjectUtils.firstNonNull(this.split_total_amount);
    }

}
