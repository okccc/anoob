package com.okccc.flink.tuning;

import com.alibaba.fastjson.JSON;
import com.okccc.flink.source.MockSourceFunction;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.util.Random;

/**
 * @Author: okccc
 * @Date: 2022/8/3 4:05 下午
 * @Desc: flink数据倾斜优化之twoPhaseKeyBy
 */
public class SkewDemo01 extends BaseEnvironment {
    public static void main(String[] args) throws Exception {
        // 流处理环境
        StreamExecutionEnvironment env = getEnv();
        // 获取数据源
        DataStreamSource<String> dataStream = env.addSource(new MockSourceFunction());
        // 数据处理
        SingleOutputStreamOperator<Tuple2<String, Integer>> jsonStream = dataStream
                .map(JSON::parseObject)
                .filter(data -> StringUtils.isEmpty(data.getString("start")))
                // 将数据映射成(mid, 1)
                .map(data -> Tuple2.of(data.getJSONObject("common").getString("mid"), 1))
                .returns(Types.TUPLE(Types.STRING, Types.INT));

        // 按照mid分组,统计每10s每个mid出现的次数
        ParameterTool parameterTool = ParameterTool.fromArgs(args);
        boolean isTwoPhase = parameterTool.getBoolean("two-phase", false);
        int randomNum = parameterTool.getInt("random-num", 5);
        // 判断是否使用两阶段聚合,对比开启前后效果Overview - SubTasks - Bytes Received & Bytes Sent
        if (!isTwoPhase) {
            jsonStream
                    .keyBy(r -> r.f0)
                    .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                    .reduce(
                            // 预聚合函数
                            new ReduceFunction<Tuple2<String, Integer>>() {
                                @Override
                                public Tuple2<String, Integer> reduce(Tuple2<String, Integer> value1, Tuple2<String, Integer> value2) {
                                    return Tuple2.of(value1.f0, value1.f1 + value2.f1);
                                }
                            },
                            // 窗口处理函数
                            new ProcessWindowFunction<Tuple2<String, Integer>, Tuple3<String, Integer, Long>, String, TimeWindow>() {
                                @Override
                                public void process(String s, Context context, Iterable<Tuple2<String, Integer>> elements, Collector<Tuple3<String, Integer, Long>> out) {
                                    Tuple2<String, Integer> tuple2 = elements.iterator().next();
                                    long windowEnd = context.window().getEnd();
                                    out.collect(Tuple3.of(tuple2.f0, tuple2.f1, windowEnd));
                                }
                            }
                    )
                    .print().setParallelism(1);
        } else {
            // 第一次聚合
            SingleOutputStreamOperator<Tuple3<String, Integer, Long>> firstAgg = jsonStream
                    // 拼接随机数后缀将key打散,比如mid-001变成mid-001-0、mid-001-1、mid-001-2,随机数范围需手动测试选择合适值
                    .map(new MapFunction<Tuple2<String, Integer>, Tuple2<String, Integer>>() {
                        final Random random = new Random();
                        @Override
                        public Tuple2<String, Integer> map(Tuple2<String, Integer> value) {
                            return Tuple2.of(value.f0 + "-" + random.nextInt(randomNum), 1);
                        }
                    })
                    .keyBy(r -> r.f0)
                    .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                    .reduce(
                            // 预聚合函数
                            new ReduceFunction<Tuple2<String, Integer>>() {
                                @Override
                                public Tuple2<String, Integer> reduce(Tuple2<String, Integer> value1, Tuple2<String, Integer> value2) {
                                    return Tuple2.of(value1.f0, value1.f1 + value2.f1);
                                }
                            },
                            // 窗口处理函数
                            new ProcessWindowFunction<Tuple2<String, Integer>, Tuple3<String, Integer, Long>, String, TimeWindow>() {
                                @Override
                                public void process(String s, Context context, Iterable<Tuple2<String, Integer>> elements, Collector<Tuple3<String, Integer, Long>> out) {
                                    // 迭代器只包含一个元素,就是前面增量聚合的结果
                                    Tuple2<String, Integer> tuple2 = elements.iterator().next();
                                    // 不管怎么打散,当前窗口的数据肯定不能跑到别的窗口,所以要给数据绑定窗口信息准备做第二次聚合
                                    long windowEnd = context.window().getEnd();
                                    out.collect(Tuple3.of(tuple2.f0, tuple2.f1, windowEnd));
                                }
                            }
                    );
            // 第二次聚合
            firstAgg
                    // 去掉拼接的随机数将key还原
                    .map(new MapFunction<Tuple3<String, Integer, Long>, Tuple3<String, Integer, Long>>() {
                        @Override
                        public Tuple3<String, Integer, Long> map(Tuple3<String, Integer, Long> value) {
                            String originKey = value.f0.split("-")[0];
                            return Tuple3.of(originKey, value.f1 ,value.f2);
                        }
                    })
                    // 按照原来的key和windowEnd分组,将属于同一个窗口但被打散的数据聚合到一起
                    // Tuple2使用lambda表达式泛型丢失问题：The generic type parameters of 'Tuple2' are missing
                    .keyBy(new KeySelector<Tuple3<String, Integer, Long>, Tuple2<String, Long>>() {
                        @Override
                        public Tuple2<String, Long> getKey(Tuple3<String, Integer, Long> value) {
                            return Tuple2.of(value.f0, value.f2);
                        }
                    })
                    // 直接聚合
                    .reduce(new ReduceFunction<Tuple3<String, Integer, Long>>() {
                        @Override
                        public Tuple3<String, Integer, Long> reduce(Tuple3<String, Integer, Long> value1, Tuple3<String, Integer, Long> value2) {
                            return Tuple3.of(value1.f0, value1.f1 + value2.f1, value1.f2);
                        }
                    })
                    .print().setParallelism(1);
        }

        // 启动任务
        env.execute();
    }
}
