package com.okccc.realtime.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Author: okccc
 * Date: 2022/1/13 10:11 上午
 * Desc: 关键词主题实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeywordStats {
    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 维度数据(分组)
    private String keyword;

    // 度量数据(聚合)
    private Long ct;
    private String source;

    // 统计时间
    private Long ts;
}
