package com.okccc.app.dwd;

/**
 * @Author: okccc
 * @Date: 2023/5/6 14:37:08
 * @Desc: 添加购物车(FlinkSQL - KafkaSource & MysqlSource - Lookup Join - KafkaSink)
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/kafka/
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/jdbc/
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/jdbc/#lookup-cache
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/jdbc/#data-type-mapping
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/configuration/advanced/#anatomy-of-table-dependent1es
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/table/types/
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/table/sql/queries/joins/#lookup-join
 * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/table/concepts/time_attributes/#processing-time
 *
 * lookup join
 * A lookup join is typically used to enrich a table with data that is queried from an external system.
 * The join requires one table to have a processing time attribute and the other table to be backed by a lookup source connector.
 * lookup join主要用于flink sql表和外部系统(mysql)进行维度关联,要求主表必须有处理时间字段,维表由lookup连接器生成
 * 维度数据是有时效性的,所以需要时间字段来对数据版本进行标识,flink sql通常使用PROCTIME()函数获取当前系统时间作为处理时间字段
 * 事实表和维度表关联用lookup join,类似DataStream中的map算子通过jdbc查询mysql获取补充字段,事实表之间关联用interval join
 */
