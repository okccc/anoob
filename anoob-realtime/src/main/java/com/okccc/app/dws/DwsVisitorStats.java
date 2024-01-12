package com.okccc.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.VisitorStats;
import com.okccc.util.ClickHouseUtil;
import com.okccc.util.DateUtil;
import com.okccc.util.FlinkUtil;
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
 * @Author: okccc
 * @Date: 2023/6/22 15:00:20
 * @Desc: 访客主题统计(DataStream - KafkaSource - Union - Window - JdbcSink)
 *
 * dws层是将dwd层多个实时数据以主题的方式轻度聚合方便查询,主题表包含维度(分组)和度量(聚合)两部分
 * DataStream处理join操作不如FlinkSql方便,除了join还可以使用union进行数据整合,union多流合并的元素类型必须相同,所以要统一数据结构
 *
 * =========================================================
 *        |  pv    |  dwd  |  大屏展示  | dwd_page_log直接计算
 *        |  uv    |  dwd  |  大屏展示  | dwd_page_log过滤去重
 *  user  | 进入页面 |  dwd  |  大屏展示  | dwd_page_log行为判断
 *        | 跳出页面 |  dwd  |  大屏展示  | dwd_page_log行为判断
 *        | 连续访问时长 | dwd | 大屏展示 | dwd_page_log直接获取
 * =========================================================
 *
 * 维度数据：渠道、地区、版本、新老用户
 * 度量数据：PV、UV、进入页面、跳出页面、连续访问时长
 * 访客主题如果按照mid分组,记录的都是当前设备的行为,数据量很小聚合效果不明显,起不到数据压缩的作用,所以这里多弄了几个维度
 *
 * -- clickhouse建表语句
 * create table if not exists dws_visitor_stats (
 *     stt        DateTime  comment '窗口开始时间',
 *     edt        DateTime  comment '窗口结束时间',
 *     vc         String    comment '版本',
 *     ch         String    comment '渠道',
 *     ar         String    comment '地区',
 *     is_new     String    comment '新老用户标识',
 *     pv_ct      UInt64    comment '页面访问数',
 *     uv_ct      UInt64    comment '独立访客数',
 *     sv_ct      UInt64    comment '进入页面次数',
 *     uj_ct      UInt64    comment '跳出页面次数',
 *     dur_sum    UInt64    comment '持续访问时长',
 *     ts         UInt64    comment '数据写入时间'
 * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt,edt,vc,ch,ar,is_new);
 */
public class DwsVisitorStats {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层多个流数据
        String pvTopic = "dwd_page_log";
        String uvTopic = "dwd_unique_visitor";
        String ujTopic = "dwd_user_jump";
        String groupId = "dws_visitor_stats_g";
        DataStreamSource<String> pvStream = env.fromSource(FlinkUtil.getKafkaSource(pvTopic, groupId), WatermarkStrategy.noWatermarks(), "pv");
        DataStreamSource<String> uvStream = env.fromSource(FlinkUtil.getKafkaSource(uvTopic, groupId), WatermarkStrategy.noWatermarks(), "uv");
        DataStreamSource<String> ujStream = env.fromSource(FlinkUtil.getKafkaSource(ujTopic, groupId), WatermarkStrategy.noWatermarks(), "uj");

        // 3.核心业务逻辑：统一数据结构,将每条流都转换成VisitorStats,方便后面做分组开窗聚合
        SingleOutputStreamOperator<VisitorStats> pvStream02 = pvStream.map(new MapFunction<String, VisitorStats>() {
            @Override
            public VisitorStats map(String value) {
                // {"common":{"ar":"230000","uid":"9","os":"Android 11.0","ch":"360","is_new":"0","md":"Xiaomi 10 Pro ","mid":"mid_4","vc":"v2.1.134","ba":"Xiaomi"},"page":{"page_id":"home","during_time":17256},"ts":1687167009000}
                JSONObject jsonObject = JSON.parseObject(value);
                JSONObject common = jsonObject.getJSONObject("common");
                JSONObject page = jsonObject.getJSONObject("page");
                // 将dwd_page_log数据封装成VisitorStats
                VisitorStats visitorStats = new VisitorStats(
                        "",  // 此时还没有窗口信息,等分组开窗后再补充
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
                if (lastPageId == null || lastPageId.isEmpty()) {
                    // 不是跳转过来的说明是新页面,进入次数+1
                    visitorStats.setSvCnt(1L);
                }
                return visitorStats;
            }
        });

        SingleOutputStreamOperator<VisitorStats> uvStream02 = uvStream.map(new MapFunction<String, VisitorStats>() {
            @Override
            public VisitorStats map(String value) {
                // {"common":{"ar":"440000","uid":"10","os":"Android 9.0","ch":"360","is_new":"0","md":"Xiaomi 9","mid":"mid_18","vc":"v2.1.134","ba":"Xiaomi"},"page":{"page_id":"home","during_time":5467},"ts":1687166780000}
                JSONObject jsonObject = JSON.parseObject(value);
                JSONObject common = jsonObject.getJSONObject("common");
                // 将dwd_uniquer_visitor数据封装成VisitorStats
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
                        1L,
                        0L,
                        jsonObject.getLong("ts")
                );
            }
        });

