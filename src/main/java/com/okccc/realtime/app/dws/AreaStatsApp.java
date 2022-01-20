package com.okccc.realtime.app.dws;

import com.okccc.realtime.bean.AreaStats;
import com.okccc.realtime.utils.ClickHouseUtil;
import com.okccc.realtime.utils.MyKafkaUtil;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

/**
 * Author: okccc
 * Date: 2022/1/6 3:46 下午
 * Desc: flink sql统计地区主题宽表
 * https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/connectors/table/kafka/
 * https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/dev/table/sql/create/
 */
public class AreaStatsApp {
    public static void main(String[] args) throws Exception {
        /*
         * ==========================================================
         *         |  pv	 |  dwd  |  多维分析	 | dwd_page_log直接计算
         * area    |  uv	 |  dwm  |  多维分析	 | dwd_page_log过滤去重
         *         |  下单	 |  dwm  |  大屏展示	 | 订单宽表
         * ==========================================================
         *
         * -- clickhouse建表语句
         * create table if not exists area_stats (
         *   stt               DateTime,
         *   edt               DateTime,
         *   province_id       UInt64,
         *   province_name     String,
         *   area_code         String,
         *   iso_code          String,
         *   iso_3166_2        String,
         *   order_count       UInt64,
         *   order_amount      Decimal64(2),
         *   ts                UInt64
         * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt,edt,province_id);
         */

        // 1.创建流处理环境和表执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);

        // 2.获取kafka数据,并根据topic中的字段创建相应动态表
        String topic = "dwm_order_wide";
        String groupId = "area_stats_app_group";
        /* {
         *     ...(省略若干字段)
         *     "order_id":35772,
         *     "split_total_amount":999,
         *     "province_3166_2_code":"CN-XJ",
         *     "province_area_code":"650000",
         *     "province_id":22,
         *     "province_iso_code":"CN-65",
         *     "province_name":"新疆",
         *     "create_time":"2022-01-12 15:54:26",
         *     ...(省略若干字段)
         * }
         */
        tableEnv.executeSql(
                "create table if not exists order_wide (\n" +
                        "    province_id             BIGINT,\n" +
                        "    province_name           STRING,\n" +
                        "    province_area_code      STRING,\n" +
                        "    province_iso_code       STRING,\n" +
                        "    province_3166_2_code    STRING,\n" +
                        "    order_id                STRING,\n" +
                        "    split_total_amount      DOUBLE,\n" +
                        "    create_time             STRING,\n" +
                        "    rowtime as TO_TIMESTAMP(create_time),\n" +  // 提取时间戳生成水位线
                        "    WATERMARK FOR rowtime as rowtime - INTERVAL '3' SECOND\n" +
                        ") WITH (" + MyKafkaUtil.getKafkaDDL(topic, groupId) + ")"
        );

        // 3.对动态表进行分组/开窗/聚合,和普通sql的区别主要在于开窗操作
        Table table = tableEnv.sqlQuery(
                "select\n" +
                "    DATE_FORMAT(TUMBLE_START(rowtime, INTERVAL '10' SECOND), 'yyyy-MM-dd HH:mm:ss') as stt,\n" +  // 补全窗口区间
                "    DATE_FORMAT(TUMBLE_END(rowtime, INTERVAL '10' SECOND), 'yyyy-MM-dd HH:mm:ss') as edt,\n" +
                "    province_id,\n" +
                "    province_name,\n" +
                "    province_area_code area_code,\n" +
                "    province_iso_code iso_code,\n" +
                "    province_3166_2_code iso_3166_2,\n" +
                "    count(distinct order_id) as order_count,\n" +
                "    sum(split_total_amount) as order_amount,\n" +
                "    UNIX_TIMESTAMP() * 1000 as ts\n" +  // 补全统计时间
                "from order_wide\n" +
                "group by TUMBLE(rowtime, INTERVAL '10' SECOND),\n" +  // 开一个10s的滚动窗口
                "         province_id,province_name,province_area_code,province_iso_code,province_3166_2_code"
        );

        // 4.将动态表再转换成DataStream,flink-sql目前只能写mysql,所以要先转换成流再往clickhouse写数据
        DataStream<AreaStats> areaStatsStream = tableEnv.toAppendStream(table, AreaStats.class);
        areaStatsStream.print("areaStats");

        // 5.将流数据写入clickhouse
        areaStatsStream.addSink(
                ClickHouseUtil.getJdbcSinkBySchema("insert into area_stats values(?,?,?,?,?,?,?,?,?,?)"));

        // 启动任务
        env.execute();
    }
}
