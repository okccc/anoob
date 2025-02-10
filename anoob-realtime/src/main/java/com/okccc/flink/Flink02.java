package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.source.lib.NumberSequenceSource;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.Random;

/**
 * @Author: okccc
 * @Date: 2021/9/7 下午4:49
 * @Desc: 状态变量和定时器
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/
 *
 * RichFunction
 * DataStream API提供的转换算子都有其Rich版本,都继承自RichFunction接口,有额外三个方法
 * open()：流的生命周期开始时执行,只执行一次,用于初始化操作,比如创建数据库连接、实例化状态变量,在map/join这些算子前被调用
 * getRuntimeContext()：处理数据时可以获取函数执行的上下文信息,比如任务并行度、子任务名称和索引、访问键控状态等
 * close()：流的生命周期结束时执行,只执行一次,做一些清理操作,比如关闭数据库连接、清空状态,在程序结束前被调用
 *
 * DataStream API提供的普通算子功能有限,flink提供了更底层的8大处理函数,都继承自RichFunction接口
 * ProcessFunction、KeyedProcessFunction、ProcessWindowFunction、ProcessAllWindowFunction
 * CoProcessFunction、ProcessJoinFunction、BroadcastProcessFunction、KeyedBroadcastProcessFunction
 *
 * KeyedProcessFunction<K, I, O>
 * K：分组字段类型
 * I：输入元素类型
 * O：输出元素类型
 * processElement(I value, Context ctx, Collector<O> out)
 * 每来一条数据都会驱动该方法运行,然后输出0/1/N个元素,ctx可以访问元素的时间戳和key、注册定时器、写侧输出流,out收集结果往下游发送
 * onTimer(long timestamp, OnTimerContext ctx, Collector<O> out)
 * 定时器timestamp触发时会驱动该方法运行,ctx和out功能同上
 *
 * Context和OnTimerContext持有的TimerService对象拥有以下方法
 * currentProcessingTime()返回当前处理时间
 * currentWatermark()返回当前水位线的时间戳
 * registerEventTimeTimer(long time)注册当前key的事件时间定时器,水位线>=定时器就会触发执行回调函数
 * deleteEventTimeTimer(long time)删除之前注册的事件时间定时器
 *
 * 总结：
 * 1.flink流处理最重要的两个概念：状态和时间,KeyedState可见范围是当前key,定时器本质上也是一个状态变量
 * 2.flink大招：KeyedProcessFunction + 状态变量 + 定时器,功能极其强大,是flatMap和reduce的终极加强版,也是flink精髓所在
 */
public class Flink02 {

    /**
     * 演示富函数
     */
    private static void testRichFunction(StreamExecutionEnvironment env) {
        env
                .socketTextStream("localhost", 9999)
                .map(new RichMapFunction<String, Integer>() {
                    @Override
                    public void open(Configuration parameters) {
                        // 子任务索引和并行度有关,一个并行度索引就是0,两个并行度索引就是0和1
                        System.out.println("map算子生命周期开始,当前子任务索引：" + getRuntimeContext().getTaskInfo());
                    }

                    @Override
                    public Integer map(String value) {
                        System.out.println("当前进来数据：" + value);
                        return Integer.parseInt(value) * 10;
                    }

                    @Override
                    public void close() {
                        System.out.println("map算子生命周期结束");
                    }
                })
                .keyBy(r -> true)
                .process(new KeyedProcessFunction<Boolean, Integer, Integer>() {
                    @Override
                    public void open(Configuration parameters) {
                        System.out.println("process算子生命周期开始,当前子任务索引：" + getRuntimeContext().getTaskInfo());
                    }

                    @Override
                    public void processElement(Integer value, KeyedProcessFunction<Boolean, Integer, Integer>.Context ctx, Collector<Integer> out) {
                        System.out.println("当前进来数据：" + value);
                        out.collect(value * value);
                    }

                    @Override
                    public void close() {
                        System.out.println("process算子生命周期结束");
                    }
                })
                .print();
    }

    /**
     * 演示定时器
     */
    private static void testTimer(StreamExecutionEnvironment env) {
        env
                .socketTextStream("localhost", 9999)
                .keyBy(r -> 1)
                .process(new KeyedProcessFunction<Integer, String, String>() {
                    @Override
                    public void processElement(String value, Context ctx, Collector<String> out) {
                        // 获取当前机器时间
                        long ts = ctx.timerService().currentProcessingTime();
                        out.collect("元素 " + value + " 到达时间 " + new Timestamp(ts));
                        // 注册一个5秒钟后的定时器 Setting timers is only supported on a keyed streams
                        // 定时器本质上也是状态,checkpoint是为了故障恢复,所以定期保存检查点时也会将定时器保存到状态后端
                        ctx.timerService().registerProcessingTimeTimer(ts + 5000L);
                        out.collect("注册一个即将在 " + new Timestamp(ts + 5000L) + " 触发的定时器");
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        out.collect("定时器 " + new Timestamp(timestamp) + " 触发了");
                    }
                })
                .print();
    }

