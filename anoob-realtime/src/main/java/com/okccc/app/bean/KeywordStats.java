package com.okccc.app.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: okccc
 * @Date: 2023/6/16 10:45:04
 * @Desc: DWS层搜索关键词实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeywordStats {

    // 窗口区间(开窗)
    private String stt;
    private String edt;

    // 关键词来源(辅助字段,不需要写入clickhouse)
//    @TransientSink
    private String source;

    // 维度数据(分组)
    private String keyword;

    // 度量数据(聚合)
    private Long cnt;

    // 统计时间
    private Long ts;
}
