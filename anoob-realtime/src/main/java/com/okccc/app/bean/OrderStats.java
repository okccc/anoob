package com.okccc.app.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/7/18 10:13:59
 * @Desc: DWS层下单实体类
 */
@Data
@AllArgsConstructor
public class OrderStats {

    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 度量数据(聚合)
    private Long orderUvCnt;  // 下单独立用户数
    private Long orderNewCnt;  // 首次下单用户数
    private Double orderOriginalTotalAmount;  // 下单原始总金额
    private Double orderSplitActivityAmount; // 下单活动减免金额
    private Double orderSplitCouponAmount;  // 下单优惠券减免金额

    // 统计时间
    private Long ts;
}
