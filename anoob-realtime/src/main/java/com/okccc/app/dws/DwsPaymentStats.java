package com.okccc.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.PaymentStats;
import com.okccc.util.ClickHouseUtil;
import com.okccc.util.DateUtil;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.io.IOException;
import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2023/7/3 18:09:06
 * @Desc: 支付独立用户数和首次支付用户数(DataStream - kafkaSource - State & Timer - Window - JdbcSink)
 *
 * 业务背景
 * 当前需求数据源是dwd_payment_info,该数据由payment_info、dwd_order_detail、base_dic三张表inner join获得,此过程不会产生重复数据
 * dwd_order_detail是多个表left join而来
 *
 * 数据重复问题(难点)
 * left join会形成回撤流,导致生成的数据存在null值(直接过滤),并且相同主键的数据可能会有多条(需要去重)
 * order_pre_process> +I[81816, 1001, 3, xiaomi, 5999.0, null, 2023-07-12 11:20:45]
 * order_pre_process> -D[81816, 1001, 3, xiaomi, 5999.0, null, 2023-07-12 11:20:46]
 * order_pre_process> +I[81816, 1001, 3, xiaomi, 5999.0, 3, 2023-07-12 11:20:46]
 * 分析回撤流生成过程发现,字段内容完整的数据生成时间一定晚于不完整的数据,要确保统计结果准确性,应保留字段内容最全的数据即生成时间最晚的数据
 * 方案1：按唯一键分组,声明状态并注册定时器,每来一条数据就和状态值比较生成时间,保留晚的那条更新到状态,定时器触发就输出状态中的数据(推荐)
 * 方案2：按唯一键分组,开窗,在窗口闭合前对所有数据的生成时间排序,将最晚的那条发送到下游,排序操作要保存所有数据很消耗内存(不推荐)
 * 方案3：支付和下单需求都用不到left join右表的字段,那么即便右表的字段值为null也无所谓,所以只保留第一条数据输出即可(推荐)
 *
 * 其实当前支付需求可以不对left join回撤数据去重,即使主键id有多条重复数据也无所谓,因为这些重复数据都属于同一个用户
 * 统计独立用户数时也会做去重,但是下单需求要统计总金额和减免金额,就必须先对回撤数据去重,不然金额会被多次重复累加导致结果错误
 *
 * -- clickhouse建表语句
 * create table if not exists dws_payment_stats (
 *     stt            DateTime  comment '窗口开始时间',
 *     edt            DateTime  comment '窗口结束时间',
 *     pay_uv_cnt     UInt32    comment '支付成功独立用户数',
 *     pay_new_cnt    UInt32    comment '首次支付用户数',
 *     ts             UInt64    comment '数据写入时间'
 * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt,edt);
 */
