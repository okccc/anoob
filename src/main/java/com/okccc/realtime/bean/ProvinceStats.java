package com.okccc.realtime.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Author: okccc
 * Date: 2022/1/7 10:14 上午
 * Desc: 地区主题实体类
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ProvinceStats {
    // 窗口开始时间
    private String stt;
    // 窗口结束时间
    private String edt;

    // 维度数据
    private Long province_id;
    private String province_name;
    private String area_code;
    private String iso_code;
    private String iso_3166_2;

    // 度量数据
    private Long  order_count;
    private BigDecimal order_amount;

    // 统计时间
    private Long ts;

    public ProvinceStats(OrderWide orderWide) {
        province_id = orderWide.getProvince_id();
        province_name = orderWide.getProvince_name();
        area_code = orderWide.getProvince_area_code();
        iso_code = orderWide.getProvince_iso_code();
        iso_3166_2 = orderWide.getProvince_iso_code();
        order_count = 1L;
        order_amount = orderWide.getSplit_total_amount();
        ts = new Date().getTime();
    }
}
