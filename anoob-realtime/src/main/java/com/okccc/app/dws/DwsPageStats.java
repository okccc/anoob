package com.okccc.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.PageStats;
import com.okccc.util.ClickHouseUtil;
import com.okccc.util.DateUtil;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2023/6/28 16:39:47
 * @Desc: 首页和商品详情页独立访客数(DataStream - KafkaSource - State - Window - JdbcSink)
 *
 * -- clickhouse建表语句
 * create table if not exists dws_page_stats (
 *     stt                   DateTime  comment '窗口开始时间',
 *     edt                   DateTime  comment '窗口结束时间',
 *     home_uv_cnt           UInt32    comment '首页独立访客数',
 *     good_detail_uv_cnt    UInt32    comment '商品详情页独立访客数',
 *     ts                    UInt64    comment '数据写入时间'
 * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt,edt);
 */
public class DwsPageStats {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层日志数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_page_log", "dws_page_stats_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "page");

        // 3.将数据格式转换成JSON
        // {"common":{"mid":"mid_12"...}, "page":{"page_id":"cart", "last_page_id":"home"...}, "ts":1634284695000}
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String value, Collector<JSONObject> out) {
                JSONObject jsonObject = JSON.parseObject(value);
                String pageId = jsonObject.getJSONObject("page").getString("page_id");
                // 筛选首页和商品详情页数据
                if ("home".equals(pageId) || "good_detail".equals(pageId)) {
                    out.collect(jsonObject);
                }
            }
        });

        // 4.独立访客统计,状态编程
        SingleOutputStreamOperator<PageStats> filterStream = jsonStream
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((jsonObject, l) -> jsonObject.getLong("ts"))
                )
                // 按照mid分组
                .keyBy(r -> r.getJSONObject("common").getString("mid"))
                // 核心业务逻辑：将符合条件的JSONObject转换成PageStats,方便后面做开窗聚合
                .flatMap(new RichFlatMapFunction<JSONObject, PageStats>() {
                    // 声明状态变量,记录设备上次访问日期
                    private ValueState<String> homeLastVisitDate;
                    private ValueState<String> goodDetailLastVisitDate;

                    @Override
                    public void open(Configuration parameters) {
                        // 初始化状态变量
                        homeLastVisitDate = getRuntimeContext().getState(FlinkUtil.setStateTtl("home", 1));
                        goodDetailLastVisitDate = getRuntimeContext().getState(FlinkUtil.setStateTtl("good_detail", 1));
                    }

                    @Override
                    public void flatMap(JSONObject value, Collector<PageStats> out) throws Exception {
                        // 获取上次访问日期和当前访问日期
                        String homeLastDate = homeLastVisitDate.value();
                        String goodDetailLastDate = goodDetailLastVisitDate.value();
                        String currentDate = DateUtil.parseUnixToDateTime(value.getLong("ts"));

                        // 首页和商品详情页独立访客数
                        long homeUvCnt = 0;
                        long goodDetailUvCnt = 0;

                        // 获取页面
                        String pageId = value.getJSONObject("page").getString("page_id");
                        // 判断是首页还是商品详情页
                        if ("home".equals(pageId)) {
                            if (homeLastDate == null || !homeLastDate.equals(currentDate)) {
                                homeUvCnt = 1;
                                homeLastVisitDate.update(currentDate);
                            }
                        } else {
                            if (goodDetailLastDate == null || !goodDetailLastDate.equals(currentDate)) {
                                goodDetailUvCnt = 1;
                                goodDetailLastVisitDate.update(currentDate);
                            }
                        }

                        // 任意度量值不为0就往下游发送,准备做累加
                        if (homeUvCnt != 0 || goodDetailUvCnt != 0) {
                            out.collect(new PageStats("", "", homeUvCnt, goodDetailUvCnt, null));
                        }
                    }
                });

        // 5.开窗、聚合
        SingleOutputStreamOperator<PageStats> pageViewStream = filterStream
                // KeyedStream -> window(), SingleOutputStreamOperator -> windowAll()
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(10)))
                // 按照累加器思想进行滚动聚合时,如果输入输出类型一致就用reduce,输入输出类型不一致就用aggregate
                .reduce(
                        // 先增量聚合
                        new ReduceFunction<PageStats>() {
                            @Override
                            public PageStats reduce(PageStats value1, PageStats value2) {
                                // 将度量值两两相加
                                value1.setHomeUvCnt(value1.getHomeUvCnt() + value2.getHomeUvCnt());
                                value1.setGoodDetailUvCnt(value1.getGoodDetailUvCnt() + value2.getGoodDetailUvCnt());
                                return value1;
                            }
                        },
                        // 再窗口处理,WindowedStream -> ProcessWindowFunction, AllWindowedStream -> AllWindowFunction
                        new AllWindowFunction<PageStats, PageStats, TimeWindow>() {
                            @Override
                            public void apply(TimeWindow window, Iterable<PageStats> values, Collector<PageStats> out) {
                                // 迭代器只有一个元素,就是上面增量聚合的结果
                                PageStats pageStats = values.iterator().next();
                                // 补充窗口区间
                                pageStats.setStt(DateUtil.parseUnixToDateTime(window.getStart()));
                                pageStats.setEdt(DateUtil.parseUnixToDateTime(window.getEnd()));
                                // 修改统计时间
                                pageStats.setTs(System.currentTimeMillis());
                                // 收集结果往下游发送
                                out.collect(pageStats);
                            }
                        }
                );

        // 6.将数据写入Clickhouse
        pageViewStream.addSink(
                ClickHouseUtil.getJdbcSink("INSERT INTO realtime.dws_page_stats VALUES(?,?,?,?,?)"));

        // 7.启动任务
        env.execute("DwsPageStats");
    }
}
