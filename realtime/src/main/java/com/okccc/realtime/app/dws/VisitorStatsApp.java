package com.okccc.realtime.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.bean.VisitorStats;
import com.okccc.realtime.util.ClickHouseUtil;
import com.okccc.realtime.util.DateUtil;
import com.okccc.realtime.util.MyFlinkUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * Author: okccc
 * Date: 2021/12/13 10:14 上午
 * Desc: 访客主题宽表
 */
public class VisitorStatsApp {
    public static void main(String[] args) throws Exception {
        /*
         * dws层将多个实时数据以主题的方式轻度聚合方便查询,主题表包含维度(分组)和度量(聚合)两部分数据
         * connect双流连接的元素类型可以不同,union多流合并的元素类型必须相同,所以要整合各个实时数据的字段统一生成主题宽表
         *
         * ==========================================================
         *         |  pv    |  dwd  |  大屏展示  | dwd_page_log直接计算
         *         |  uv    |  dwm  |  大屏展示  | dwd_page_log过滤去重
         * user    | 进入页面 |  dwd  |  大屏展示  | dwd_page_log行为判断
         *         | 跳出页面 |  dwm  |  大屏展示  | dwd_page_log行为判断
         *         | 连续访问时长 | dwd | 大屏展示 | dwd_page_log直接获取
         * ==========================================================
         *
         * 维度数据：渠道、地区、版本、新老用户
         * 度量数据：PV、UV、进入页面、跳出页面、连续访问时长
         * 访客主题如果按照mid分组,记录的都是当前设备的行为,数据量很小聚合效果不明显,起不到数据压缩的作用
         *
         * -- clickhouse建表语句
         * create table if not exists visitor_stats (
         *   stt            DateTime,
         *   edt            DateTime,
         *   vc             String,
         *   ch             String,
         *   ar             String,
         *   is_new         String,
         *   uv_ct          UInt64,
         *   pv_ct          UInt64,
         *   sv_ct          UInt64,
         *   uj_ct          UInt64,
         *   dur_sum        UInt64,
         *   ts             UInt64
         * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt,edt,is_new,vc,ch,ar);
         */

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // flink并行度和kafka分区数保持一致
        env.setParallelism(1);

        // 2.获取kafka数据
        String pageViewTopic = "dwd_page_log";
        String uniqueVisitorTopic = "dwm_unique_visitor";
        String userJumpDetailTopic = "dwm_user_jump_detail";
        String groupId = "visitor_stats_app_group";
        DataStreamSource<String> pvStream = env.addSource(MyFlinkUtil.getKafkaSource(pageViewTopic, groupId));
        DataStreamSource<String> uvStream = env.addSource(MyFlinkUtil.getKafkaSource(uniqueVisitorTopic, groupId));
        DataStreamSource<String> ujdStream = env.addSource(MyFlinkUtil.getKafkaSource(userJumpDetailTopic, groupId));
        // 打印测试
//        pvStream.print("pv");
//        uvStream.print("uv");
//        ujdStream.print("ujd");

        // 3.结构转换
        SingleOutputStreamOperator<VisitorStats> pvStatsStream = pvStream
                // 将pv流数据封装成访客主题实体类
                .map(new MapFunction<String, VisitorStats>() {
                    @Override
                    public VisitorStats map(String value) {
                        // {"common":{"ar":"","ch":"","vc":""...},"page":{...},"displays":[{},{}...],"ts":1639123347000}
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject common = jsonObject.getJSONObject("common");
                        JSONObject page = jsonObject.getJSONObject("page");
                        VisitorStats visitorStats = new VisitorStats(
                                "",
                                "",
                                common.getString("vc"),
                                common.getString("ch"),
                                common.getString("ar"),
                                common.getString("is_new"),
                                1L,
                                0L,
                                0L,
                                0L,
                                page.getLong("during_time"),
                                jsonObject.getLong("ts")
                        );
                        // 先判断是否从别的页面跳转过来
                        String lastPageId = page.getString("last_page_id");
                        if (lastPageId == null || lastPageId.length() == 0) {
                            // 不是跳转过来的说明是新页面,进入次数+1
                            visitorStats.setSv_ct(1L);
                        }
                        return visitorStats;
                    }
                });

        SingleOutputStreamOperator<VisitorStats> uvStatsStream = uvStream
                // 将uv流数据封装成访客主题实体类
                .map(new MapFunction<String, VisitorStats>() {
                    @Override
                    public VisitorStats map(String value) {
                        // {"common":{"ar":"","ch":"","vc":""...},"page":{...},"displays":[{},{}...],"ts":1639123347000}
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject common = jsonObject.getJSONObject("common");
                        return new VisitorStats(
                                "",
                                "",
                                common.getString("vc"),
                                common.getString("ch"),
                                common.getString("ar"),
                                common.getString("is_new"),
                                0L,
                                1L,
                                0L,
                                0L,
                                0L,
                                jsonObject.getLong("ts")
                        );
                    }
                });

        SingleOutputStreamOperator<VisitorStats> ujdStatsStream = ujdStream
                // 将ujd流数据封装成访客主题实体类
                .map(new MapFunction<String, VisitorStats>() {
                    @Override
                    public VisitorStats map(String value) {
                        // {"common":{"ar":"","ch":"","vc":""...},"page":{...},"displays":[{},{}...],"ts":1639123347000}
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject common = jsonObject.getJSONObject("common");
                        return new VisitorStats(
                                "",
                                "",
                                common.getString("vc"),
                                common.getString("ch"),
                                common.getString("ar"),
                                common.getString("is_new"),
                                0L,
                                0L,
                                0L,
                                1L,
                                0L,
                                jsonObject.getLong("ts")
                        );
                    }
                });

        // 4.合并多条流
        DataStream<VisitorStats> unionStream = pvStatsStream.union(uvStatsStream, ujdStatsStream);
        // 打印测试
//        unionStream.print("union");  // VisitorStats(stt=, edt=, vc=v2.1.132, ch=Appstore, ar=440000, is_new=1, uv_ct=0, pv_ct=1, sv_ct=0, uj_ct=0, dur_sum=8042, ts=1639120380000)

        // 5.分组/开窗/聚合
        SingleOutputStreamOperator<VisitorStats> visitorStatsStream = unionStream
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<VisitorStats>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner(new SerializableTimestampAssigner<VisitorStats>() {
                                    @Override
                                    public long extractTimestamp(VisitorStats element, long recordTimestamp) {
                                        return element.getTs();
                                    }
                                })
                )
                // 分组：按照版本/渠道/地区/新老访客4个维度进行分组,所以key是Tuple4类型
                .keyBy(new KeySelector<VisitorStats, Tuple4<String, String, String, String>>() {
                    @Override
                    public Tuple4<String, String, String, String> getKey(VisitorStats value) throws Exception {
                        return Tuple4.of(
                                value.getCh(),
                                value.getAr(),
                                value.getVc(),
                                value.getIs_new()
                        );
                    }
                })
                // 开窗：每个分组都是独立窗口互不影响
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                // 聚合：按照累加器思想进行滚动聚合时,如果输入输出类型一致就用reduce,输入输出类型不一致就用aggregate
                .reduce(
                        // 先增量聚合
                        new ReduceFunction<VisitorStats>() {
                            @Override
                            public VisitorStats reduce(VisitorStats value1, VisitorStats value2) {
                                // 按照维度分组后,将度量值进行两两相加
                                value1.setPv_ct(value1.getPv_ct() + value2.getPv_ct());
                                value1.setUv_ct(value1.getUv_ct() + value2.getUv_ct());
                                value1.setSv_ct(value1.getSv_ct() + value2.getSv_ct());
                                value1.setUj_ct(value1.getUj_ct() + value2.getUj_ct());
                                value1.setDur_sum(value1.getDur_sum() + value2.getDur_sum());
                                return value1;
                            }
                        },
                        // 再全窗口处理
                        new ProcessWindowFunction<VisitorStats, VisitorStats, Tuple4<String, String, String, String>, TimeWindow>() {
                            @Override
                            public void process(Tuple4<String, String, String, String> tuple4, Context context, Iterable<VisitorStats> elements, Collector<VisitorStats> out) {
                                // 获取窗口信息
                                long windowStart = context.window().getStart();
                                long windowEnd = context.window().getEnd();
                                // 遍历迭代器
                                for (VisitorStats visitorStats : elements) {
                                    // 补全窗口区间及统计时间
                                    visitorStats.setStt(DateUtil.parseUnixToDateTime(windowStart));
                                    visitorStats.setEdt(DateUtil.parseUnixToDateTime(windowEnd));
                                    visitorStats.setTs(System.currentTimeMillis());
                                    // 收集结果往下游发送
                                    out.collect(visitorStats);
                                }
                            }
                        }
                );
        // 打印测试
        visitorStatsStream.print("VisitStats");  // VisitorStats(stt=2021-12-10 17:47:20, edt=2021-12-10 17:47:30, vc=v2.1.134, ch=oppo, ar=500000, is_new=1, pv_ct=1, uv_ct=1, sv_ct=1, uj_ct=0, dur_sum=8947, ts=1639734453427)

        // 6.将聚合数据写入clickhouse,主题表只是轻度聚合,更细粒度的统计分析可以借助sql分析
        visitorStatsStream.addSink(
                ClickHouseUtil.getJdbcSinkBySchema("insert into visitor_stats values(?,?,?,?,?,?,?,?,?,?,?,?)"));

        // 启动任务
        env.execute();
    }
}
