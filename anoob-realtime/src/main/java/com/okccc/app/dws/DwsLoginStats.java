package com.okccc.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.LoginStats;
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
 * @Date: 2023/6/29 18:27:58
 * @Desc: 当日独立用户数和七日回流用户数(DataStream - KafkaSource - State - Window - JdbcSink)
 *
 * -- clickhouse建表语句
 * create table if not exists dws_login_stats (
 *     stt           DateTime  comment '窗口开始时间',
 *     edt           DateTime  comment '窗口结束时间',
 *     uv_cnt        UInt32    comment '当日独立用户数',
 *     back7_cnt     UInt32    comment '7日回流用户数',
 *     back15_cnt    UInt32    comment '15日回流用户数',
 *     back30_cnt    UInt32    comment '30日回流用户数',
 *     ts            UInt64    comment '数据写入时间'
 * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt,edt);
 */
public class DwsLoginStats {
    public static void main(String[] args) throws Exception {
        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层日志数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_page_log", "dws_login_stats_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "login");

        // 3.将数据格式转换成JSON
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String value, Collector<JSONObject> out) {
                JSONObject jsonObject = JSON.parseObject(value);
                String uid = jsonObject.getJSONObject("common").getString("uid");
                String lastPageId = jsonObject.getJSONObject("page").getString("last_page_id");
                // 筛选登录数据
                if (uid != null && (lastPageId == null || "login".equals(lastPageId))) {
                    out.collect(jsonObject);
                }
            }
        });

        // 4.独立用户和回流用户统计,状态编程
        SingleOutputStreamOperator<LoginStats> filterStream = jsonStream
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((element, recordTimestamp) -> element.getLong("ts"))
                )
                // 按照uid分组
                .keyBy(r -> r.getJSONObject("common").getString("uid"))
                // 核心业务逻辑：将符合条件的JSONObject转换成LoginStats
                .flatMap(new RichFlatMapFunction<JSONObject, LoginStats>() {
                    // 声明状态变量,记录上次登录日期
                    private ValueState<String> lastLoginDate;

                    @Override
                    public void open(Configuration parameters) {
                        // 初始化状态变量
                        lastLoginDate = getRuntimeContext().getState(FlinkUtil.setStateTtl("login", 30));
                    }

                    @Override
                    public void flatMap(JSONObject value, Collector<LoginStats> out) throws Exception {
                        // 获取上次登录日期和当前登录日期
                        String lastDate = lastLoginDate.value();
                        Long ts = value.getLong("ts");
                        String currentDate = DateUtil.parseUnixToDateTime(ts);

                        // 当日独立用户数和七日回流用户数
                        long uvCnt = 0;
                        long back7Cnt = 0;
                        long back15Cnt = 0;
                        long back30Cnt = 0;

                        if(lastDate == null) {
                            uvCnt = 1;
                            lastLoginDate.update(currentDate);
                        } else if (!lastDate.equals(currentDate)) {
                            uvCnt = 1;
                            lastLoginDate.update(currentDate);
                            // 计算本次和上次登陆时间差,判断是否是7/15/30日回流用户
                            Long lastTs = DateUtil.parseDateTimeToUnix(lastDate);
                            long day = (ts - lastTs) / (24 * 3600 * 1000L);
                            if (day >= 7 && day < 15) {
                                back7Cnt = 1;
                            } else if (day >= 15 && day < 30) {
                                back15Cnt = 1;
                            } else if (day >= 30) {
                                back30Cnt = 1;
                            }
                        }

                        // 度量值不为0就往下游发送,准备做累加
                        if(uvCnt != 0) {
                            out.collect(new LoginStats("", "", uvCnt, back7Cnt, back15Cnt, back30Cnt, null));
                        }
                    }
                });

        // 5.开窗、聚合
        SingleOutputStreamOperator<LoginStats> loginStream = filterStream
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(10)))
                .reduce(
                        // 先增量聚合
                        new ReduceFunction<LoginStats>() {
                            @Override
                            public LoginStats reduce(LoginStats value1, LoginStats value2) {
                                // 将度量值两两相加
                                value1.setUvCnt(value1.getUvCnt() + value2.getUvCnt());
                                value1.setBack7Cnt(value1.getBack7Cnt() + value2.getBack7Cnt());
                                value1.setBack15Cnt(value1.getBack15Cnt() + value2.getBack15Cnt());
                                value1.setBack30Cnt(value1.getBack30Cnt() + value2.getBack30Cnt());
                                return value1;
                            }
                        },
                        // 再窗口处理
                        new AllWindowFunction<LoginStats, LoginStats, TimeWindow>() {
                            @Override
                            public void apply(TimeWindow window, Iterable<LoginStats> values, Collector<LoginStats> out) {
                                // 迭代器只有一个元素,就是上面增量聚合的结果
                                LoginStats loginStats = values.iterator().next();
                                // 补充窗口区间
                                loginStats.setStt(DateUtil.parseUnixToDateTime(window.getStart()));
                                loginStats.setEdt(DateUtil.parseUnixToDateTime(window.getEnd()));
                                // 修改统计时间
                                loginStats.setTs(System.currentTimeMillis());
                                // 收集结果往下游发送
                                out.collect(loginStats);
                            }
                        }
                );

        // 6.将数据写入ClickHouse
        loginStream.addSink(
                ClickHouseUtil.getJdbcSink("INSERT INTO realtime.dws_login_stats VALUES(?,?,?,?,?,?,?)"));

        // 7.启动任务
        env.execute("DwsLoginStats");
    }
}
