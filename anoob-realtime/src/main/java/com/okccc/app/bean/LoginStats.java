package com.okccc.app.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/6/30 10:27:40
 * @Desc: DWS层用户登录实体类
 */
@Data
@AllArgsConstructor
public class LoginStats {

    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 度量数据(聚合)
    private Long uvCnt;  // 当日独立用户数
    private Long back7Cnt;  // 7日回流用户数
    private Long back15Cnt;  // 15日回流用户数
    private Long back30Cnt;  // 30日回流用户数

    // 统计时间
    private Long ts;
}
