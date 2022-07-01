package com.okccc.warehouse.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.sql.Timestamp;
import java.time.Duration;

/**
 * Author: okccc
 * Date: 2021/10/12 下午4:05
 * Desc: 广告点击刷单行为分析
 */
public class MaliceClick {
    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);

        // 获取数据源
        SingleOutputStreamOperator<ClickLog> filterStream = env
                .readTextFile("input/ClickLog.csv")
                // 将数据封装成POJO类
                .map((MapFunction<String, ClickLog>) value -> {
                    // 578814,1715,guangdong,shenzhen,1511658330
                    String[] arr = value.split(",");
                    return new ClickLog(arr[0], arr[1], arr[2], arr[3], Long.parseLong(arr[4]) * 1000);
                })
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<ClickLog>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                )

                // 先处理刷单行为,按照(userId, adId)分组
                .keyBy(new KeySelector<ClickLog, Tuple2<String, String>>() {
                    @Override
                    public Tuple2<String, String> getKey(ClickLog value) throws Exception {
                        return Tuple2.of(value.userId, value.adId);
                    }
                })
                // 将刷单数据放到侧输出流,将刷单用户添加到黑名单,涉及状态管理和定时器操作直接上大招
                .process(new BlackListFilter());

        filterStream
                // 按照(province, city)分组
                .keyBy(new KeySelector<ClickLog, Tuple2<String, String>>() {
                    @Override
                    public Tuple2<String, String> getKey(ClickLog value) throws Exception {
                        return Tuple2.of(value.province, value.city);
                    }
                })
                //滚动窗口
                .window(TumblingEventTimeWindows.of(Time.minutes(5)))
                // 先增量聚合再全窗口处理
                // 结果显示beijing地区的数据明显偏多,查看数据源发现有大量相同的(userId, adId)属于恶意刷单行为,应该在数据统计之前就过滤掉
                .aggregate(new CountAgg(), new WindowResult())
                .print();

        // 获取侧输出流数据
        filterStream.getSideOutput(new OutputTag<BlackListUser>("black"){}).print("output");

        // 启动任务
        env.execute();
    }

    // 自定义增量聚合函数
    public static class CountAgg implements AggregateFunction<ClickLog, Integer, Integer> {
        @Override
        public Integer createAccumulator() {
            return 0;
        }
        @Override
        public Integer add(ClickLog value, Integer accumulator) {
            return accumulator + 1;
        }
        @Override
        public Integer getResult(Integer accumulator) {
            return accumulator;
        }
        @Override
        public Integer merge(Integer a, Integer b) {
            return 0;
        }
    }

    // 自定义全窗口函数
    public static class WindowResult extends ProcessWindowFunction<Integer, ClickCount, Tuple2<String, String>, TimeWindow> {
        @Override
        public void process(Tuple2<String, String> key, Context context, Iterable<Integer> elements, Collector<ClickCount> out) {
            out.collect(new ClickCount(context.window().getStart(), context.window().getEnd(), key.f0, key.f1, elements.iterator().next()));
        }
    }

    // 自定义处理函数
    public static class BlackListFilter extends KeyedProcessFunction<Tuple2<String, String>, ClickLog, ClickLog> {
        // 声明状态,保存当前用户点击该广告的次数
        private ValueState<Integer> clickNumState;
        // 声明状态,标记当前用户是否已添加到黑名单
        private ValueState<Boolean> isBlackState;
        // 声明状态,作为定时器
        private ValueState<Long> timerState;

        // 设置广告每天点击次数上限及告警信息
        Integer maxNum = 30;
        String msg = "WARNING: ad click over " + maxNum + " times today!";

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            // 实例化状态变量
            clickNumState = getRuntimeContext().getState(new ValueStateDescriptor<>("click-num", Types.INT));
            isBlackState = getRuntimeContext().getState(new ValueStateDescriptor<>("is-black", Types.BOOLEAN));
            timerState = getRuntimeContext().getState(new ValueStateDescriptor<>("timer", Types.LONG));
        }

        @Override
        public void processElement(ClickLog value, Context ctx, Collector<ClickLog> out) throws Exception {
            // 1.第一次点击
            if (clickNumState.value() == null) {
                // 获取明天0点的时间戳,默认是伦敦时间,北京时间要减8小时
                long curTime = ctx.timerService().currentProcessingTime();
                long ts = (curTime / (24 * 3600 * 1000) + 1) * (24 * 3600 * 1000) - (8 * 3600 * 1000);
                // 注册明天0点清空状态的定时器
                ctx.timerService().registerProcessingTimeTimer(ts);
                // 更新状态
                clickNumState.update(1);
                timerState.update(ts);
                // 2.点击次数未达上限
            } else if (clickNumState.value() < maxNum) {
                // 更新状态
                clickNumState.update(clickNumState.value() + 1);
                // 3.点击次数已达上限
            } else {
                // 判断是否已经在黑名单
                if (isBlackState.value() == null) {
                    // 将刷单用户添加到黑名单
                    isBlackState.update(true);
                    // 将刷单数据放到侧输出流
                    ctx.output(new OutputTag<BlackListUser>("black"){}, new BlackListUser(value.userId, value.adId, msg));
                }
                // 已经在黑名单就不处理直接结束
                return;
            }

            // 收集结果往下游发送
            out.collect(value);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<ClickLog> out) throws Exception {
            super.onTimer(timestamp, ctx, out);
            // 触发定时器时已经是第二天了,清空所有状态
            clickNumState.clear();
            isBlackState.clear();
            timerState.clear();
        }
    }

    // 输入数据POJO类
    public static class ClickLog {
        public String userId;
        public String adId;
        public String province;
        public String city;
        public Long timestamp;

        public ClickLog() {
        }

        public ClickLog(String userId, String adId, String province, String city, Long timestamp) {
            this.userId = userId;
            this.adId = adId;
            this.province = province;
            this.city = city;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "ClickLog{" +
                    "userId='" + userId + '\'' +
                    ", adId='" + adId + '\'' +
                    ", province='" + province + '\'' +
                    ", city='" + city + '\'' +
                    ", timestamp=" + new Timestamp(timestamp) +
                    '}';
        }
    }

    // 输出结果POJO类
    public static class ClickCount {
        public Long windowStart;
        public Long windowEnd;
        public String province;
        public String city;
        public Integer count;

        public ClickCount() {
        }

        public ClickCount(Long windowStart, Long windowEnd, String province, String city, Integer count) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.province = province;
            this.city = city;
            this.count = count;
        }

        @Override
        public String toString() {
            return "ClickCount{" +
                    "windowStart=" + new Timestamp(windowStart) +
                    ", windowEnd=" + new Timestamp(windowEnd) +
                    ", province='" + province + '\'' +
                    ", city='" + city + '\'' +
                    ", count=" + count +
                    '}';
        }
    }

    // 黑名单POJO类
    public static class BlackListUser {
        public String userId;
        public String adId;
        public String msg;

        public BlackListUser() {
        }

        public BlackListUser(String userId, String adId, String msg) {
            this.userId = userId;
            this.adId = adId;
            this.msg = msg;
        }

        @Override
        public String toString() {
            return "BlackListUser{" +
                    "userId='" + userId + '\'' +
                    ", adId='" + adId + '\'' +
                    ", msg='" + msg + '\'' +
                    '}';
        }
    }
}
