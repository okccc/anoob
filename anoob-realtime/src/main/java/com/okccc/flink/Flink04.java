package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2021/9/13 15:22:06
 * @Desc: 事件时间和水位线
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/event-time/generating_watermarks/
 *
 * 时间语义
 * 发生顺序：EventTime(事件时间) -> IngestionTime(机器时间) -> ProcessingTime(处理时间)
 * 使用ProcessingTime处理数据就不存在延迟,但窗口计算结果不准,因为网络延迟和分布式(kafka分区间数据无序)等原因很容易产生乱序数据
 * 所以流处理的时间进度应该取决于数据本身而不是系统时间,flink1.12开始默认使用EventTime处理数据,并且针对乱序数据专门引入水位线的概念
 *
 * 水位线
 * watermark = 进入flink的最大时间戳 - 最大延迟时间 - 1ms(窗口左闭右开,[0,5s)其实是[0,4999ms])
 * 水位线是程序自动插入到流中的逻辑时钟,随着数据向下流动,是事件时间世界的唯一标尺,决定了窗口何时关闭、定时器何时触发
 * 水位线是一种延迟触发机制,当水位线越过windowEnd且窗口内有数据就关闭窗口开始计算,表示流中低于水位线的数据都已到达,后面的迟到数据直接丢弃
 *
 * 标点水位线(Punctuated Watermark)：每条数据后面都有一个水位线,适用于数据稀少的情况
 * 定期水位线(Periodic Watermark)：隔几条数据后面会有一个水位线,适用于数据密集的情况(flink默认200ms插入一次)
 * flink会在流的最开始插入一个负无穷大的水位线,在流的最末尾插入一个正无穷大的水位线,流结束了要闭合所有窗口触发计算
 *
 * 延迟时间
 * 太大会等很久导致实时性很差,太小会漏数据导致结果不准确,数据的乱序程度应该是符合正态分布的,可以先设置一个很小的延迟时间
 * hold住大部分延迟数据,剩下少量延迟太久的数据还可以通过allowLateness和sideOutput处理,这样就能同时兼顾准确性和实时性
 * 最大延迟时间设置经验：先不设置延迟时间将迟到数据都放到侧输出流,看看具体延迟情况
 *
 * 迟到事件
 * 当水位线越过windowEnd窗口关闭开始计算然后销毁,再进来的数据就是迟到事件,其乱序程度超出了水位线的预计,有三种处理方式
 * 1.直接丢弃时间戳小于水位线的事件(默认)
 * 2.allowLateness允许迟到事件更新已经累加完的窗口结果,当水位线越过windowEnd + allowLateness时窗口才会销毁,窗口保存状态需要更多内存
 * 3.sideOutPut是最后的兜底操作,所有过期的迟到数据在窗口彻底销毁后可以放到侧输出流
 * 乱序数据三重保证：watermark - allowLateness - sideOutput
 */
public class Flink04 {

