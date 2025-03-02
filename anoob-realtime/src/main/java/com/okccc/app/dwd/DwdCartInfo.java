package com.okccc.app.dwd;

import com.okccc.util.FlinkUtil;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2023/5/6 14:37:08
 * @Desc: 添加购物车(FlinkSQL - KafkaSource & MysqlSource - Lookup Join - KafkaSink)
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/kafka/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/jdbc/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/jdbc/#lookup-cache
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/table/jdbc/#data-type-mapping
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/configuration/advanced/#anatomy-of-table-dependent1es
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/types/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/sql/queries/joins/#lookup-join
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/concepts/time_attributes/#processing-time
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
public class DwdCartInfo {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境,env执行DataStream操作
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 创建表环境,tableEnv执行Table操作
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        tableEnv.getConfig().setIdleStateRetention(Duration.ofHours(1));

        // 2.读取ods层业务数据(maxwell)
        tableEnv.executeSql(FlinkUtil.getOdsBaseDb("dwd_cart_info_g"));

        // 3.过滤cart_info数据,封装成动态表
        Table cartInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                        "    data['id'] id,\n" +
                        "    data['user_id'] user_id,\n" +
                        "    data['sku_id'] sku_id,\n" +
                        "    data['sku_name'] sku_name,\n" +
                        "    data['cart_price'] cart_price,\n" +
                        "    if(type='insert',data['sku_num'],cast(cast(data['sku_num'] as int) - cast(`old`['sku_num'] as int) as string)) sku_num,\n" +
                        "    data['create_time'] create_time,\n" +
                        "    data['source_type'] source_type,\n" +
                        "    proc_time\n" +
                        "FROM ods_base_db\n" +
                        "WHERE `table` = 'cart_info'\n" +
                        "AND (type = 'insert' OR (type = 'update' and `old`['sku_num'] is not null and cast(data['sku_num'] as int) > cast(`old`['sku_num'] as int)))"
        );
//        cartInfo.printSchema();  // (`id` STRING,`user_id` STRING,...,`proc_time` TIMESTAMP_LTZ(3) NOT NULL *PROCTIME*)
        tableEnv.createTemporaryView("cart_info", cartInfo);

        // 4.读取mysql字典表base_dic
        tableEnv.executeSql(FlinkUtil.getBaseDic());

        // 5.关联加购表和字典表生成维度退化后的加购表
        Table dwdCartInfo = tableEnv.sqlQuery(
                "SELECT\n" +
                        "    ci.id,\n" +
                        "    ci.user_id,\n" +
                        "    ci.sku_id,\n" +
                        "    ci.sku_name,\n" +
                        "    ci.cart_price,\n" +
                        "    ci.sku_num,\n" +
                        "    ci.create_time,\n" +
                        "    ci.source_type,\n" +
                        "    dic.dic_name\n" +
                        "FROM cart_info ci\n" +
                        "JOIN base_dic FOR SYSTEM_TIME AS OF ci.proc_time AS dic ON ci.source_type = dic.dic_code"
        );
//        tableEnv.createTemporaryView("dwd_cart_info", dwdCartInfo);
        tableEnv.toDataStream(dwdCartInfo).print("dwdCartInfo");

        // 6.将加购数据写入kafka
        tableEnv.executeSql(
                "CREATE TABLE IF NOT EXISTS dwd_cart_info (\n" +
                        "    id              STRING  COMMENT '编号',\n" +
                        "    user_id         STRING  COMMENT '用户id',\n" +
                        "    sku_id          STRING  COMMENT '商品id',\n" +
                        "    sku_name        STRING  COMMENT '商品名称',\n" +
                        "    cart_price      STRING  COMMENT '放入购物车时商品价格',\n" +
                        "    sku_num         STRING  COMMENT '商品数量',\n" +
                        "    create_time     STRING  COMMENT '创建时间',\n" +
                        "    source_type     STRING  COMMENT '来源类型',\n" +
                        "    dic_name        STRING  COMMENT '编码名称'\n" +
                        ")" + FlinkUtil.getKafkaSinkDdl("dwd_cart_info")
        );
        // 两种写法,创不创建视图都可以
//        tableEnv.executeSql("INSERT INTO dwd_cart_info SELECT * FROM dwd_cart_info");
        tableEnv.executeSql("INSERT INTO dwd_cart_info SELECT * FROM " + dwdCartInfo);

        // 启动任务
        env.execute("DwdCartInfo");
    }
}