    /**
     * 演示ValueState: 每隔5秒统计一次所有数据平均值
     */
    private static void testValueState(StreamExecutionEnvironment env) {
        // 分析: 每隔5秒就是要设置5秒后的定时器,学完窗口函数后可以直接开窗,平均值需要累加器
        env
                .fromSource(new NumberSequenceSource(1, 10), WatermarkStrategy.noWatermarks(), "Number Source")
                .keyBy(r -> true)
                .process(new KeyedProcessFunction<Boolean, Long, Double>() {
                    // 声明一个ValueState作为累加器,Tuple2的参数1是累加器的sum值,参数2是元素个数
                    private ValueState<Tuple2<Long, Integer>> acc;
                    // 声明一个ValueState作为定时器
                    private ValueState<Long> timer;

                    @Override
                    public void open(Configuration parameters) {
                        // 实例化状态变量,声明状态时还无法获取运行上下文,必须等到open()生命周期初始化才可以
                        acc = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("acc", Types.TUPLE(Types.LONG, Types.INT)));
                        timer = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("timer", Types.LONG));
                    }

                    @Override
                    public void processElement(Long value, KeyedProcessFunction<Boolean, Long, Double>.Context ctx, Collector<Double> out) throws Exception {
                        // 当第一条数据进来时,状态变量的值都是null
                        long currentProcessingTime = ctx.timerService().currentProcessingTime();
                        System.out.println("当前进来数据 " + value + " 当前处理时间 " + new Timestamp(currentProcessingTime));

                        // 判断累加器状态
                        if (acc.value() == null) {
                            // 第一条数据作为初始值
                            acc.update(Tuple2.of(value, 1));
                        } else {
                            // 后续数据进行滚动聚合
                            acc.update(Tuple2.of(acc.value().f0 + value, acc.value().f1 + 1));
                        }
                        System.out.println("当前累加器 " + acc.value());

                        // 这里输入类型是Long而输出类型是Double,类型可以随便定义比reduce灵活,并且还能利用定时器设置输出频率
//                        out.collect((double) acc.value().f0 / acc.value().f1);

                        // 判断定时器状态
                        if (timer.value() == null) {
                            // 没有定时器就创建
                            long ts = ctx.timerService().currentProcessingTime() + 5000L;
                            ctx.timerService().registerProcessingTimeTimer(ts);
                            // 更新定时器状态
                            timer.update(ts);
                        }
                        System.out.println("当前定时器 " + new Timestamp(timer.value()));

                        // 每隔1秒发送一条数据
                        Thread.sleep(1000L);
                    }

                    @Override
                    public void onTimer(long timestamp, KeyedProcessFunction<Boolean, Long, Double>.OnTimerContext ctx, Collector<Double> out) throws Exception {
                        if (acc.value() != null) {
                            // 收集结果往下游发送
                            out.collect((double) acc.value().f0 / acc.value().f1);
                            // 清空累加器,取决于统计的是所有数据还是最近5秒数据
//                            acc.clear();
                            // 清空定时器,不然下一个5秒的数据进来时检查定时器状态不为null就不会更新,那么就只会触发第一次
                            timer.clear();
                        }
                    }
                })
                .print();
    }

    /**
     * 演示ListState: 每隔5秒统计一次所有数据平均值
     */
    private static void testListState(StreamExecutionEnvironment env) {
        env
                .fromSource(new NumberSequenceSource(1, 10), WatermarkStrategy.noWatermarks(), "Number Source")
                .keyBy(r -> 1)
                .process(new KeyedProcessFunction<Integer, Long, Double>() {
                    // 声明一个ListState作为累加器,列表状态会保存流中所有数据,占用更多内存,不如值状态效率高
                    private ListState<Long> acc;
                    // 声明一个ValueState作为定时器
                    private ValueState<Long> timer;

                    @Override
                    public void open(Configuration parameters) {
                        // 实例化状态变量
                        acc = getRuntimeContext().getListState(
                                new ListStateDescriptor<>("acc", Types.LONG));
                        timer = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("timer", Types.LONG));
                    }

                    @Override
                    public void processElement(Long value, Context ctx, Collector<Double> out) throws Exception {
                        // 数据进来了
                        long currentProcessingTime = ctx.timerService().currentProcessingTime();
                        System.out.println("当前进来数据 " + value + " 当前处理时间 " + new Timestamp(currentProcessingTime));

                        // 每来一条数据就添加到累加器
                        acc.add(value);
                        System.out.println("当前累加器 " + acc.get());

                        // 判断定时器状态
                        if (timer.value() == null) {
                            // 没有定时器就创建
                            long ts = ctx.timerService().currentProcessingTime() + 5000L;
                            ctx.timerService().registerProcessingTimeTimer(ts);
                            // 更新定时器状态
                            timer.update(ts);
                        }
                        System.out.println("当前定时器 " + new Timestamp(timer.value()));

                        // 每隔1秒发送一条数据
                        Thread.sleep(1000L);
                    }

                    @Override
                    public void onTimer(long timestamp, KeyedProcessFunction<Integer, Long, Double>.OnTimerContext ctx, Collector<Double> out) throws Exception {
                        if (acc.get() != null) {
                            // 遍历累加器求平均值
                            int sum = 0;
                            int cnt = 0;
                            for (Long l : acc.get()) {
                                sum += l;
                                cnt += 1;
                            }
                            // 收集结果往下游发送
                            out.collect((double) sum / cnt);
                            // 清空定时器,不然下一个5秒的数据进来时检查定时器状态不为null就不会更新,那么就只会触发第一次
                            timer.clear();
                        }
                    }
                })
                .print();
    }

    /**
     * 演示MapState: 每隔5秒统计一次网站页面平均访问次数 = 总访问次数/页面数
     */
    private static void testMapState(StreamExecutionEnvironment env) {
        String[] arr = {"/home", "/item", "/cart", "/history", "/fav"};
        Random random = new Random();

        env
                .fromSource(
                        new DataGeneratorSource<>(
                                new GeneratorFunction<Long, String>() {
                                    @Override
                                    public String map(Long value) {
                                        return arr[random.nextInt(arr.length)];
                                    }
                                }, 10, Types.STRING
                        ), WatermarkStrategy.noWatermarks(), "Data Source"
                )
                .keyBy(r -> 1)
                .process(new KeyedProcessFunction<Integer, String, Double>() {
                    // 声明一个MapState作为累加器,key是页面,value是其访问次数
                    // hash表是最经典的数据结构,复杂度O(1),读写速度非常快,hbase/redis/es都有用到
                    // Tuple2<T0, T1>的T0无法去重,而HashMap<key, value>的key是可以去重的,复杂的统计需求往往需要借助HashMap
                    private MapState<String, Integer> acc;
                    // 声明一个ValueState作为定时器
                    private ValueState<Long> timer;

                    @Override
                    public void open(Configuration parameters) {
                        // 实例化状态变量
                        acc = getRuntimeContext().getMapState(
                                new MapStateDescriptor<>("acc", Types.STRING, Types.INT));
                        timer = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("timer", Types.LONG));
                    }

                    @Override
                    public void processElement(String value, Context ctx, Collector<Double> out) throws Exception {
                        // 数据进来了
                        long currentProcessingTime = ctx.timerService().currentProcessingTime();
                        System.out.println("当前进来数据：" + value + ", 当前处理时间：" + new Timestamp(currentProcessingTime));

                        // 判断累加器状态
                        if (!acc.contains(value)) {
                            // 页面不存在,第一条数据作为初始值
                            acc.put(value, 1);
                        } else {
                            // 页面已存在,后续数据进行滚动聚合
                            acc.put(value, acc.get(value) + 1);
                        }
                        System.out.println("当前累加器是：" + acc.entries());

                        // 判断定时器状态
                        if (timer.value() == null) {
                            // 没有定时器就创建
                            long ts = ctx.timerService().currentProcessingTime() + 5000L;
                            ctx.timerService().registerProcessingTimeTimer(ts);
                            // 更新定时器状态
                            timer.update(ts);
                        }
                        System.out.println("当前定时器：" + new Timestamp(timer.value()));

                        // 每隔1秒发送一条数据
                        Thread.sleep(1000L);
                    }

                    @Override
                    public void onTimer(long timestamp, KeyedProcessFunction<Integer, String, Double>.OnTimerContext ctx, Collector<Double> out) throws Exception {
                        // 遍历累加器求平均值
                        int pageNum = 0;
                        int pvSum = 0;
                        for (String page : acc.keys()) {
                            pageNum += 1;
                            pvSum += acc.get(page);
                        }
                        // 定时器触发时输出结果
                        out.collect((double) pvSum / pageNum);
                        // 清空定时器,不然下一个5秒的数据进来时检查定时器状态不为null就不会更新,那么就只会触发第一次
                        timer.clear();
                    }
                })
                .print("MapState");
    }

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

//        testRichFunction(env);
//        testTimer(env);
//        testValueState(env);
//        testListState(env);
        testMapState(env);

        // 启动任务
        env.execute();
    }
}
