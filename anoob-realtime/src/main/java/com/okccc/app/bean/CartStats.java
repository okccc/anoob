package com.okccc.app.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/7/3 17:18:45
 * @Desc: DWD层添加购物车实体类
 */
@Data
@AllArgsConstructor
public class CartStats {

    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 度量数据(聚合)
    private Long cartAddUvCnt;  // 加购独立用户数

    // 统计时间
    private Long ts;
}