    /**
     * 水位线决定窗口何时关闭
     */
    private static void testWatermark01(StreamExecutionEnvironment env) {
        env
                .socketTextStream("localhost", 9999)
                // 事件时间要求数据源必须包含时间戳,先将输入元素`a 1`映射成Tuple2(a, 1000L)
                .map((MapFunction<String, Tuple2<String, Long>>) value -> {
                    String[] words = value.split("\\s");
                    return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000L);
                })
                // Tuple2是flink给java设置的新数据类型,使用lambda表达式会泛型丢失,需要显式指定返回类型,或者直接使用匿名内部类
                // 比如按照Tuple2类型的字段进行keyBy会报错：The generic type parameters of 'Tuple2' are missing.
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                // window算子使用事件时间时必须分配时间戳和生成水位线
                .assignTimestampsAndWatermarks(
                        // 设置乱序数据的最大延迟时间为5秒
                        WatermarkStrategy.<Tuple2<String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                // 从元素中提取时间戳(ms)作为事件时间
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1)
                )
                // 分组
                .keyBy(r -> r.f0)
                // 开窗
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                // 输入`a 1` `a 2` `a 10` `a 1` `a 6` `a 15` `a 9`观察迭代器中的元素
                .process(new ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>.Context context, Iterable<Tuple2<String, Long>> elements, Collector<String> out) throws Exception {
                        // 当水位线越过windowEnd且窗口内有数据就关闭窗口开始计算,后面时间戳小于水位线的数据就进不来了
                        System.out.println("current watermark is: " + context.currentWatermark());
                        // 获取窗口信息
                        long windowStart = context.window().getStart();
                        long windowEnd = context.window().getEnd();
                        // 收集结果往下游发送
                        out.collect(key + ": [" + windowStart + ", " + windowEnd + ") = " + elements);
                    }
                })
                .print();
    }

    /**
     * 水位线决定定时器何时触发
     */
    public static void testWatermark02(StreamExecutionEnvironment env) {
        env
                .socketTextStream("localhost",9999)
                .map((MapFunction<String, Tuple2<String, Long>>) value -> {
                    String[] words = value.split("\\s");
                    return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000L);
                })
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1)
                )
                .keyBy(r -> r.f0)
                // 输入`a 1` `a 2` `a 1` `a 12` `a 23`查看输出结果,可以深刻理解水位线
                .process(new KeyedProcessFunction<String, Tuple2<String, Long>, String>() {
                    @Override
                    public void processElement(Tuple2<String, Long> value, Context ctx, Collector<String> out) throws Exception {
                        // 流开始时水位线负无穷大,数据进来后会推高水位线,`a 1`进来变成-4001,`a 12`进来变成6999,触发`a 1`的定时器
                        out.collect("当前水位线 " + ctx.timerService().currentWatermark());
                        out.collect("当前进来数据 " + value);
                        // 注册5秒后的定时器
                        ctx.timerService().registerEventTimeTimer(value.f1 + 5000L);
                        out.collect("注册 " + (value.f1 + 5000L) + " 触发的定时器");
                        System.out.println("=========================================");
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        out.collect("定时器 " + timestamp + " 触发了!");
                        System.out.println("=========================================");
                    }
                })
                .print();
    }

    /**
     * 修改插入水位线的时间间隔
     */
    private static void testWatermark03(StreamExecutionEnvironment env) {
        // flink默认每200ms插入一次水位线,比如一秒钟来一条数据,那么两条数据之间会插入5次相同数值的水位线,因为计算公式是固定的
        // 设置自动插入水位线的时间间隔为30秒,插入水位线的间隔根据经验设置,属于调优部分
        env.getConfig().setAutoWatermarkInterval(30000L);
        env
                .socketTextStream("localhost", 9999)
                .map((MapFunction<String, Tuple2<String, Long>>) value -> {
                    String[] words = value.split("\\s");
                    return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000L);
                })
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1)
                )
                .keyBy(r -> r.f0)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                // 输入`a 1` `a 2` `a 10` `a 3` `a 4`,因为水位线30秒后才更新为4999,所以1、2、3、4都会进入[0, 5)窗口
                .process(new ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, Context context, Iterable<Tuple2<String, Long>> elements, Collector<String> out) throws Exception {
                        // 当水位线越过windowEnd且窗口内有数据就关闭窗口开始计算,后面时间戳小于水位线的数据就进不来了
                        System.out.println("current watermark is: " + context.currentWatermark());
                        // 获取窗口信息
                        long windowStart = context.window().getStart();
                        long windowEnd = context.window().getEnd();
                        // 收集结果往下游发送
                        out.collect(key + ": [" + windowStart + ", " + windowEnd + ") = " + elements);
                    }
                })
                .print();
    }

    /**
     * 演示迟到事件和侧输出流
     */
    private static void testWatermark04(StreamExecutionEnvironment env) {
        // 侧输出流标签必须写成{}匿名类的形式,不然报错：The types of the interface org.apache.flink.util.OutputTag could not be inferred
        OutputTag<Tuple2<String, Long>> outputTag = new OutputTag<>("dirty") {};
        SingleOutputStreamOperator<String> result = env
                .socketTextStream("localhost", 9999)
                .map((MapFunction<String, Tuple2<String, Long>>) value -> {
                    String[] words = value.split("\\s");
                    return Tuple2.of(words[0], Long.parseLong(words[1]) * 1000);
                })
                .returns(Types.TUPLE(Types.STRING, Types.LONG))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Tuple2<String, Long>>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((element, recordTimestamp) -> element.f1)
                )
                .keyBy(r -> r.f0)
                .window(TumblingEventTimeWindows.of(Duration.ofSeconds(5)))
                // 允许迟到事件,水位线越过windowEnd + allowLateness时窗口才销毁,后面再进来的迟到数据就放侧输出流
                .allowedLateness(Duration.ofSeconds(5))
                // 设置侧输出流
                .sideOutputLateData(outputTag)
                // 输入`a 1` `a 2` `a 10` `a 1` `a 2` `a 15` `a,1` `a 2`查看结果
                .process(new ProcessWindowFunction<Tuple2<String, Long>, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, Context context, Iterable<Tuple2<String, Long>> elements, Collector<String> out) throws Exception {
                        // 当水位线越过windowEnd且窗口内有数据就关闭窗口开始计算,但是不会马上关闭窗口,还会继续等待迟到事件
                        System.out.println("current watermark is: " + context.currentWatermark());
                        // 获取窗口信息
                        long windowStart = context.window().getStart();
                        long windowEnd = context.window().getEnd();
                        // 收集结果往下游发送
                        out.collect(key + ": [" + windowStart + ", " + windowEnd + ") = " + elements);
                    }
                });
        // 正常输出的结果
        result.print();
        // 输出到侧输出流的结果,侧输出流也是单例的,根据标签名查找
        result.getSideOutput(outputTag).print("dirty");
    }

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 插入水位线之前要保证流的并行度是1,不然就乱套了
        env.setParallelism(1);

//        testWatermark01(env);
//        testWatermark02(env);
//        testWatermark03(env);
        testWatermark04(env);

        // 启动任务
        env.execute();
    }
}
