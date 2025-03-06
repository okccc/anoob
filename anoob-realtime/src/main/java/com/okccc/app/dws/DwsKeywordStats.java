package com.okccc.app.dws;

import com.okccc.func.SplitFunction;
import com.okccc.app.bean.KeywordStats;
import com.okccc.util.ClickHouseUtil;
import com.okccc.util.FlinkUtil;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * @Author: okccc
 * @Date: 2023/6/16 10:45:33
 * @Desc: 搜索关键词主题统计(FlinkSQL - KafkaSource - UDTF - Window - JdbcSink)
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/concepts/time_attributes/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/functions/systemfunctions/#temporal-functions
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/functions/udfs/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/sql/queries/window-tvf/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/table/sql/queries/window-agg/
 *
 * Temporal Functions
 * TO_TIMESTAMP(str1[, str2])：Converts date time string str1 with format str2 (by default: 'yyyy-MM-dd HH:mm:ss') under the 'UTC+0' time zone to a timestamp.
 * TO_TIMESTAMP_LTZ(numeric, precision)：Converts a epoch seconds or epoch milliseconds to a TIMESTAMP_LTZ, the valid precision is 0 or 3.
 * DATE_FORMAT(timestamp, string)：Converts timestamp to a value of string in the format specified by the date format string. The format string is compatible with Java’s SimpleDateFormat.
 *
 * Windowing TVFs (Windowing table-valued functions)
 * Flink supports TUMBLE, HOP and CUMULATE types of window aggregations.
 * In streaming mode, the time attribute field of a window tvf must be on either event or processing time attributes.
 * In batch mode, the time attribute field of a window tvf must be an attribute of type TIMESTAMP or TIMESTAMP_LTZ.
 *
 * -- clickhouse建表语句(表字段和实体类属性要对应)
 * create table if not exists dws_keyword_stats (
 *     stt        DateTime  comment '窗口开始时间',
 *     edt        DateTime  comment '窗口结束时间',
 *     source     String    comment '关键词来源',
 *     keyword    String    comment '搜索关键词',
 *     cnt        UInt32    comment '统计次数',
 *     ts         UInt64    comment '数据写入时间'
 * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by(stt,edt,keyword);
 */
public class DwsKeywordStats {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境和表环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);

        // 2.读取dwd层日志数据,并创建动态表
        // {"common":{...},"page":{"page_id":"home","item":"小米","item_type":"keyword"},"displays":[{...}],"ts":1641971089000}
        tableEnv.executeSql(
                "CREATE TABLE dwd_page_log (\n" +
                "    page    MAP<String, String>,\n" +
                "    ts      BIGINT,\n" +
                "    rt      AS TO_TIMESTAMP_LTZ(ts, 3),\n" +
                "    WATERMARK FOR rt AS rt - INTERVAL '3' SECOND\n" +  // 窗口操作要指定水位线策略
                ")" + FlinkUtil.getKafkaSourceDdl("dwd_page_log", "dws_keyword_stats_g")
        );

        // 3.从动态表过滤搜索数据
        Table searchTable = tableEnv.sqlQuery(
                "SELECT\n" +
                "    page['item'] item,\n" +
                "    rt\n" +
                "FROM dwd_page_log\n" +
                "WHERE page['last_page_id'] = 'search'\n" +
                "AND page['item_type'] = 'keyword' AND page['item'] is not null"
        );
        tableEnv.createTemporaryView("search_table", searchTable);

        // 4.注册udtf
        tableEnv.createTemporarySystemFunction("SplitFunction", SplitFunction.class);

        // 5.使用udtf拆分搜索关键词,item -> word是一对多关系,这里的word是SplitFunction函数指定的
        Table splitTable = tableEnv.sqlQuery(
                "SELECT\n" +
                "    word,\n" +
                "    rt\n" +
                "FROM search_table, LATERAL TABLE(SplitFunction(item))"
        );
        tableEnv.createTemporaryView("split_table", splitTable);

        // 6.对动态表进行分组、开窗、聚合
        Table resultTable = tableEnv.sqlQuery(
                "SELECT\n" +
                "    DATE_FORMAT(window_start, 'yyyy-MM-dd HH:mm:ss') as stt,\n" +
                "    DATE_FORMAT(window_end, 'yyyy-MM-dd HH:mm:ss') as edt,\n" +
                "    'SEARCH' source,\n" +
                "    word keyword,\n" +
                "    count(*) cnt,\n" +
                "    UNIX_TIMESTAMP() * 1000 as ts\n" +
                "FROM TABLE(\n" +
                "    TUMBLE(TABLE split_table, DESCRIPTOR(rt), INTERVAL '10' SECONDS))\n" +  // 窗口大小视具体业务情况而定
                "GROUP BY window_start,window_end,word"
        );

        // 另一种写法
        Table resultTable1 = tableEnv.sqlQuery(
                "SELECT\n" +
                "    DATE_FORMAT(TUMBLE_START(rt, INTERVAL '10' SECONDS),'yyyy-MM-dd HH:mm:ss') as stt,\n" +
                "    DATE_FORMAT(TUMBLE_END(rt, INTERVAL '10' SECONDS),'yyyy-MM-dd HH:mm:ss') as edt,\n" +
                "    'SEARCH' source,\n" +
                "    word keyword,\n" +
                "    count(*) cnt,\n" +
                "    UNIX_TIMESTAMP() * 1000 as ts\n" +
                "FROM split_table\n" +
                "GROUP BY TUMBLE(rt, INTERVAL '10' SECONDS),word"
        );

        // 7.将动态表再转换为DataStream
        DataStream<KeywordStats> keywordStream = tableEnv.toDataStream(resultTable, KeywordStats.class);
        keywordStream.print("keyword");

        // 8.将流数据写入ClickHouse
        keywordStream.addSink(
                ClickHouseUtil.getJdbcSink("INSERT INTO realtime.dws_keyword_stats values(?,?,?,?,?,?)"));

        // 9.启动任务
        env.execute("DwsKeywordStats");
    }
}
