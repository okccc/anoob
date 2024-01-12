package com.okccc.app.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/6/27 15:04:39
 * @Desc: DWS层访客主题实体类
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
    private String isNew;  // 新老用户标识

    // 度量数据(聚合)
    private Long pvCnt;  // 页面访问数
    private Long uvCnt;  // 独立访客数
    private Long svCnt;  // 进入页面次数
    private Long ujCnt;  // 跳出页面次数
    private Long durSum;  // 持续访问时长

    // 统计时间
    private Long ts;
}
