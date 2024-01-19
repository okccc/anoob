package com.okccc.app.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/7/3 16:49:31
 * @Desc: DWS层用户注册实体类
 */
@Data
@AllArgsConstructor
public class RegisterStats {

    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 度量数据(聚合)
    private Long registerCnt;  // 用户注册数

    // 统计时间
    private Long ts;
}
