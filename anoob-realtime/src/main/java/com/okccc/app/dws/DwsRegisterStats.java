package com.okccc.app.dws;

import com.okccc.app.bean.RegisterStats;
import com.okccc.util.ClickHouseUtil;
import com.okccc.util.DateUtil;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
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
 * @Date: 2023/7/3 16:51:42
 * @Desc: 注册数统计(DataStream - KafkaSource - Window - JdbcSink)
 *
 * -- clickhouse建表语句
 * create table if not exists dws_register_stats (
 *     stt             DateTime  comment '窗口开始时间',
 *     edt             DateTime  comment '窗口结束时间',
 *     register_cnt    UInt32    comment '注册用户数',
 *     ts              UInt64    comment '数据写入时间'
 * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt,edt);
 */
public class DwsRegisterStats {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层注册数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_register_info", "dws_register_stats_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "login");

        // 3.转换数据结构
        SingleOutputStreamOperator<RegisterStats> mapStream = dataStream
                .map(new MapFunction<String, RegisterStats>() {
                    @Override
                    public RegisterStats map(String value) {
                        return new RegisterStats("", "", 1L, null);
                    }
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<RegisterStats>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((element, recordTimestamp) -> element.getTs())
                );

        // 4.开窗、聚合
        SingleOutputStreamOperator<RegisterStats> registerStream = mapStream
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(10)))
                .reduce(
                        // 先增量聚合
                        new ReduceFunction<RegisterStats>() {
                            @Override
                            public RegisterStats reduce(RegisterStats value1, RegisterStats value2) {
                                // 将度量值两两相加
                                value1.setRegisterCnt(value1.getRegisterCnt() + value2.getRegisterCnt());
                                return value1;
                            }
                        },
                        // 再窗口处理
                        new AllWindowFunction<RegisterStats, RegisterStats, TimeWindow>() {
                            @Override
                            public void apply(TimeWindow window, Iterable<RegisterStats> values, Collector<RegisterStats> out) {
                                // 迭代器只有一个元素,就是上面增量聚合的结果
                                RegisterStats registerStats = values.iterator().next();
                                // 补充窗口区间
                                registerStats.setStt(DateUtil.parseUnixToDateTime(window.getStart()));
                                registerStats.setEdt(DateUtil.parseUnixToDateTime(window.getEnd()));
                                // 修改统计时间
                                registerStats.setTs(System.currentTimeMillis());
                                // 收集结果往下游发送
                                out.collect(registerStats);
                            }
                        }
                );

        // 5.将数据写入ClickHouse
        registerStream.addSink(
                ClickHouseUtil.getJdbcSink("INSERT INTO realtime.dws_register_stats VALUES(?,?,?,?)"));

        // 6.启动任务
        env.execute("DwsRegisterStats");
    }
}
