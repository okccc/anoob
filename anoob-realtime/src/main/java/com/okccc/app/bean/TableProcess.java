package com.okccc.app.bean;

import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/3/9 15:22:17
 * @Desc: dim层配置实体类,对应mysql配置表table_process
 */
@Data
public class TableProcess {

    // 来源表(Mysql)
    String sourceTable;

    // 输出表(Hbase)
    String sinkTable;

    // 输出字段
    String sinkColumns;

    // 主键字段
    String sinkPk;

    // 建表扩展
    String sinkExtend;
}
