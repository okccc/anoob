package com.okccc.flink;

import com.okccc.source.UserActionSource;
import com.okccc.bean.Event;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
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
 * @Author: okccc
 * @Date: 2021/9/9 下午5:05
 * @Desc: flink窗口
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.15/zh/docs/dev/datastream/operators/windows/
 *
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
public class Flink03 {

    /**
     * 演示ProcessWindowFunction: 统计每个用户每5秒钟的pv
     */
    private static void testProcess(StreamExecutionEnvironment env) {
        env
                .addSource(new UserActionSource())
                .keyBy(r -> r.user)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(5)))
                .process(new ProcessWindowFunction<Event, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, ProcessWindowFunction<Event, String, String, TimeWindow>.Context context, Iterable<Event> elements, Collector<String> out) throws Exception {
                        // 获取窗口信息
                        long start = context.window().getStart();
                        long end = context.window().getEnd();
                        // 迭代器保存了当前窗口内的所有元素
                        System.out.println(key + ": " + elements);
                        // 获取迭代器中的元素个数
                        long cnt = elements.spliterator().getExactSizeIfKnown();
                        // 收集结果往下游发送
                        out.collect(key + ": [" + new Timestamp(start) + " ~ " + new Timestamp(end) + ") = " + cnt);
                    }
                })
                .print();
    }

    /**
     * 演示ProcessWindowFunction结合AggregateFunction: 统计每个用户每5秒钟的pv
     */
    private static void testAggregate(StreamExecutionEnvironment env) {
        env
                .addSource(new UserActionSource())
                // 分组：按照用户分组
                .keyBy(r -> r.user)
                // 开窗：滚动窗口,窗口大小为5秒
                .window(TumblingProcessingTimeWindows.of(Time.seconds(5)))
                // 先增量聚合再全窗口处理,前者的输出作为后者的输入
                .aggregate(
                        new AggregateFunction<Event, Integer, Integer>() {
                            @Override
                            public Integer createAccumulator() {
                                return 0;
                            }
                            @Override
                            public Integer add(Event value, Integer accumulator) {
                                // 定义累加规则,返回更新后的累加器
                                System.out.println("当前进来数据: " + value);
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
                        new ProcessWindowFunction<Integer, String, String, TimeWindow>() {
                            @Override
                            public void process(String key, ProcessWindowFunction<Integer, String, String, TimeWindow>.Context context, Iterable<Integer> elements, Collector<String> out) throws Exception {
                                // 当时间越过windowEnd时窗口关闭,驱动该方法执行,计算完后窗口销毁
                                System.out.println("current time is: " + context.currentProcessingTime());
                                // 获取窗口信息
                                long windowStart = context.window().getStart();
                                long windowEnd = context.window().getEnd();
                                // 迭代器只包含一个元素,就是增量聚合的结果
                                Integer cnt = elements.iterator().next();
                                // 收集结果往下游发送
                                out.collect(key + ": [" + new Timestamp(windowStart) + ", " + new Timestamp(windowEnd) + ") = " + cnt);
                            }
                        }
                )
                .print();
    }

    /**
     * 使用定时器模拟窗口: 统计每个用户每5秒钟的pv
     */
    public static void testMockWindow(StreamExecutionEnvironment env) {
        env
                .addSource(new UserActionSource())
                .keyBy(r -> r.user)
                .process(new KeyedProcessFunction<String, Event, String>() {
                    // 声明一个ValueState作为累加器
                    private ValueState<Integer> acc;
                    // 声明一个ValueState作为定时器
                    private ValueState<Long> timer;
                    // 设置窗口大小为5秒
                    private final long windowSize = 5000L;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 实例化状态变量
                        acc = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("acc", Types.INT));
                        timer = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("timer", Types.LONG));
                    }

                    @Override
                    public void processElement(Event value, KeyedProcessFunction<String, Event, String>.Context ctx, Collector<String> out) throws Exception {
                        // 每来一条数据都会驱动该方法运行
                        System.out.println("当前进来数据：" + value);

                        // 计算当前数据所属窗口区间
                        Long curTime = value.timestamp;
                        // 比如7s所属的窗口是[5s, 10s)
                        long windowStart = curTime - curTime % windowSize;
                        long windowEnd = windowStart + windowSize;

                        // 判断累加器状态
                        if (acc.value() == null) {
                            acc.update(1);
                        } else {
                            acc.update(acc.value() + 1);
                        }

                        // 判断定时器状态
                        if (timer.value() == null) {
                            // 没有定时器就创建
                            ctx.timerService().registerProcessingTimeTimer(windowEnd - 1L);
                            // 更新定时器状态
                            timer.update(windowEnd);
                        }
                    }

                    @Override
                    public void onTimer(long timestamp, KeyedProcessFunction<String, Event, String>.OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        // 获取窗口信息
                        long windowEnd = timestamp + 1L;
                        long windowStart = windowEnd - windowSize;
                        // 获取累加器的值
                        Integer cnt = acc.value();
                        // 收集结果往下游发送
                        out.collect(ctx.getCurrentKey() + ": [" + new Timestamp(windowStart) + ", " + new Timestamp(windowEnd) + ") = " + cnt);
                        // 清空累加器,不然下一个窗口的数据进来时检查累加器状态不为null就会在上一个窗口的基础上累加
                        acc.clear();
                        // 清空定时器,不然下一个窗口的数据进来时检查定时器状态不为null就不会更新,那么就只会触发第一个窗口
                        timer.clear();
                    }
                })
                .print();
    }

    private static void testMockWindow02(StreamExecutionEnvironment env) {
        env
                .addSource(new UserActionSource())
                .keyBy(r -> r.user)
                .process(new KeyedProcessFunction<String, Event, String>() {
                    // 声明一个MapState作为当前窗口的累加器,key是窗口开始时间,value是累加器
                    private MapState<Long, Integer> mapState;
                    // 声明一个ValueState作为定时器
                    private ValueState<Long> timer;
                    // 设置窗口大小为5秒
                    private final Long windowSize = 5000L;

                    @Override
                    public void open(Configuration parameters) {
                        // 实例化状态变量
                        mapState = getRuntimeContext().getMapState(
                                new MapStateDescriptor<>("mapState", Types.LONG, Types.INT));
                        timer = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("timer", Types.LONG));
                    }

                    @Override
                    public void processElement(Event value, Context ctx, Collector<String> out) throws Exception {
                        // 每来一条数据都会驱动该方法运行
                        System.out.println("当前进来数据：" + value);

                        // 计算当前数据所属窗口区间
                        Long curTime = value.timestamp;
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

                        // 判断定时器状态
                        if (timer.value() == null) {
                            // 没有定时器就创建
                            ctx.timerService().registerProcessingTimeTimer(windowEnd - 1L);
                            // 更新定时器状态
                            timer.update(windowEnd);
                        }
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        // 获取窗口信息
                        long windowEnd = timestamp + 1L;
                        long windowStart = windowEnd - windowSize;
                        // 获取累加器的值
                        int cnt = mapState.get(windowStart);
                        // 收集结果往下游发送
                        out.collect( ctx.getCurrentKey() + ": [" + new Timestamp(windowStart) + ", " + new Timestamp(windowEnd) + ") = " + cnt);
                        // 清空当前窗口的累加器,不然下一个窗口的数据会在前面窗口基础上累加
                        mapState.remove(windowStart);
                        // 清空定时器,不然下一个窗口的数据进来时检查定时器状态不为null就不会更新,那么就只会触发第一个窗口
                        timer.clear();
                    }
                })
                .print();
    }

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

//        testProcess(env);
//        testAggregate(env);
        testMockWindow(env);
//        testMockWindow02(env);

        // 启动任务
        env.execute();
    }
}
