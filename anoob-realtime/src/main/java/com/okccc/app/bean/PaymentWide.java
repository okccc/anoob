package com.okccc.app.bean;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/8/22 17:44:07
 * @Desc: DWD层支付宽表实体类
 */
@Data
@Builder
public class PaymentWide {

    // dwd_payment_detail已有字段
    private String orderId;
    private String skuId;
    private String skuName;
    private String userId;
    private String provinceId;
    private Double totalAmount;
    private String callbackTime;

    // 用户维度
    private String userName;
    private String gender;
    private int age;

    // 地区维度
    private String provinceName;
    private String areaCode;
    private String isoCode;

    // sku维度
    private String spuId;
    private String tmId;
    private String category3Id;

    // spu维度
    private String spuName;

    // 品牌维度
    private String tmName;

    // 类别维度
    private String category3Name;
}
