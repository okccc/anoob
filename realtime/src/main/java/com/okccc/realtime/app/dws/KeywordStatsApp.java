package com.okccc.realtime.app.dws;

import com.okccc.realtime.app.func.KeywordUDTF;
import com.okccc.realtime.bean.KeywordStats;
import com.okccc.realtime.common.MyConstant;
import com.okccc.realtime.util.ClickHouseUtil;
import com.okccc.realtime.util.MyFlinkUtil;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * @Author: okccc
 * @Date: 2022/1/13 11:00 上午
 * @Desc: flink sql统计关键词主题宽表
 * https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/dev/table/functions/udfs/
 */
public class KeywordStatsApp {
    public static void main(String[] args) throws Exception {
        /*
         * ==========================================================
         *         | 搜索关键词	 | dwd | 大屏展示 | dwd_page_log直接计算
         * keyword | 点击商品关键词 | dws | 大屏展示 | 商品主题下单再次聚合
         *         | 下单商品关键词 | dws | 大屏展示 | 商品主题下单再次聚合
         * ==========================================================
         *
         * -- clickhouse建表语句
         * create table keyword_stats (
         *   stt        DateTime,
         *   edt        DateTime,
         *   keyword    String,
         *   ct         UInt64,
         *   source     String,
         *   ts         UInt64
         * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by(stt,edt,keyword);
         */

        // 1.创建流处理环境和表执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        // 2.注册自定义的udtf函数
        tableEnv.createTemporarySystemFunction("ik_analyse", KeywordUDTF.class);

        // 3.获取kafka数据,并创建相应动态表
        String topic = "dwd_page_log";
        String groupId = "keyword_stats_app_group";
        /* {
         *     "common":{...},
         *     "page":{
         *         "page_id":"home",
         *         "item":"iphone11",
         *         "during_time":6421
         *     },
         *     "displays":[{},{}...],
         *     "ts":1641971089000
         * }
         */
        tableEnv.executeSql(
                "create table if not exists page_log (\n" +
                        "    common    MAP<String, String>,\n" +
                        "    page      MAP<String, String>,\n" +
                        "    ts        BIGINT,\n" +
                        "    rowtime as TO_TIMESTAMP(FROM_UNIXTIME(ts/1000, 'yyyy-MM-dd HH:mm:ss')),\n" +  // 提取时间戳生成水位线
                        "    WATERMARK FOR rowtime as rowtime - INTERVAL '3' SECOND\n" +
                        ") WITH (" + MyFlinkUtil.getKafkaDDL(topic, groupId) + ")"
        );

        // 4.从动态表中过滤出表示搜索行为的记录
        Table table = tableEnv.sqlQuery(
                "select page['item'] word,rowtime from page_log where page['page_id']='good_list' and page['item'] is not null"
        );

        // 5.使用udtf对搜索关键词进行拆分
        // "SELECT rowtime, keyword FROM " + fullwordTable + ", LATERAL TABLE(ik_analyze(fullword)) AS T(keyword)"
        Table keywordTable = tableEnv.sqlQuery(
                "select rowtime,keyword from " + table + ",LATERAL TABLE(ik_analyse(word)) as T(keyword)"
        );

        // 6.对动态表进行分组/开窗/聚合
        Table resTable = tableEnv.sqlQuery(
                "select\n" +
                "    DATE_FORMAT(TUMBLE_START(rowtime, INTERVAL '10' SECOND),'yyyy-MM-dd HH:mm:ss') as stt,\n" +
                "    DATE_FORMAT(TUMBLE_END(rowtime, INTERVAL '10' SECOND),'yyyy-MM-dd HH:mm:ss') as edt,\n" +
                "    keyword,\n" +
                "    count(*) ct,\n" +
                "    '" + MyConstant.KEYWORD_SEARCH + "' source,\n" +
                "    UNIX_TIMESTAMP() * 1000 as ts\n" +
                "from " + keywordTable + "\n" +
                "group by TUMBLE(rowtime, INTERVAL '10' SECOND),keyword"
        );

        // 7.将动态表再转换成DataStream
        DataStream<KeywordStats> keywordDataStream = tableEnv.toAppendStream(resTable, KeywordStats.class);
        keywordDataStream.print("keywordStats");

        // 8.将流数据写入clickhouse
        keywordDataStream.addSink(
                ClickHouseUtil.getJdbcSinkBySchema("insert into keyword_stats values(?,?,?,?,?,?)"));

        // 启动任务
        env.execute();
    }
}
