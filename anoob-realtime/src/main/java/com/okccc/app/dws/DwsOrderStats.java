package com.okccc.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.OrderStats;
import com.okccc.util.ClickHouseUtil;
import com.okccc.util.DateUtil;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
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
 * @Date: 2023/7/17 17:17:11
 * @Desc: 下单独立用户数和首次下单用户数(DataStream - kafkaSource - State - Window - JdbcSink)
 *
 * 业务背景
 * 当前需求数据源是dwd_order_detail,该数据是多个表left join而来
 *
 * -- clickhouse建表语句
 * create table if not exists dws_order_stats (
 *     stt                             DateTime         comment '窗口开始时间',
 *     edt                             DateTime         comment '窗口结束时间',
 *     order_uv_cnt                    UInt32           comment '下单独立用户数',
 *     order_new_cnt                   UInt32           comment '首次下单用户数',
 *     order_origin_total_amount       Decimal(38, 20)  comment '下单原始总金额',
 *     order_split_activity_amount     Decimal(38, 20)  comment '下单活动减免金额',
 *     order_split_coupon_amount       Decimal(38, 20)  comment '下单优惠券减免金额',
 *     ts                              UInt64           comment '数据写入时间'
 * ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt, edt);
 *
 */
public class DwsOrderStats {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层下单数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_order_detail", "dws_order_stats_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "order");

        // 3.将数据格式转换成JSON
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String value, Collector<JSONObject> out) {
                try {
                    JSONObject jsonObject = JSON.parseObject(value);
                    out.collect(jsonObject);
                } catch (Exception e) {
                    // 上游数据有left join时会生成null值,需要过滤
                    System.out.println(">>>" + value);
                }
            }
        });

        // 4.主键id数据去重,状态编程(此处演示方案3保留第一条)
        SingleOutputStreamOperator<JSONObject> fixedStream = jsonStream
                // 按照主键(唯一键)分组
                .keyBy(r -> r.getString("id"))
                .filter(new RichFilterFunction<JSONObject>() {
                    // 声明状态变量,记录最新数据
                    private ValueState<JSONObject> latestData;

                    @Override
                    public void open(Configuration parameters) {
                        // 创建状态描述符
                        ValueStateDescriptor<JSONObject> stateDescriptor = new ValueStateDescriptor<>("is-exist", JSONObject.class);

                        // 设置状态存活时间
                        StateTtlConfig stateTtlConfig = new StateTtlConfig.Builder(org.apache.flink.api.common.time.Time.seconds(5))
                                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                                .build();
                        stateDescriptor.enableTimeToLive(stateTtlConfig);

                        // 初始化状态变量
                        latestData = getRuntimeContext().getState(stateDescriptor);
                    }

                    @Override
                    public boolean filter(JSONObject value) throws Exception {
                        // 获取状态数据
                        JSONObject jsonObject = latestData.value();

                        // 判断状态是否为空
                        if (jsonObject == null) {
                            latestData.update(value);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });

        // 5.独立下单用户数,状态编程
        SingleOutputStreamOperator<OrderStats> filterStream = fixedStream
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner((element, recordTimestamp) -> DateUtil.parseDateTimeToUnix(element.getString("create_time")))
                )
                // 按照user_id分组
                .keyBy(r -> r.getString("user_id"))
                // 核心业务逻辑：将JSONObject转换成OrderStats,因为要计算累加金额,肯定会输出一个结果,所以用map算子,比如一个用户下了好几单,独立用户数不变但是金额会累加
                .map(new RichMapFunction<JSONObject, OrderStats>() {
                    // 声明状态变量,记录上次下单日期
                    private ValueState<String> lastOrderDate;

                    @Override
                    public void open(Configuration parameters) {
                        // 初始化状态变量
                        lastOrderDate = getRuntimeContext().getState(FlinkUtil.setStateTtl("order", 1));
                    }

                    @Override
                    public OrderStats map(JSONObject value) throws Exception {
                        // 获取上次下单日期和当前下单日期
                        String lastDate = lastOrderDate.value();
                        String currentDate = value.getString("create_time").split(" ")[0];

                        // 下单独立用户数和首次下单用户数
                        long orderUvCnt = 0;
                        long orderNewCnt = 0;

                        // 判断状态是否为空
                        if (lastDate == null) {
                            orderUvCnt = 1;
                            orderNewCnt = 1;
                            lastOrderDate.update(currentDate);
                        } else if (!lastDate.equals(currentDate)) {
                            orderUvCnt = 1;
                            lastOrderDate.update(currentDate);
                        }

                        // 获取商品数量、商品价格、活动减免金额、优惠券减免金额
                        Integer skuNum = value.getInteger("sku_num");
                        Double orderPrice = value.getDouble("order_price");
                        Double splitActivityAmount = value.getDouble("split_activity_amount");
                        Double splitCouponAmount = value.getDouble("split_coupon_amount");
                        splitActivityAmount = splitActivityAmount == null ? 0.0 : splitActivityAmount;
                        splitCouponAmount = splitCouponAmount == null ? 0.0 : splitCouponAmount;

                        // 输出结果
                        return new OrderStats("", "", orderUvCnt, orderNewCnt,skuNum * orderPrice, splitActivityAmount, splitCouponAmount, null);
                    }
                });

        // 6.开窗、聚合
        SingleOutputStreamOperator<OrderStats> orderStream = filterStream
                .windowAll(TumblingEventTimeWindows.of(Time.seconds(10)))
                .reduce(
                        // 先增量聚合
                        new ReduceFunction<OrderStats>() {
                            @Override
                            public OrderStats reduce(OrderStats value1, OrderStats value2) {
                                // 将度量值两两相加
                                value1.setOrderUvCnt(value1.getOrderUvCnt() + value2.getOrderUvCnt());
                                value1.setOrderNewCnt(value1.getOrderNewCnt() + value2.getOrderNewCnt());
                                value1.setOrderOriginalTotalAmount(value1.getOrderOriginalTotalAmount() + value2.getOrderOriginalTotalAmount());
                                value1.setOrderSplitActivityAmount(value1.getOrderSplitActivityAmount() + value2.getOrderSplitActivityAmount());
                                value1.setOrderSplitCouponAmount(value1.getOrderSplitCouponAmount() + value2.getOrderSplitCouponAmount());
                                return value1;
                            }
                        },
                        // 再窗口处理
                        new AllWindowFunction<OrderStats, OrderStats, TimeWindow>() {
                            @Override
                            public void apply(TimeWindow window, Iterable<OrderStats> values, Collector<OrderStats> out) {
                                // 迭代器只有一个元素,就是上面增量聚合的结果
                                OrderStats orderStats = values.iterator().next();
                                // 补充窗口区间
                                orderStats.setStt(DateUtil.parseUnixToDateTime(window.getStart()));
                                orderStats.setEdt(DateUtil.parseUnixToDateTime(window.getEnd()));
                                // 修改统计时间
                                orderStats.setTs(System.currentTimeMillis());
                                // 收集结果往下游发送
                                out.collect(orderStats);
                            }
                        }
                );

        // 7.将数据写入ClickHouse
        orderStream.addSink(
                ClickHouseUtil.getJdbcSink("INSERT INTO realtime.dws_order_stats VALUES(?,?,?,?,?,?,?,?)"));

        // 8.启动任务
        env.execute("DwsOrderStats");
    }
}
