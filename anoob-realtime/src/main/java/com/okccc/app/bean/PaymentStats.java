package com.okccc.app.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/7/3 18:34:58
 * @Desc: DWS层支付成功实体类
 */
@Data
@AllArgsConstructor
public class PaymentStats {

    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 度量数据(聚合)
    private Long payUvCnt;  // 支付独立用户数
    private Long payNewCnt;  // 首次支付用户数

    // 统计时间
    private Long ts;
}
