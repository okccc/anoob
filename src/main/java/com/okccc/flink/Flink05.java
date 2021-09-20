package com.okccc.flink;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.co.CoFlatMapFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/**
 * Author: okccc
 * Date: 2021/9/18 下午2:28
 * Desc: 多流合并
 */
public class Flink05 {
    public static void main(String[] args) throws Exception {
        /*
         * 合流
         * union可以合并多条流,流中的元素类型必须相同
         * connect只能连接两条流,流中的元素类型可以不同
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 演示union
//        demo01(env);
        // 演示水位线传播方式
//        demo02(env);
//        demo03(env);
        // 演示connect
        demo04(env);

        // 启动任务
        env.execute();

    }

    private static void demo01(StreamExecutionEnvironment env) {
        // 演示union合并多条流
        DataStreamSource<Integer> stream01 = env.fromElements(1, 2, 3);
        DataStreamSource<Integer> stream02 = env.fromElements(4, 5, 6);
        DataStreamSource<Integer> stream03 = env.fromElements(7, 8, 9);
        DataStream<Integer> unionStream = stream01.union(stream02, stream03);
        unionStream.print();
    }

    private static void demo02(StreamExecutionEnvironment env) {
        // 分流时水位线的传播方式
        env.socketTextStream("localhost",9999)
                .map(new MapFunction<String, Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> map(String value) throws Exception {
                        String[] words = value.split(",");
                        return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000);
                    }
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forMonotonousTimestamps()
                                .withTimestampAssigner(new SerializableTimestampAssigner<Tuple2<String, Long>>() {
                                    @Override
                                    public long extractTimestamp(Tuple2<String, Long> element, long recordTimestamp) {
                                        return element.f1;
                                    }
                                })
                )
                .keyBy(r -> r.f0)
                .window(TumblingEventTimeWindows.of(Time.seconds(5)))
                // 简单聚合一下,输入`a,1` `b,5`
                .process(new ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, Context context, Iterable<Tuple2<String, Long>> elements, Collector<String> out) throws Exception {
                        // 当`b,5`进来时`a,1`的窗口也触发了,说明分流时水位线是复制广播的,整个流中只有一个最新的水位线,和分流的key没关系
                        out.collect(key + " 的窗口触发了");
                    }
                })
                .print();
    }

    private static void demo03(StreamExecutionEnvironment env) {
        // 合流时水位线的传播方式
        SingleOutputStreamOperator<Tuple2<String, Long>> stream01 = env.socketTextStream("localhost", 9999)
                .map(new MapFunction<String, Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> map(String value) throws Exception {
                        String[] words = value.split(",");
                        return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000);
                    }
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forMonotonousTimestamps()
                                .withTimestampAssigner(new SerializableTimestampAssigner<Tuple2<String, Long>>() {
                                    @Override
                                    public long extractTimestamp(Tuple2<String, Long> element, long recordTimestamp) {
                                        return element.f1;
                                    }
                                })
                );

        SingleOutputStreamOperator<Tuple2<String, Long>> stream02 = env.socketTextStream("localhost", 8888)
                .map(new MapFunction<String, Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> map(String value) throws Exception {
                        String[] words = value.split(",");
                        return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000);
                    }
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forMonotonousTimestamps()
                                .withTimestampAssigner(new SerializableTimestampAssigner<Tuple2<String, Long>>() {
                                    @Override
                                    public long extractTimestamp(Tuple2<String, Long> element, long recordTimestamp) {
                                        return element.f1;
                                    }
                                })
                );

        stream01
                .union(stream02)
                // 开启两个nc -lk,分别输入`a,1` `a,2`
                .process(new ProcessFunction<Tuple2<String, Long>, String>() {
                    @Override
                    public void processElement(Tuple2<String, Long> value, Context ctx, Collector<String> out) throws Exception {
                        // 开始两个流的水位线都是负无穷,然后任意流再输入任意元素,水位线变成999而不是1999,说明合流时水位线传递的是小的那个值
                        out.collect("当前水位线是：" + ctx.timerService().currentWatermark());
                    }
                })
                .print();
    }

    private static void demo04(StreamExecutionEnvironment env) {
        // 演示connect连接两条流
        DataStreamSource<Flink01.Event> actionStream = env.addSource(new Flink01.UserActionSource());
        DataStreamSource<String> queryStream = env.socketTextStream("localhost", 9999).setParallelism(1);
        actionStream
                // 将第一条流分流
                .keyBy(r -> r.user)
                // 将第二条流广播到所有并行度
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
}
