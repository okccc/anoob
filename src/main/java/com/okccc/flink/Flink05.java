package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.JoinFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoFlatMapFunction;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;

/**
 * Author: okccc
 * Date: 2021/9/18 下午2:28
 * Desc: CoProcessFunction、ProcessJoinFunction
 */
public class Flink05 {
    public static void main(String[] args) throws Exception {
        /*
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
         * 定时器timestamp会驱动该回调函数运行,ctx功能同上,out收集结果往下游发送
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 演示union
        demo01(env);
        // 演示水位线传播方式
//        demo02(env);
        // 演示connect
//        demo03(env);
//        demo04(env);
        // 演示join
//        demo05(env);
//        demo06(env);

        // 启动任务
        env.execute();
    }

    private static void demo01(StreamExecutionEnvironment env) {
        // 演示union合并多条流
        DataStreamSource<Integer> stream01 = env.fromElements(1, 2);
        DataStreamSource<Integer> stream02 = env.fromElements(3, 4, 5);
        DataStreamSource<Integer> stream03 = env.fromElements(6, 7, 8, 9);
        DataStream<Integer> unionStream = stream01.union(stream02, stream03);
        unionStream.print();
    }

    private static void demo02(StreamExecutionEnvironment env) {
        SingleOutputStreamOperator<Tuple2<String, Long>> stream01 = env.socketTextStream("localhost", 9999)
                .map(value -> {
                    String[] arr = value.split(",");
                    return Tuple2.of(arr[0], Long.parseLong(arr[1]) * 1000);
                })
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1)
                );
        SingleOutputStreamOperator<Tuple2<String, Long>> stream02 = env.socketTextStream("localhost", 8888)
                .map(value -> {
                    String[] arr = value.split(",");
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
                .window(TumblingEventTimeWindows.of(Time.seconds(5)))
                // 输入`a,1` `b,5`
                .process(new ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, Context context, Iterable<Tuple2<String, Long>> elements, Collector<String> out) throws Exception {
                        // 当`b,5`进来时`a,1`的窗口也触发了,说明分流时水位线是复制广播的,整个流中只有一个最新的水位线,和分流的key没关系
                        out.collect(key + " 的窗口触发了,当前水位线是 " + context.currentWatermark());
                    }
                })
                .print();

        // 合流时水位线传播方式
//        stream01
//                .union(stream02)
//                // 开启两个nc -lk,分别输入`a,1` `a,2`
//                .process(new ProcessFunction<Tuple2<String, Long>, String>() {
//                    @Override
//                    public void processElement(Tuple2<String, Long> value, Context ctx, Collector<String> out) throws Exception {
//                        // 开始两个流的水位线都是负无穷,然后任意流再输入任意元素,水位线变成999而不是1999,说明合流时水位线传递的是小的那个值
//                        out.collect("当前水位线是 " + ctx.timerService().currentWatermark());
//                    }
//                })
//                .print();
    }

    private static void demo03(StreamExecutionEnvironment env) {
        // 演示connect连接两条流
        DataStreamSource<Flink01.Event> actionStream = env.addSource(new Flink01.UserActionSource());
        DataStreamSource<String> queryStream = env.socketTextStream("localhost", 9999).setParallelism(1);
        actionStream
                // 第一条流分流
                .keyBy(r -> r.user)
                // 第二条流做广播变量
                .connect(queryStream.broadcast())
                // 相当于join操作
                .flatMap(new CoFlatMapFunction<Flink01.Event, String, Flink01.Event>() {
                    // 在socket流中输入查询关键字"./home" "./cart",即对应action流中Event的url属性
                    private String query = "";
                    @Override
                    public void flatMap1(Flink01.Event value, Collector<Flink01.Event> out) throws Exception {
                        // 第一条流的数据进来时调用
                        if (value.url.equals(query)) {
                            // 满足条件就向下游发送
                            out.collect(value);
                        }
                    }

                    @Override
                    public void flatMap2(String value, Collector<Flink01.Event> out) throws Exception {
                        // 第二条流的数据进来时调用
                        query = value;
                    }
                })
                .print();
    }

    private static void demo04(StreamExecutionEnvironment env) {
        DataStreamSource<Tuple2<String, Integer>> stream01 = env.fromElements(Tuple2.of("fly", 18), Tuple2.of("ted", 19));
        DataStreamSource<Tuple2<String, String>> stream02 = env.fromElements(Tuple2.of("fly", "orc"), Tuple2.of("ted", "ud"));
        stream01
                // 第一条流分流
                .keyBy(r -> r.f0)
                // 第二条流除了广播也可以分流
                .connect(stream02.keyBy(r -> r.f0))
                // 双流合并的底层是CoProcessFunction
                .process(new CoProcessFunction<Tuple2<String, Integer>, Tuple2<String, String>, String>() {
                    // 声明ListState存储流中数据
                    private ListState<Tuple2<String, Integer>> listState01;
                    private ListState<Tuple2<String, String>> listState02;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 实例化状态变量
                        listState01 = getRuntimeContext().getListState(
                                new ListStateDescriptor<>("list-state01", Types.TUPLE(Types.STRING, Types.INT)));
                        listState02 = getRuntimeContext().getListState(
                                new ListStateDescriptor<>("list-state02", Types.TUPLE(Types.STRING, Types.STRING)));
                    }

                    @Override
                    public void processElement1(Tuple2<String, Integer> value, Context ctx, Collector<String> out) throws Exception {
                        // 第一条流的数据进来了
                        listState01.add(value);
                        // 匹配另一条流数据
                        for (Tuple2<String, String> e : listState02.get()) {
                            if (value.f0.equals(e.f0)) {
                                out.collect(value + " <=> " + e);
                            }
                        }
                    }

                    @Override
                    public void processElement2(Tuple2<String, String> value, Context ctx, Collector<String> out) throws Exception {
                        // 第二条流的数据进来了
                        listState02.add(value);
                        // 匹配另一条流数据
                        for (Tuple2<String, Integer> e : listState01.get()) {
                            if (value.f0.equals(e.f0)) {
                                out.collect(e + " <=> " + value);
                            }
                        }
                    }
                })
                .print();
    }

    private static void demo05(StreamExecutionEnvironment env) {
        // 演示基于时间间隔的join
        SingleOutputStreamOperator<Event> stream01 = env
                .fromElements(
                        Event.of("fly", "create", 10 * 60 * 1000L),
                        Event.of("fly", "pay", 20 * 60 * 1000L)
                )
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forMonotonousTimestamps()
                        .withTimestampAssigner((element, recordTimestamp) -> element.timestamp));
        SingleOutputStreamOperator<Event> stream02 = env
                .fromElements(
                        Event.of("fly", "view", 5 * 60 * 1000L),
                        Event.of("fly", "view", 10 * 60 * 1000L),
                        Event.of("fly", "view", 12 * 60 * 1000L),
                        Event.of("fly", "view", 22 * 60 * 1000L)
                )
                .assignTimestampsAndWatermarks(WatermarkStrategy.<Event>forMonotonousTimestamps()
                        .withTimestampAssigner((element, recordTimestamp) -> element.timestamp));

        stream01
                .keyBy(r -> r.userId)
                // A流的每个元素和B流某个时间段的所有元素进行连接,join间隔是对称的,反过来写也是一样的
                .intervalJoin(stream02.keyBy(r -> r.userId))
                .between(Time.minutes(-10), Time.minutes(5))
                .process(new ProcessJoinFunction<Event, Event, String>() {
                    @Override
                    public void processElement(Event left, Event right, Context ctx, Collector<String> out) throws Exception {
                        out.collect(left + " <=> " + right);
                    }
                })
                .print();
    }

    private static void demo06(StreamExecutionEnvironment env) {
        // 演示基于窗口的join(很少用)
        SingleOutputStreamOperator<Tuple2<String, Integer>> stream01 = env
                .fromElements(Tuple2.of("a", 1), Tuple2.of("b", 1))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Integer>>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1 * 1000)
                );
        SingleOutputStreamOperator<Tuple2<String, Integer>> stream02 = env
                .fromElements(Tuple2.of("a", 2), Tuple2.of("b", 2), Tuple2.of("b", 3))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Integer>>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1 * 1000)
                );

        stream01
                .join(stream02)
                .where(r -> r.f0)
                .equalTo(r -> r.f0)
                .window(TumblingEventTimeWindows.of(Time.seconds(5)))
                .apply(new JoinFunction<Tuple2<String, Integer>, Tuple2<String, Integer>, String>() {
                    @Override
                    public String join(Tuple2<String, Integer> first, Tuple2<String, Integer> second) throws Exception {
                        return first + " <=> " + second;
                    }
                })
                .print();
    }

    public static class Event {
        public String userId;
        public String eventType;
        public Long timestamp;

        public Event() {
        }

        public Event(String userId, String eventType, Long timestamp) {
            this.userId = userId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }

        public static Event of(String userId, String eventType, Long timestamp) {
            return new Event(userId, eventType, timestamp);
        }

        @Override
        public String toString() {
            return "Event{" +
                    "userId='" + userId + '\'' +
                    ", eventType='" + eventType + '\'' +
                    ", timestamp=" + new Timestamp(timestamp) +
                    '}';
        }
    }

}
