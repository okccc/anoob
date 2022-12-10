package com.okccc.realtime.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @Author: okccc
 * @Date: 2022/1/7 10:14 上午
 * @Desc: 地区主题实体类
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AreaStats {
    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 维度数据(分组)
    private Long province_id;
    private String province_name;
    private String area_code;
    private String iso_code;
    private String iso_3166_2;

    // 度量数据(聚合)
    private Long order_count;
    private BigDecimal order_amount;

    // 统计时间
    private Long ts;
}
