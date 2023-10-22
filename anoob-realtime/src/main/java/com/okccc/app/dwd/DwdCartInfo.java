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
 *
 * 维度退化
 * 将维度退化到事实表中,减少事实表和维度表的关联,降低维度数仓的复杂度
 * 数仓维度建模时有一种维度叫Degenerate Dimension,比如将订单id这类事务编号合并到dwd层事实表,这样就不需要创建订单维度表
 * 适用于base_dic这种单一维度表,base_category1/2/3这些有层级关联的就不适合,因为维度退化通常只退化某一张表,不会退化整个维度
 *
 * ERROR [Source: ods_base_db[1] -> Calc[2] -> LookupJoin[3] -> Calc[4] -> dwd_cart_add[5]: Writer -> dwd_cart_add[5]: Committer (1/1)#0]
 * org.apache.flink.connector.jdbc.table.JdbcRowDataLookupFunction - JDBC executeBatch error, retry times = 0
 * com.mysql.cj.jdbc.exceptions.CommunicationsException: The last packet successfully received from the server was 2,634,557 milliseconds ago.
 * 分析："-"前半截是类的全路径,"-"后半截是该类打印的错误日志,直接double shift进入该类查看源码
 * FlinkSQL访问mysql数据库长时间不操作连接会自动断开,报这个错然后自动重连,消费kafka发现其实数据已经写进去了
 */
