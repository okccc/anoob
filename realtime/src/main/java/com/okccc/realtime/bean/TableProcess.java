package com.okccc.realtime.bean;

import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2021/10/7 下午4:17
 * @Desc: 配置类,对应mysql配置表table_process
 *
 * insert into table_process values("base_trademark", "insert", "hbase", "dim_base_trademark", "id,tm_name", "id", "");
 * insert into table_process values("order_info", "update", "kafka", "dwd_order_info", "id,consignee,...", "id", "");
 * +----------------+--------------+-----------+--------------------+------------------+---------+-------------+
 * | source_table   | operate_type | sink_type | sink_table         | sink_columns     | sink_pk | sink_extend |
 * +----------------+--------------+-----------+--------------------+------------------+---------+-------------+
 * | base_trademark | insert       | hbase     | dim_base_trademark | id,tm_name       | id      |             |
 * | order_info     | update       | kafka     | dwd_order_info     | id,consignee,... | id      |             |
 * +----------------+--------------+-----------+--------------------+------------------+---------+-------------+
 */

@Data  // lombok可以简化JavaBean开发,会自动实现属性的get/set/equals/hashcode/toString方法,可通过Structure查看
public class TableProcess {
    // 动态分流Sink常量
    public static final String SINK_TYPE_HBASE = "hbase";
    public static final String SINK_TYPE_KAFKA = "kafka";
    public static final String SINK_TYPE_CK = "clickhouse";
    // 来源表
    String sourceTable;
    // 操作类型(insert/update/delete)
    String operateType;
    // 输出类型(hbase/kafka)
    String sinkType;
    // 输出表
    String sinkTable;
    // 输出字段
    String sinkColumns;
    // 主键字段
    String sinkPk;
    // 建表扩展
    String sinkExtend;
}