public class DwsPaymentStats {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层支付数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_payment_detail", "dws_payment_stats_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "payment");

        // 3.将数据格式转换成JSON
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String value, Collector<JSONObject> out) {
                try {
                    JSONObject jsonObject = JSON.parseObject(value);
                    out.collect(jsonObject);
                } catch (Exception e) {
                    // 上游数据有left join时会生成null值,需要过滤
//                    System.out.println(">>>" + value);
                }
            }
        });

        // 4.保留主键order_detail_id的最新数据,状态编程(此处演示方案1保留最后一条)
        SingleOutputStreamOperator<JSONObject> fixedStream = jsonStream
                // 按照主键(唯一键)分组
                .keyBy(r -> r.getString("id"))
                // 涉及定时器操作用process
                .process(new KeyedProcessFunction<String, JSONObject, JSONObject>() {
                    // 声明状态变量,记录最新数据
                    private ValueState<JSONObject> latestData;

                    @Override
                    public void open(Configuration parameters) {
                        // 初始化状态变量,这里不设置ttl,后面定时器触发时会手动清空状态
                        latestData = getRuntimeContext().getState(new ValueStateDescriptor<>("latest", JSONObject.class));
                    }

                    @Override
                    public void processElement(JSONObject value, KeyedProcessFunction<String, JSONObject, JSONObject>.Context ctx, Collector<JSONObject> out) throws Exception {
                        // 获取状态中的数据
                        JSONObject jsonObject = latestData.value();

                        // 判断状态是否为空
                        if (jsonObject == null) {
                            latestData.update(value);
                            // 注册5秒后的定时器,和数据乱序程度保持一致
                            ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + 5000L);
                        } else {
                            // 不为空就要比较两条数据的生成时间
                            String stateTs = jsonObject.getString("crt");
                            String currentTs = value.getString("crt");
                            int diff = DateUtil.compare(stateTs, currentTs);
                            // 将后来的数据更新到状态
                            if (diff != 1) {
                                latestData.update(value);
                            }
                        }
                    }

                    @Override
                    public void onTimer(long timestamp, KeyedProcessFunction<String, JSONObject, JSONObject>.OnTimerContext ctx, Collector<JSONObject> out) throws Exception {
                        // 定时器触发,输出数据并清空状态
                        out.collect(latestData.value());
                        latestData.clear();
                    }
                });

        // 5.独立支付用户数,状态编程
        SingleOutputStreamOperator<PaymentStats> filterStream = fixedStream
                // 开窗操作要设置水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((element, recordTimestamp) -> DateUtil.parseDateTimeToUnix(element.getString("callback_time")))
                )
                // 按照user_id分组
                .keyBy(r -> r.getString("user_id"))
                // 核心业务逻辑：将符合条件的JSONObject转换成PaymentStats
                .flatMap(new RichFlatMapFunction<JSONObject, PaymentStats>() {
                    // 声明状态变量,记录上次支付日期
                    private ValueState<String> lastPaymentDate;

                    @Override
                    public void open(Configuration parameters) {
                        // 初始化状态变量
                        lastPaymentDate = getRuntimeContext().getState(FlinkUtil.setStateTtl("payment", 1));
                    }

                    @Override
                    public void flatMap(JSONObject value, Collector<PaymentStats> out) throws IOException {
                        // 获取上次支付日期和当前支付日期
                        String lastDate = lastPaymentDate.value();
                        String currentDate = value.getString("callback_time").split(" ")[0];

                        // 支付独立用户数和首次支付用户数
                        long payUvCnt = 0;
                        long payNewCnt = 0;

                        // 判断状态是否为空
                        if (lastDate == null) {
                            payUvCnt = 1;
                            payNewCnt = 1;
                            lastPaymentDate.update(currentDate);
                        } else if (!lastDate.equals(currentDate)) {
                            payUvCnt = 1;
                            lastPaymentDate.update(currentDate);
                        }

                        // 度量值不为0就往下游发送,准备做累加
                        if (payUvCnt != 0) {
                            out.collect(new PaymentStats("", "", payUvCnt, payNewCnt, null));
                        }
                    }
                });

        // 6.开窗、聚合
        SingleOutputStreamOperator<PaymentStats> payStream = filterStream
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(10)))
                .reduce(
                        // 先增量聚合
                        new ReduceFunction<PaymentStats>() {
                            @Override
                            public PaymentStats reduce(PaymentStats value1, PaymentStats value2) {
                                // 将度量值两两相加
                                value1.setPayUvCnt(value1.getPayUvCnt() + value2.getPayUvCnt());
                                value1.setPayNewCnt(value1.getPayNewCnt() + value2.getPayNewCnt());
                                return value1;
                            }
                        },
                        // 再全窗口处理
                        new AllWindowFunction<PaymentStats, PaymentStats, TimeWindow>() {
                            @Override
                            public void apply(TimeWindow window, Iterable<PaymentStats> values, Collector<PaymentStats> out) {
                                // 迭代器只有一个元素,就是上面增量聚合的结果
                                PaymentStats paymentStats = values.iterator().next();
                                // 补充窗口区间
                                paymentStats.setStt(DateUtil.parseUnixToDateTime(window.getStart()));
                                paymentStats.setEdt(DateUtil.parseUnixToDateTime(window.getEnd()));
                                // 修改统计时间
                                paymentStats.setTs(System.currentTimeMillis());
                                // 收集结果往下游发送
                                out.collect(paymentStats);
                            }
                        }
                );

        // 7.将数据写入ClickHouse
        payStream.addSink(
                ClickHouseUtil.getJdbcSink("INSERT INTO realtime.dws_payment_stats VALUES(?,?,?,?,?)"));

        // 8.启动任务
        env.execute("DwsPaymentStats");
    }
}
