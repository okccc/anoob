package com.okccc.flink;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;

/**
 * Author: okccc
 * Date: 2021/9/9 下午5:05
 * Desc: flink窗口
 */
public class Flink03 {
    public static void main(String[] args) throws Exception {
        /*
         * flink也有窗口函数,有些业务场景必须要攒一批数据做批处理
         * keyBy()是分组后聚合,keyBy() + window()是分组并各自开窗后再聚合,分组就是将数据分到不同的流
         *
         * 滚动窗口：窗口大小固定,没有重叠,一个元素只会属于一个窗口
         * 滑动窗口：窗口大小固定,有滑动间隔所以会有重叠,一个元素可以属于多个窗口
         * 会话窗口：前两者统计网站pv/uv这种固定时间,而用户访问行为是不固定的,超时时间内没收到数据就生成新窗口,只有flink支持会话窗口
         *
         * 窗口处理函数
         * ProcessWindowFunction<IN, OUT, KEY, W extends Window>
         * IN： 输入元素类型
         * OUT：输出元素类型
         * KEY：分组字段类型
         * W：  窗口类型
         * process(KEY key, Context context, Iterable<IN> elements, Collector<OUT> out)
         * 窗口关闭时会驱动其运行,key是分组字段,context可以访问窗口元数据信息,elements保存窗口内所有元素,out收集结果往下游发送
         *
         * 增量聚合函数
         * AggregateFunction<IN, ACC, OUT>
         * IN： 输入元素类型
         * ACC：累加器类型
         * OUT：输出元素类型
         * createAccumulator()：创建累加器
         * add(IN value, ACC accumulator)：累加器思想,每来一条数据就滚动聚合,不用收集全部数据但是无法访问窗口信息,不知道属于哪个窗口
         * getResult(ACC accumulator)：窗口关闭时返回累加器
         * merge(ACC a, ACC b)：合并累加器,一般用不到
         *
         * 总结：
         * 1.开窗需求通常都是结合两者一起使用,keyBy() + window() + aggregate(AggregateFunction(), ProcessWindowFunction())
         * 窗口闭合时,增量聚合函数会将累加结果发送给窗口处理函数,这样既不需要收集全部数据又能访问窗口信息,除非是排序和求中位数这种全局操作
         * 2.window()是flink语法糖,底层就是KeyedProcessFunction + 状态变量 + 定时器,一般直接开窗就行,除非特别复杂的需求才会用大招
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        demo01(env);
//        demo02(env);
//        demo03(env);
//        demo04(env);

        // 启动任务
        env.execute();
    }

    // 演示ProcessWindowFunction
    private static void demo01(StreamExecutionEnvironment env) {
        // 需求：使用窗口处理函数,统计每个用户每5秒钟的pv
        // 分析：按照用户分组,窗口大小5秒,pv对应窗口处理函数的迭代器,所以是keyBy(user) + window(5s) + process(Iterable)
        env.addSource(new Flink01.UserActionSource())
                // 分组：按照用户分组
                .keyBy(r -> r.user)
                // 开窗：滚动窗口,窗口大小为5秒
                .window(TumblingProcessingTimeWindows.of(Time.seconds(5)))
                // 全窗口聚合
                .process(new ProcessWindowFunction<Flink01.Event, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, Context context, Iterable<Flink01.Event> elements, Collector<String> out) throws Exception {
                        // 获取窗口信息
                        long start = context.window().getStart();
                        long end = context.window().getEnd();
                        // 获取迭代器中的元素个数
                        long cnt = elements.spliterator().getExactSizeIfKnown();
                        // 输出结果
                        out.collect(key + ": " + new Timestamp(start) + " ~ " + new Timestamp(end) + " = " + cnt);
                    }
                })
                .print();
    }

    // 演示AggregateFunction
    private static void demo02(StreamExecutionEnvironment env) {
        // 需求：使用增量聚合函数,统计每个用户每5秒钟的pv
        // 分析：按照用户分组,窗口大小5秒,pv对应增量聚合函数的累加器,所以是keyBy(user) + window(5s) + aggregate(add)
        env.addSource(new Flink01.UserActionSource())
                .keyBy(r -> r.user)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(5)))
                .aggregate(new MyAggregateFunction())
                .print();
    }

    // 演示两者结合使用
    private static void demo03(StreamExecutionEnvironment env) {
        // 需求：结合两者使用,统计每个用户每5秒钟的pv
        // 分析：先增量聚合再全窗口处理,前者的输出元素作为后者的输入元素
        env.addSource(new Flink01.UserActionSource())
                .keyBy(r -> r.user)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(5)))
                .aggregate(new MyAggregateFunction(), new MyProcessWindowFunction())
                .print();
    }

    // 自定义增量聚合函数
    public static class MyAggregateFunction implements AggregateFunction<Flink01.Event, Integer, Integer> {
        @Override
        public Integer createAccumulator() {
            return 0;
        }
        @Override
        public Integer add(Flink01.Event value, Integer accumulator) {
            // 定义累加规则,返回更新后的累加器
            System.out.println("current element is: " + value);
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
    }

    // 自定义窗口处理函数,输入类型是前面增量聚合函数的输出类型
    public static class MyProcessWindowFunction extends ProcessWindowFunction<Integer, String, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<Integer> elements, Collector<String> out) throws Exception {
            // 获取窗口信息
            long start = context.window().getStart();
            long end = context.window().getEnd();
            // 迭代器只包含一个元素,就是增量聚合的结果
            Integer cnt = elements.iterator().next();
            // 收集结果往下游发送
            out.collect(key + ": " + new Timestamp(start) + " ~ " + new Timestamp(end) + " = " + cnt);
        }
    }

    // 使用KeyedProcessFunction模拟窗口
    private static void demo04(StreamExecutionEnvironment env) {
        // 需求：不准开窗,只能使用KeyedProcessFunction,统计每个用户每5秒钟的pv
        // 分析：按照用户分组,模拟一个5秒的窗口,由定时器来触发计算,pv值对应累加器,所以是keyBy(user) + MapState<窗口, 累加器>
        env.addSource(new Flink01.UserActionSource())
                .keyBy(r -> r.user)
                .process(new KeyedProcessFunction<String, Flink01.Event, String>() {
                    // 声明一个MapState,key是窗口开始时间,value是累加器
                    private MapState<Long, Integer> mapState;
                    // 定义窗口大小为5秒
                    private final Long windowSize = 5000L;

                    @Override
                    public void open(Configuration parameters) {
                        // 实例化状态变量
                        mapState = getRuntimeContext().getMapState(
                                new MapStateDescriptor<>("windowStart-pv", Types.LONG, Types.INT));
                    }

                    @Override
                    public void processElement(Flink01.Event value, Context ctx, Collector<String> out) throws Exception {
                        System.out.println("当前进来的元素是：" + value);
                        // 计算当前元素所属窗口区间
                        long curTime = ctx.timerService().currentProcessingTime();
                        // 比如7s所属的窗口是[5s, 10s)
                        long windowStart = curTime - curTime % windowSize;
                        long windowEnd = windowStart + windowSize;

                        // 判断累加器状态
                        if (!mapState.contains(windowStart)) {
                            // 窗口不存在
                            mapState.put(windowStart, 1);
                        } else {
                            // 窗口已存在
                            mapState.put(windowStart, mapState.get(windowStart) + 1);
                        }

                        // 注册窗口结束时间触发的定时器,后面数据进来发现当前窗口已经有定时器就不会再注册
                        ctx.timerService().registerProcessingTimeTimer(windowEnd - 1L);
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        // 窗口区间
                        long windowEnd = timestamp + 1L;
                        long windowStart = windowEnd - windowSize;
                        // 累加器的值
                        int cnt = mapState.get(windowStart);
                        // 收集结果
                        out.collect("用户 " + ctx.getCurrentKey() + " 在窗口 " + new Timestamp(windowStart)
                                + " ~ " + new Timestamp(windowEnd) + " 的pv是 " + cnt);
                        // 窗口用完就清空,节约内存
                        mapState.remove(windowStart);
                    }
                })
                .print();
    }

}
