package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.co.CoFlatMapFunction;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2021/9/18 15:22:28
 * @Desc: DataStream API - Operators - connect、union、join
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/overview/#connect
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/overview/#union
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/joining/
 *
 * 合流
 * union可以合并多条流,流中的元素类型必须相同
 * connect只能连接两条流,流中的元素类型可以不同
 *
 * CoProcessFunction<IN1, IN2, OUT>
 * IN1：第一条流输入元素类型
 * IN2：第二条流输入元素类型
 * OUT：输出元素类型
 * processElement2(IN1 value, Context ctx, Collector<OUT> out)
 * processElement2(IN2 value, Context ctx, Collector<OUT> out)
 * 每来一条数据都会驱动其运行,然后输出0/1/N个元素,ctx可以访问元素的时间戳和key、注册定时器、写侧输出流,out收集结果往下游发送
 * onTimer(long timestamp, OnTimerContext ctx, Collector<OUT> out)
 * 定时器timestamp会驱动该回调函数运行,ctx和out功能同上
 */
public class Flink05 {

    /**
     * 演示union合并多条流
     */
    private static void testUnion(StreamExecutionEnvironment env) {
        DataStreamSource<Integer> stream01 = env.fromData(1, 2);
        DataStreamSource<Integer> stream02 = env.fromData(3, 4, 5);
        DataStreamSource<Integer> stream03 = env.fromData(6, 7, 8, 9);
        DataStream<Integer> unionStream = stream01.union(stream02, stream03);
        unionStream.print();
    }

