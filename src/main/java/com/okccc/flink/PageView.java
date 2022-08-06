package com.okccc.flink;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Properties;
import java.util.Random;

/**
 * Author: okccc
 * Date: 2021/10/11 下午2:18
 * Desc: 实时统计1小时内的页面访问量(PV=PageView)
 */
public class PageView {
    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度,默认是cpu核数
//        env.setParallelism(1);

        // 获取数据源
        Properties prop = new Properties();
        prop.put("bootstrap.servers", "localhost:9092");
        prop.put("group.id", "consumer-group");
        prop.put("key.deserializer", StringDeserializer.class.toString());
        prop.put("value.deserializer", StringDeserializer.class.toString());
        env
                .addSource(new FlinkKafkaConsumer<>("nginx", new SimpleStringSchema(), prop))
                // 将流数据封装样例类
                .map(new MapFunction<String, UserBehavior>() {
                    @Override
                    public UserBehavior map(String value) throws Exception {
                        // 561558,3611281,965809,pv,1511658000
                        String[] arr = value.split(",");
                        return new UserBehavior(arr[0], arr[1], arr[2], arr[3], Long.parseLong(arr[4]) * 1000);
                    }
                })
                .filter(r -> r.behavior.equals("pv"))
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<UserBehavior>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                        .withTimestampAssigner(new SerializableTimestampAssigner<UserBehavior>() {
                            @Override
                            public long extractTimestamp(UserBehavior element, long recordTimestamp) {
                                return element.timestamp;
                            }
                        })
                )
                // 如果将数据都放到一条流,并行度是1无法充分利用集群资源,先将数据映射成Tuple2("randomStr",1)再按照随机字符串分组
                // 生产中按照userId分组时,如果某个用户数据过多就会导致数据倾斜,此时也可以自定义map逻辑重新设计分组字段
//                .keyBy(r -> 1)
                .map(new MapFunction<UserBehavior, Tuple2<String, Integer>>() {
                    @Override
                    public Tuple2<String, Integer> map(UserBehavior value) throws Exception {
                        return Tuple2.of("str" + new Random().nextInt(10), 1);
                    }
                })
                .keyBy(r -> r.f0)
                // 1小时的滚动窗口
                .window(TumblingEventTimeWindows.of(Time.hours(1)))
                // 统计每个slot里面每个窗口的访问量
                .aggregate(new CountAgg(), new WindowResult())
                // 再按照窗口分组
                .keyBy(r -> r.windowEnd)
                // 将窗口内所有数据进行累加
                .process(new MyKeyedProcessFunction())
                .print();

        // 启动任务
        env.execute();
    }

    // 自定义预聚合函数
    public static class CountAgg implements AggregateFunction<Tuple2<String, Integer>, Integer, Integer> {
        @Override
        public Integer createAccumulator() {
            return 0;
        }
        @Override
        public Integer add(Tuple2<String, Integer> value, Integer accumulator) {
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

    // 自定义窗口函数
    public static class WindowResult extends ProcessWindowFunction<Integer, PVCount, String, TimeWindow> {
        @Override
        public void process(String s, Context context, Iterable<Integer> elements, Collector<PVCount> out) throws Exception {
            out.collect(new PVCount(context.window().getStart(), context.window().getEnd(), elements.iterator().next()));
        }
    }

    // 自定义处理函数
    public static class MyKeyedProcessFunction extends KeyedProcessFunction<Long, PVCount, PVCount> {
        // 定义状态保存窗口的PV值
        private ValueState<Integer> valueState;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            valueState = getRuntimeContext().getState(new ValueStateDescriptor<>("valueState", Types.INT));
        }

        @Override
        public void processElement(PVCount value, Context ctx, Collector<PVCount> out) throws Exception {
            // 判断当前状态
            if (valueState.value() == null) {
                valueState.update(value.cnt);
            } else {
                valueState.update(valueState.value() + value.cnt);
            }
            // 注册一个windowEnd + 1(ms)之后触发的定时器
            ctx.timerService().registerEventTimeTimer(value.windowEnd + 1);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<PVCount> out) throws Exception {
            super.onTimer(timestamp, ctx, out);
            // 当前分组字段就是窗口结束时间
            Long windowEnd = ctx.getCurrentKey();
            Long windowStart = windowEnd - 3600 * 1000;
            // 收集结果往下游发送
            out.collect(new PVCount(windowStart, windowEnd, valueState.value()));
            // 清空状态
            valueState.clear();
        }
    }

    // 输入数据POJO类
    public static class UserBehavior {
        public String userId;
        public String itemId;
        public String categoryId;
        public String behavior;
        public Long timestamp;

        public UserBehavior() {
        }

        public UserBehavior(String userId, String itemId, String categoryId, String behavior, Long timestamp) {
            this.userId = userId;
            this.itemId = itemId;
            this.categoryId = categoryId;
            this.behavior = behavior;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "UserBehavior{" +
                    "userId='" + userId + '\'' +
                    ", itemId='" + itemId + '\'' +
                    ", categoryId='" + categoryId + '\'' +
                    ", behavior='" + behavior + '\'' +
                    ", timestamp=" + new Timestamp(timestamp) +
                    '}';
        }
    }

    // 输出结果POJO类
    public static class PVCount {
        public Long windowStart;
        public Long windowEnd;
        public Integer cnt;

        public PVCount() {
        }

        public PVCount(Long windowStart, Long windowEnd, Integer cnt) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.cnt = cnt;
        }

        @Override
        public String toString() {
            return "PVCount{" +
                    "windowStart=" + new Timestamp(windowStart) +
                    ", windowEnd=" + new Timestamp(windowEnd) +
                    ", cnt=" + cnt +
                    '}';
        }
    }
}
