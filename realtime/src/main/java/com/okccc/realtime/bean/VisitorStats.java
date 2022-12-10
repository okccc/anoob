package com.okccc.realtime.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2021/12/13 2:55 下午
 * @Desc: 访客主题实体类
 */
@Data
@AllArgsConstructor
public class VisitorStats {
    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 维度数据(分组)
    private String vc;  // 版本
    private String ch;  // 渠道
    private String ar;  // 地区
    private String is_new;  // 新老用户标识

    // 度量数据(聚合)
    private Long pv_ct;  // 页面访问数  
    private Long uv_ct;  // 独立访客数
    private Long sv_ct;  // 进入页面次数
    private Long uj_ct;  // 跳出页面次数
    private Long dur_sum;  // 持续访问时长

    // 统计时间
    private Long ts;
}