    /**
     * 演示水位线传播方式
     */
    private static void testWatermarkBroadcast(StreamExecutionEnvironment env) {
        SingleOutputStreamOperator<Tuple2<String, Long>> stream01 = env.socketTextStream("localhost", 9999)
                .map(value -> {
                    String[] arr = value.split("\\s");
                    return Tuple2.of(arr[0], Long.parseLong(arr[1]) * 1000);
                })
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1)
                );
        SingleOutputStreamOperator<Tuple2<String, Long>> stream02 = env.socketTextStream("localhost", 8888)
                .map(value -> {
                    String[] arr = value.split("\\s");
                    return Tuple2.of(arr[0], Long.parseLong(arr[1]) * 1000);
                })
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1)
                );

        // 分流时水位线传播方式
        stream01
            .keyBy(r -> r.f0)
            .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
            // 输入`a,1` `b,5`
            .process(new ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>() {
                @Override
                public void process(String s, ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>.Context context, Iterable<Tuple2<String, Long>> elements, Collector<String> out) {
                    // 当`b,5`进来时`a,1`窗口关闭,说明分流时水位线是复制广播的,整个流中只有一个最新的水位线,和分流的key没关系
                    System.out.println("分流时的水位线：" + context.currentWatermark());
                }
            });

        // 合流时水位线传播方式
        stream01
            .union(stream02)
            // 开启两个nc -lk,分别输入`a,1` `a,2`
            .process(new ProcessFunction<Tuple2<String, Long>, String>() {
                @Override
                public void processElement(Tuple2<String, Long> value, Context ctx, Collector<String> out) {
                    // 开始时两个流水位线都是负无穷大,然后任意流再输入任意元素,水位线变成999而不是1999,说明合流时水位线传递的是小的那个值
                    System.out.println("合流时的水位线：" + ctx.timerService().currentWatermark());
                }
            });
    }

    /**
     * 演示connect连接两条流
     */
    private static void testConnect01(StreamExecutionEnvironment env) {
        DataStreamSource<Tuple2<String, String>> stream01 = env.fromData(Tuple2.of("grubby", "orc"));
        DataStreamSource<String> stream02 = env.socketTextStream("localhost", 9999).setParallelism(1);
        stream01
                // 第一条流分流
                .keyBy(r -> r.f0)
                // 第二条流做广播变量
                .connect(stream02.broadcast())
                // 相当于join操作
                .flatMap(new CoFlatMapFunction<Tuple2<String, String>, String, Tuple2<String, String>>() {
                    // 在socket流中输入查询关键字
                    private String query = "";

                    @Override
                    public void flatMap1(Tuple2<String, String> value, Collector<Tuple2<String, String>> out) {
                        // 第一条流的数据进来时调用
                        if (value.f0.equals(query)) {
                            // 满足条件就向下游发送
                            out.collect(value);
                        }
                    }

                    @Override
                    public void flatMap2(String value, Collector<Tuple2<String, String>> out) {
                        // 第二条流的数据进来时调用
                        query = value;
                    }
                })
                .print();
    }

    private static void testConnect02(StreamExecutionEnvironment env) {
        DataStreamSource<Tuple2<String, Integer>> stream01 = env.fromData(Tuple2.of("fly", 18), Tuple2.of("ted", 19));
        DataStreamSource<Tuple2<String, String>> stream02 = env.fromData(Tuple2.of("fly", "orc"), Tuple2.of("ted", "ud"));
        stream01
                // 第一条流分流
                .keyBy(r -> r.f0)
                // 第二条流除了广播也可以分流
                .connect(stream02.keyBy(r -> r.f0))
                // 双流合并的底层是CoProcessFunction
                .process(new CoProcessFunction<Tuple2<String, Integer>, Tuple2<String, String>, String>() {
                    // 声明ListState存储流中数据
                    private ListState<Tuple2<String, Integer>> stream01;
                    private ListState<Tuple2<String, String>> stream02;

                    @Override
                    public void open(Configuration parameters) {
                        // 实例化状态变量
                        stream01 = getRuntimeContext().getListState(
                                new ListStateDescriptor<>("stream01", Types.TUPLE(Types.STRING, Types.INT)));
                        stream02 = getRuntimeContext().getListState(
                                new ListStateDescriptor<>("stream02", Types.TUPLE(Types.STRING, Types.STRING)));
                    }

                    @Override
                    public void processElement1(Tuple2<String, Integer> value, Context ctx, Collector<String> out) throws Exception {
                        // 第一条流数据进来了
                        stream01.add(value);
                        // 匹配另一条流数据
                        for (Tuple2<String, String> tuple2 : stream02.get()) {
                            if (value.f0.equals(tuple2.f0)) {
                                out.collect(value + "" + tuple2);
                            }
                        }
                    }

                    @Override
                    public void processElement2(Tuple2<String, String> value, Context ctx, Collector<String> out) throws Exception {
                        // 第二条流数据进来了
                        stream02.add(value);
                        // 匹配另一条流数据
                        for (Tuple2<String, Integer> tuple2 : stream01.get()) {
                            if (value.f0.equals(tuple2.f0)) {
                                out.collect(tuple2 + " <=> " + value);
                            }
                        }
                    }
                })
                .print();
    }

    /**
     * 演示基于时间间隔的join
     */
    private static void testIntervalJoin(StreamExecutionEnvironment env) {
        SingleOutputStreamOperator<Tuple3<String, String, Long>> stream01 = env
                .fromData(
                        Tuple3.of("fly", "create", 10 * 60 * 1000L),
                        Tuple3.of("fly", "pay", 10 * 60 * 1000L)
                )
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Tuple3<String, String, Long>>forMonotonousTimestamps()
                        .withTimestampAssigner((element, recordTimestamp) -> element.f2));

        SingleOutputStreamOperator<Tuple3<String, String, Long>> stream02 = env
                .fromData(
                        Tuple3.of("fly", "view", 5 * 60 * 1000L),
                        Tuple3.of("fly", "view", 10 * 60 * 1000L),
                        Tuple3.of("fly", "view", 12 * 60 * 1000L),
                        Tuple3.of("fly", "view", 22 * 60 * 1000L)
                )
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Tuple3<String, String, Long>>forMonotonousTimestamps()
                        .withTimestampAssigner((element, recordTimestamp) -> element.f2));

        stream01
                .keyBy(r -> r.f0)
                // A流的每个元素和B流某个时间段的所有元素进行连接,join间隔是对称的,反过来写也是一样的
                .intervalJoin(stream02.keyBy(r -> r.f0))
                .between(Duration.ofMinutes(-10), Duration.ofMinutes(5))
                .process(new ProcessJoinFunction<Tuple3<String, String, Long>, Tuple3<String, String, Long>, String>() {
                    @Override
                    public void processElement(Tuple3<String, String, Long> left, Tuple3<String, String, Long> right, ProcessJoinFunction<Tuple3<String, String, Long>, Tuple3<String, String, Long>, String>.Context ctx, Collector<String> out) {
                        out.collect(left + " <=> " + right);
                    }
                })
                .print();
    }

    /**
     * 演示基于窗口的join(很少用)
     */
    private static void testWindowJoin(StreamExecutionEnvironment env) {
        SingleOutputStreamOperator<Tuple2<String, Integer>> stream01 = env
                .fromData(Tuple2.of("a", 1), Tuple2.of("b", 1))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Integer>>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1 * 1000)
                );
        SingleOutputStreamOperator<Tuple2<String, Integer>> stream02 = env
                .fromData(Tuple2.of("a", 2), Tuple2.of("b", 2), Tuple2.of("b", 3))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Integer>>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1 * 1000)
                );

        stream01
                .join(stream02)
                .where(r -> r.f0)
                .equalTo(r -> r.f0)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                .apply(new JoinFunction<Tuple2<String, Integer>, Tuple2<String, Integer>, String>() {
                    @Override
                    public String join(Tuple2<String, Integer> first, Tuple2<String, Integer> second) {
                        return first + " <=> " + second;
                    }
                })
                .print();
    }

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        testUnion(env);
//        testWatermarkBroadcast(env);
//        testConnect01(env);
//        testConnect02(env);
//        testIntervalJoin(env);
//        testWindowJoin(env);

        // 启动任务
        env.execute();
    }
}