        SingleOutputStreamOperator<VisitorStats> ujStream02 = ujStream.map(new MapFunction<String, VisitorStats>() {
            @Override
            public VisitorStats map(String value) {
                // {"common":{"ar":"110000","uid":"8","os":"Android 9.0","ch":"oppo","is_new":"0","md":"Xiaomi 9","mid":"mid_3","vc":"v2.1.134","ba":"Xiaomi"},"page":{"page_id":"home","during_time":12834},"ts":1687166785000}
                JSONObject jsonObject = JSON.parseObject(value);
                JSONObject common = jsonObject.getJSONObject("common");
                // 将dwd_user_jump数据封装成VisitorStats
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

        // 4.union多流合并
        DataStream<VisitorStats> unionStream = pvStream02.union(uvStream02, ujStream02);
        unionStream.print("union");

        // 5.分组、开窗、聚合
        SingleOutputStreamOperator<VisitorStats> visitorStream = unionStream
                // 窗口操作要指定水位线策略
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
                    public Tuple4<String, String, String, String> getKey(VisitorStats value) {
                        return Tuple4.of(value.getVc(), value.getCh(), value.getAr(), value.getIsNew());
                    }
                })
                // 开窗：每个分组都是独立窗口互不影响
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                // 聚合：按照累加器思想进行滚动聚合时,如果输入输出类型一致就用reduce,输入输出类型不一致就用aggregate
                .reduce(
                        // 先增量聚合
                        new ReduceFunction<VisitorStats>() {
                            @Override
                            public VisitorStats reduce(VisitorStats value1, VisitorStats value2) throws Exception {
                                // 按照维度分组后,将度量值两两相加
                                value1.setPvCnt(value1.getPvCnt() + value2.getPvCnt());
                                value1.setUvCnt(value1.getUvCnt() + value2.getUvCnt());
                                value1.setSvCnt(value1.getSvCnt() + value2.getSvCnt());
                                value1.setUjCnt(value1.getUjCnt() + value2.getUjCnt());
                                value1.setDurSum(value1.getDurSum() + value2.getDurSum());
                                return value1;
                            }
                        },
                        // 再窗口处理
                        new ProcessWindowFunction<VisitorStats, VisitorStats, Tuple4<String, String, String, String>, TimeWindow>() {
                            @Override
                            public void process(Tuple4<String, String, String, String> tuple4, Context context, Iterable<VisitorStats> elements, Collector<VisitorStats> out) {
                                // 迭代器只有一个元素,就是上面增量聚合的结果
                                VisitorStats visitorStats = elements.iterator().next();
                                // 补充窗口区间
                                visitorStats.setStt(DateUtil.parseUnixToDateTime(context.window().getStart()));
                                visitorStats.setEdt(DateUtil.parseUnixToDateTime(context.window().getEnd()));
                                // 修改统计时间
                                visitorStats.setTs(System.currentTimeMillis());
                                // 收集结果往下游发送
                                out.collect(visitorStats);
                            }
                        }
                );

        // 6.将数据写入Clickhouse,主题表只是轻度聚合,更细粒度的指标可以借助sql统计分析
        visitorStream.addSink(
                ClickHouseUtil.getJdbcSink("INSERT INTO realtime.dws_visitor_stats VALUES(?,?,?,?,?,?,?,?,?,?,?,?)"));

        // 7.启动任务
        env.execute("DwsVisitorStats");
    }
}
