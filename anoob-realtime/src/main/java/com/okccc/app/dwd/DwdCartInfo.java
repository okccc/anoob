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
 *
 * lookup cache
 * The lookup cache is used to improve performance of temporal join the JDBC connector.
 * The oldest rows in cache will be expired when the cache hit to the max cached rows lookup.cache.max-rows or when the row exceeds the max time to live lookup.cache.ttl.
 * users can tune lookup.cache.ttl to a smaller value to have a better fresh data, but this may increase the number of requests send to database. So this is a balance between throughput and correctness.
 * 超过最大缓存行数会清除最老的记录,存活时间超过ttl也会清除
 * 缓存可以提高查询速度,但缓存可能不是最新的,将ttl调小尽可能获取最新数据但会增加访问数据库的次数,所以这是吞吐量和准确性之间的平衡
 */
