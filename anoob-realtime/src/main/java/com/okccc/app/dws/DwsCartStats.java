package com.okccc.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.CartStats;
import com.okccc.util.ClickHouseUtil;
import com.okccc.util.DateUtil;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
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
 * @Date: 2023/7/3 17:15:27
 * @Desc: 加购独立用户数(DataStream - KafkaSource - State - Window - JdbcSink)
 *
 * -- clickhouse建表语句
 * create table if not exists dws_cart_stats (
 *     stt            DateTime  comment '窗口开始时间',
 *     edt            DateTime  comment '窗口结束时间',
 *     cart_uv_cnt    UInt32    comment '加购独立用户数',
 *     ts             UInt64    comment '数据写入时间'
 * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt,edt);
 */
public class DwsCartStats {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层加购数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_cart_info", "dws_cart_stats_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "login");

        // 3.将数据格式转换成JSON
        SingleOutputStreamOperator<JSONObject> mapStream = dataStream.map(JSON::parseObject);

        // 4.独立用户统计,状态编程
        SingleOutputStreamOperator<CartStats> filterStream = mapStream
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((element, recordTimestamp) -> DateUtil.parseDateTimeToUnix(element.getString("create_time")))
                )
                // 按照uid分组
                .keyBy(r -> r.getString("user_id"))
                // 核心业务逻辑：将符合条件的JSONObject转换成CartStats
                .flatMap(new RichFlatMapFunction<JSONObject, CartStats>() {
                    // 声明状态变量,记录上次加购日期
                    private ValueState<String> lastCartAddDate;

                    @Override
                    public void open(Configuration parameters) {
                        // 初始化状态变量
                        lastCartAddDate = getRuntimeContext().getState(FlinkUtil.setStateTtl("cart-add", 1));
                    }

                    @Override
                    public void flatMap(JSONObject value, Collector<CartStats> out) throws Exception {
                        // 获取上次加购日期和当前加购日期
                        String lastDate = lastCartAddDate.value();
                        String currentDate = value.getString("create_time").split(" ")[0];

                        if (lastDate == null || !lastDate.equals(currentDate)) {
                            lastCartAddDate.update(currentDate);
                            out.collect(new CartStats("", "", 1L, null));
                        }
                    }
                });

        // 5.开窗、聚合
        SingleOutputStreamOperator<CartStats> loginStream = filterStream
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(10)))
                .reduce(
                        // 先增量聚合
                        new ReduceFunction<CartStats>() {
                            @Override
                            public CartStats reduce(CartStats value1, CartStats value2) throws Exception {
                                // 将度量值两两相加
                                value1.setCartAddUvCnt(value1.getCartAddUvCnt() + value2.getCartAddUvCnt());
                                return value1;
                            }
                        },
                        // 再全窗口处理
                        new AllWindowFunction<CartStats, CartStats, TimeWindow>() {
                            @Override
                            public void apply(TimeWindow window, Iterable<CartStats> values, Collector<CartStats> out) throws Exception {
                                // 迭代器只有一个元素,就是上面增量聚合的结果
                                CartStats cartStats = values.iterator().next();
                                // 补充窗口信息
                                cartStats.setStt(DateUtil.parseUnixToDateTime(window.getStart()));
                                cartStats.setEdt(DateUtil.parseUnixToDateTime(window.getEnd()));
                                // 修改统计时间
                                cartStats.setTs(System.currentTimeMillis());
                                // 收集结果往下游发送
                                out.collect(cartStats);
                            }
                        }
                );

        // 6.将数据写入ClickHouse
        loginStream.addSink(
                ClickHouseUtil.getJdbcSink("INSERT INTO realtime.dws_cart_stats VALUES(?,?,?,?)"));

        // 7.启动任务
        env.execute("DwsCartStats");
    }
}
