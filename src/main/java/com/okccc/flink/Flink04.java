package com.okccc.flink;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
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
 * Date: 2021/9/13 下午2:06
 * Desc: 时间语义和水位线
 */
public class Flink04 {
    public static void main(String[] args) throws Exception {
        /*
         * 时间语义
         * 发生顺序：EventTime(producer创建) - IngestionTime(Source算子,机器时间) - ProcessingTime(操作算子,机器时间)
         * flink1.12默认使用EventTime处理,让时间进度取决于数据本身而不是机器时钟,但是由于网络延迟和分布式(kafka多个分区之间数据无序)等原因,
         * 会产生乱序数据,导致窗口计算不准确,所以要设置延迟时间等待延迟数据,但是不能无限期等下去,必须要有某种机制保证到达特定时间后就触发窗口计算
         *
         * 水位线
         * 是一种延迟触发机制,watermark >= windowEnd就关闭窗口执行计算,表示流中时间戳小于等于水位线的数据都已到达,后面迟到的数据会丢弃
         * watermark = 进入flink的最大时间戳 - 最大延迟时间(手动设置) - 1ms(窗口左闭右开,[0,5s)其实是[0,4999ms])
         * 水位线是程序插入到流中的逻辑时钟,是一个特殊事件,会随着数据流向下流动,数据进来驱动processElement方法,水位线进来推高逻辑时钟
         * 水位线是事件时间世界的唯一标尺,窗口何时关闭、定时器何时触发
         * flink会在流的最开始插入一个负无穷大的水位线,在流的最末尾插入一个正无穷大的水位线,流结束了要闭合所有窗口触发计算
         *
         * 延迟时间
         * 太大会导致等待时间过久实时性很差,太小实时性高了但是会漏数据导致结果不准确,数据的乱序程度应该是符合正态分布的,可以先设置一个很小的t
         * hold住大部分延迟数据,剩下少量延迟太久的数据还可以通过allowLateness和sideOutput处理,这样就能同时兼顾结果准确性和实时性
         * 最大延迟时间设置经验：先不设置延迟时间将迟到数据都放到侧输出流,看看具体延迟情况
         * 标点水位线(Punctuated Watermark) 每条数据后面都有一个水位线,适用于数据稀少的情况
         * 定期水位线(Periodic Watermark) 隔几条数据后面会有一个水位线,适用于数据密集的情况(flink默认200ms插入一次)
         *
         * 迟到事件
         * 当水位线越过windowEnd,窗口关闭开始计算然后销毁,再进来的事件就是迟到事件,其乱序程度超出了水位线的预计,有三种处理方式
         * 1.直接丢弃时间戳小于水位线的事件(默认)
         * 2.allowLateness允许迟到事件更新已经计算完的窗口结果,当水位线越过windowEnd + allowLateness窗口才会销毁,窗口保存状态需要更多内存
         * 3.sideOutPut是最后的兜底操作,所有过期的延迟数据在窗口彻底销毁后会被放到另一条流即侧输出流
         * 乱序数据三重保证：window - watermark - allowLateness - sideOutput
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 插入水位线之前要保证流的并行度是1,不然就乱套了
        env.setParallelism(1);

        // 演示水位线
//        demo01(env);
//        demo02(env);
//        demo03(env);
        // 演示迟到事件
        demo04(env);

        // 启动任务
        env.execute();
    }

    private static void demo01(StreamExecutionEnvironment env) {
        // 需求：基于事件时间,统计每个元素每5秒钟出现次数
        env.socketTextStream("localhost",9999)
                // 事件时间要求数据源必须包含时间戳,先将输入元素`a 1`映射成Tuple2(a, 1000L)
                .map(new MapFunction<String, Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> map(String value) throws Exception {
                        String[] words = value.split("\\s");
                        return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000L);
                    }
                })
                // flink默认每200ms机器时间插入一次水位线,比如每1秒来一条数据,那么两条数据之间会插入5次相同数值的水位线,因为计算公式是固定的
                .assignTimestampsAndWatermarks(
                        // 设置乱序数据的最大延迟时间为5秒
                        WatermarkStrategy.<Tuple2<String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        // 从元素中提取时间戳(ms)作为事件时间
                        .withTimestampAssigner(new SerializableTimestampAssigner<Tuple2<String, Long>>() {
                            @Override
                            public long extractTimestamp(Tuple2<String, Long> element, long recordTimestamp) {
                                return element.f1;
                            }
                        })
                )
                // 分组
                .keyBy(r -> r.f0)
                // 开窗
                .window(TumblingEventTimeWindows.of(Time.seconds(5)))
                // 简单聚合一下,输入`a 1` `a 2` `a 3` `a 10`
                .process(new ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, Context context, Iterable<Tuple2<String, Long>> elements, Collector<String> out) throws Exception {
                        long start = context.window().getStart();
                        long end = context.window().getEnd();
                        long cnt = elements.spliterator().getExactSizeIfKnown();
                        out.collect("元素 " + key + " 在窗口 " + new Timestamp(start) + " ~ " + new Timestamp(end) + " 出现次数是 " + cnt);
                    }
                })
                .print();
    }

    private static void demo02(StreamExecutionEnvironment env) {
        // 需求：基于事件时间,不准开窗,只能使用KeyedProcessFunction,统计每个元素每5秒钟出现次数
        env.socketTextStream("localhost",9999)
                .map((MapFunction<String, Tuple2<String, Long>>) value -> {
                    String[] words = value.split("\\s");
                    return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000L);
                })
                // Tuple是flink给java设置的新的数据类型,使用lambda表达式会有泛型擦除问题,需要显示指定类型
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner(new SerializableTimestampAssigner<Tuple2<String, Long>>() {
                            @Override
                            public long extractTimestamp(Tuple2<String, Long> element, long recordTimestamp) {
                                return element.f1;
                            }
                        })
                )
                .keyBy(r -> r.f0)
                // 输入`a 1` `a 12` `a 23`查看输出结果,可以深刻理解水位线
                .process(new KeyedProcessFunction<String, Tuple2<String, Long>, String>() {
                    // 开始时水位线负无穷大,`a 1`进来后隔200ms生成新水位线-4001,`a 12`进来后隔200ms生成新水位线6999,触发`a 1`的定时器
                    @Override
                    public void processElement(Tuple2<String, Long> value, Context ctx, Collector<String> out) throws Exception {
                        out.collect("当前水位线是 " + ctx.timerService().currentWatermark());
                        out.collect("当前元素 " + value + " 到达时间 " + new Timestamp(value.f1));
                        // 注册5秒后的定时器
                        ctx.timerService().registerEventTimeTimer(value.f1 + 5000L);
                        out.collect("注册了一个即将在 " + new Timestamp(value.f1 + 5000L) + " 触发的定时器");
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        out.collect("定时器 " + new Timestamp(timestamp) + " 触发了");
                    }
                })
                .print();
    }

    private static void demo03(StreamExecutionEnvironment env) {
        // 设置自动插入水位线的时间间隔为30秒,默认是200毫秒,插入水位线的间隔根据经验设置,属于调优部分
        env.getConfig().setAutoWatermarkInterval(30 * 1000L);
        // 需求：基于事件时间,统计每个元素每5秒钟出现次数
        env.socketTextStream("localhost",9999)
                .map((MapFunction<String, Tuple2<String, Long>>) value -> {
                    String[] words = value.split("\\s");
                    return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000L);
                })
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(
                        // 假设进来的数据是有序的,最大延迟时间设置为0秒
                        WatermarkStrategy.<Tuple2<String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                // 从元素中提取时间戳(ms)作为事件时间
                                .withTimestampAssigner(new SerializableTimestampAssigner<Tuple2<String, Long>>() {
                                    @Override
                                    public long extractTimestamp(Tuple2<String, Long> element, long recordTimestamp) {
                                        return element.f1;
                                    }
                                })
                )
                // 分组
                .keyBy(r -> r.f0)
                // 开窗
                .window(TumblingEventTimeWindows.of(Time.seconds(5)))
                // 简单聚合一下,输入`a 1` `a 2` `a 5` `a 3` `a 4`,因为水位线30秒之后才更新为4999,所以这几个元素都会进入[0, 5)窗口
                .process(new ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, Context context, Iterable<Tuple2<String, Long>> elements, Collector<String> out) throws Exception {
                        // 窗口信息
                        long start = context.window().getStart();
                        long end = context.window().getEnd();
                        // 迭代器信息
                        long cnt = elements.spliterator().getExactSizeIfKnown();
                        // 收集结果
                        out.collect("元素 " + key + " 在窗口 " + new Timestamp(start) + " ~ " + new Timestamp(end) + " 出现次数是 " + cnt);
                    }
                })
                .print();
    }

    private static void demo04(StreamExecutionEnvironment env) {
        // 迟到事件处理方式
        SingleOutputStreamOperator<String> result = env.socketTextStream("localhost", 9999)
                // 将`a,1`映射成Tuple<a, 1000>
                .map(new MapFunction<String, Tuple2<String, Long>>() {
                    @Override
                    public Tuple2<String, Long> map(String value) throws Exception {
                        String[] words = value.split(",");
                        return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000);
                    }
                })
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        // 乱序数据设置最大延迟时间
                        WatermarkStrategy.<Tuple2<String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner(new SerializableTimestampAssigner<Tuple2<String, Long>>() {
                                    @Override
                                    public long extractTimestamp(Tuple2<String, Long> element, long recordTimestamp) {
                                        return element.f1;
                                    }
                                })
                )
                // 分组
                .keyBy(r -> r.f0)
                // 开窗
                .window(TumblingEventTimeWindows.of(Time.seconds(5)))
                // 允许迟到事件,水位线越过windowEnd + allowLateness时窗口销毁
                .allowedLateness(Time.seconds(5))
                // 设置侧输出流,此处必须写成{}匿名类的形式,不然报错：The types of the interface org.apache.flink.util.OutputTag could not be inferred
                .sideOutputLateData(new OutputTag<Tuple2<String, Long>>("late"){})
                // 简单聚合一下,输入`a,1` `a,2` `a,10` `a,1` `a,1` `a,15` `a,1`查看结果
                .process(new ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>() {
                    @Override
                    public void process(String s, Context context, Iterable<Tuple2<String, Long>> elements, Collector<String> out) throws Exception {
                        // 初始化一个窗口状态变量,可见范围是当前窗口,记录该方法是否第一次调用
                        ValueState<Boolean> valueState = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("is-first", Types.BOOLEAN));
                        // 判断窗口状态
                        if (valueState.value() == null) {
                            // 该方法第一次被调用,说明水位线越过windowEnd了,窗口关闭开始计算
                            out.collect("当前水位线：" + context.currentWatermark()
                                    + ",当前窗口：" + context.window().getStart() + " ~ " + context.window().getEnd()
                                    + ",窗口元素：" + elements);
                            // 更新窗口状态
                            valueState.update(true);
                        } else {
                            // 该方法不是第一次被调用,说明窗口关闭后又有迟到事件进来了,更新已经计算完的窗口结果
                            out.collect("当前水位线：" + context.currentWatermark()
                                    + ",当前窗口：" + context.window().getStart() + " ~ " + context.window().getEnd()
                                    + ",更新后的窗口元素：" + elements);
                        }
                    }
                });

        // 正常输出的结果
        result.print();
        // 输出到侧输出流的结果,侧输出流也是单例的,根据标签名查找
        result.getSideOutput(new OutputTag<Tuple2<String, Long>>("late"){}).print("sideOutput");
    }

}
