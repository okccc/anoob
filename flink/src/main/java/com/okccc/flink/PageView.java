package com.okccc.flink;

import com.okccc.bean.PVCount;
import com.okccc.bean.UserBehavior;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
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
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.util.Random;

/**
 * @Author: okccc
 * @Date: 2021/10/11 下午2:18
 * @Desc: 统计每个小时的页面访问量(PV=PageView)
 */
public class PageView {
    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度,默认是cpu核数
//        env.setParallelism(1);

        env
                .readTextFile("anoob-flink/input/UserBehavior.csv", "UTF-8")
                // 将输入数据封装成样例类
                .map(new MapFunction<String, UserBehavior>() {
                    @Override
                    public UserBehavior map(String value) throws Exception {
                        // 561558,3611281,965809,pv,1511658000
                        String[] arr = value.split(",");
                        return new UserBehavior(arr[0], arr[1], arr[2], arr[3], Long.parseLong(arr[4]) * 1000);
                    }
                })
                // 分配时间戳和生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<UserBehavior>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                        .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                )
                // 如果将数据都放到一条流,并行度是1无法充分利用集群资源,先将数据映射成Tuple2("randomStr",1)再按照随机字符串分组
                // 实际应用场景中按照userId分组时,如果某个用户数据过多就会导致数据倾斜,此时也可以自定义map逻辑重新设计分组字段
//                .keyBy(r -> 1)
                .map(new MapFunction<UserBehavior, Tuple2<String, Integer>>() {
                    @Override
                    public Tuple2<String, Integer> map(UserBehavior value) throws Exception {
                        return Tuple2.of("str" + new Random().nextInt(8), 1);
                    }
                })
                .keyBy(r -> r.f0)
                // 1小时的滚动窗口
                .window(TumblingEventTimeWindows.of(Time.hours(1)))
                // 先统计每个slot中每个窗口的访问量,一个slot就是一个并行度
                .aggregate(
                        new AggregateFunction<Tuple2<String, Integer>, Integer, Integer>() {
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
                                return null;
                            }
                        },
                        new ProcessWindowFunction<Integer, PVCount, String, TimeWindow>() {
                            @Override
                            public void process(String s, ProcessWindowFunction<Integer, PVCount, String, TimeWindow>.Context context, Iterable<Integer> elements, Collector<PVCount> out) throws Exception {
                                // 收集结果往下游发送
                                out.collect(new PVCount(context.window().getStart(), context.window().getEnd(), elements.iterator().next()));
                            }
                        }
                )
                // 5> PVCount{windowStart=2017-11-26 13:00:00.0, windowEnd=2017-11-26 14:00:00.0, cnt=6685}
                // 再按照窗口分组
                .keyBy(r -> r.windowStart)
                // 将所有slot中属于当前窗口的数据进行累加
                .process(new KeyedProcessFunction<Long, PVCount, PVCount>() {
                    // 声明一个ValueState作为累加器
                    private ValueState<Integer> acc;
                    // 声明一个ValueState作为定时器
                    private ValueState<Long> timer;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        acc = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("acc", Types.INT));
                        timer = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("timer", Types.LONG));
                    }

                    @Override
                    public void processElement(PVCount value, KeyedProcessFunction<Long, PVCount, PVCount>.Context ctx, Collector<PVCount> out) throws Exception {
                        // 判断累加器状态
                        if(acc.value() == null) {
                            acc.update(value.cnt);
                        } else {
                            acc.update(acc.value() + value.cnt);
                        }

                        // 判断定时器状态
                        if(timer.value() == null) {
                            ctx.timerService().registerEventTimeTimer(value.windowEnd - 1L);
                            timer.update(value.windowEnd - 1L);
                        }
                    }

                    @Override
                    public void onTimer(long timestamp, KeyedProcessFunction<Long, PVCount, PVCount>.OnTimerContext ctx, Collector<PVCount> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        // 获取窗口信息
                        long windowEnd = timestamp + 1L;
                        long windowStart = windowEnd - 3600 * 1000L;
                        // 收集结果往下游发送
                        out.collect(new PVCount(windowStart, windowEnd, acc.value()));
                        // 由于分组的key就是窗口本身,累加器和定时器都只属于当前窗口,所以用完可以立即清空节省内存
                        acc.clear();
                        timer.clear();
                    }
                })
                .print();

        // 启动任务
        env.execute();
    }
}
