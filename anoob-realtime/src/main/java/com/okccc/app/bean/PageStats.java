package com.okccc.app.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/6/22 15:03:12
 * @Desc: DWS层页面主题实体类
 */
@Data
@AllArgsConstructor
public class PageStats {

    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 度量数据(聚合)
    private Long homeUvCnt;  // 首页独立访客数
    private Long goodDetailUvCnt;  // 商品详情页独立访客数

    // 统计时间
    private Long ts;
}
