package com.okccc.warehouse.flink;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;

/**
 * Author: okccc
 * Date: 2021/9/7 下午4:49
 * Desc: flink处理函数
 */
public class Flink02 {
    public static void main(String[] args) throws Exception {
        /*
         * RichFunction
         * DataStream API提供的转换算子都有其Rich版本,都继承自RichFunction接口,有额外三个方法
         * open()：生命周期初始化,比如创建数据库连接,在map/filter这些算子之前被调用
         * getRuntimeContext()：处理数据时可以获取函数执行的上下文信息,比如任务并行度、子任务名称和索引、访问分区状态等
         * close()：生命周期结束,比如关闭数据库连接、清空状态
         *
         * DataStream API提供的普通算子功能有限,flink提供了更底层的8大处理函数,都继承自RichFunction接口
         * ProcessFunction/KeyedProcessFunction/ProcessWindowFunction/ProcessAllWindowFunction/
         * CoProcessFunction/ProcessJoinFunction/BroadcastProcessFunction/KeyedBroadcastProcessFunction

         * KeyedProcessFunction<K, I, O>
         * K：分组字段类型
         * I：输入元素类型
         * O：输出元素类型
         * processElement(I value, Context ctx, Collector<O> out)
         * 每来一条数据都会驱动其运行,然后输出0/1/N个元素,ctx可以访问元素的时间戳和key、注册定时器、写侧输出流,out收集结果往下游发送
         * onTimer(long timestamp, OnTimerContext ctx, Collector<O> out)
         * 定时器timestamp会驱动该回调函数运行,ctx和out功能同上
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

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

//        demo01(env);
//        demo02(env);
//        demo03(env);
//        demo04(env);
        demo05(env);

        // 启动任务
        env.execute();
    }

    // 演示富函数
    private static void demo01(StreamExecutionEnvironment env) {
        env.fromElements(1,2,3)
                .map(new RichMapFunction<Integer, Integer>() {
                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 子任务索引和并行度有关,一个并行度索引就是0,两个并行度索引就是0和1
                        System.out.println("生命周期开始,当前子任务索引：" + getRuntimeContext().getIndexOfThisSubtask());
                    }
                    @Override
                    public Integer map(Integer value) {
                        return value * value;
                    }
                    @Override
                    public void close() throws Exception {
                        super.close();
                        System.out.println("生命周期结束");
                    }
                })
                .print();
    }

    // 演示定时器
    private static void demo02(StreamExecutionEnvironment env) {
        env.socketTextStream("localhost", 9999)
                .keyBy(r -> 1)
                .process(new KeyedProcessFunction<Integer, String, String>() {
                    @Override
                    public void processElement(String value, Context ctx, Collector<String> out) {
                        // 获取当前机器时间
                        long ts = ctx.timerService().currentProcessingTime();
                        out.collect("元素 " + value + " 到达时间 " + new Timestamp(ts));
                        // 注册一个5秒钟后的定时器
                        ctx.timerService().registerProcessingTimeTimer(ts + 5000L);
                        out.collect("注册了一个即将在 " + new Timestamp(ts + 5000L) + " 触发的定时器");
                    }

                    // 定时器本质上也是状态,checkpoint是为了故障恢复,所以定期保存检查点时也会将定时器保存到状态后端
                    // 每个key都可以注册自己的独有定时器,每个key在每个时间戳只能注册一个定时器,放在队列里面时间到了就触发
                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        out.collect("定时器 " + new Timestamp(timestamp) + " 触发了");
                    }
                })
                .print();
    }

    // 演示ValueState
    private static void demo03(StreamExecutionEnvironment env) {
        // 需求：每隔5秒钟发送一次平均值
        // 分析：每隔5秒就是要设置5秒后的定时器,平均值需要累加器,学完窗口函数后可以直接开窗
        env.addSource(new Flink01.NumberSource())
                .keyBy(r -> true)
                .process(new KeyedProcessFunction<Boolean, Integer, Double>() {
                    // 声明一个ValueState作为累加器,Tuple2的参数1是累加器的sum值,参数2是元素个数
                    private ValueState<Tuple2<Integer, Integer>> acc;
                    // 声明一个ValueState作为定时器
                    private ValueState<Long> timer;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 实例化状态变量
                        acc = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("acc", Types.TUPLE(Types.INT, Types.INT)));
                        timer = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("timer", Types.LONG));
                    }

                    @Override
                    public void processElement(Integer value, Context ctx, Collector<Double> out) throws Exception {
                        // 当第一条数据进来时,状态变量的值都是null
                        System.out.println("当前进来的数据是：" + value);

                        // 判断累加器状态
                        if (acc.value() == null) {
                            // 第一条数据作为初始值
                            acc.update(Tuple2.of(value, 1));
                        } else {
                            // 后续数据进行滚动聚合
                            acc.update(Tuple2.of(acc.value().f0 + value, acc.value().f1 + 1));
                        }
                        System.out.println("当前累加器是：" + acc.value());

                        // 判断定时器状态
                        if (timer.value() == null) {
                            // 没有定时器就创建
                            long ts = ctx.timerService().currentProcessingTime() + 5 * 1000L;
                            ctx.timerService().registerProcessingTimeTimer(ts);
                            // 更新定时器状态
                            timer.update(ts);
                        }
                        System.out.println("当前定时器是：" + new Timestamp(timer.value()));

                        // 这里输入类型是Integer而输出类型是Tuple2,类型可以随便定义比reduce灵活,并且可以利用定时器设置输出频率
//                        out.collect((double)acc.value().f0 / acc.value().f1);
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<Double> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        if (acc.value() != null) {
                            // 定时器触发时输出结果
                            out.collect((double)acc.value().f0 / acc.value().f1);
                            // 定时器用完就清空状态,不然后续数据进来时检查定时器状态不为null就不会更新,那么就只会触发第一次
                            timer.clear();
                        }
                    }
                })
                .print();
    }

    // 演示ListState
    private static void demo04(StreamExecutionEnvironment env) {
        // 需求：使用ListState求平均值
        env.addSource(new Flink01.NumberSource())
                .keyBy(r -> 1)
                .process(new KeyedProcessFunction<Integer, Integer, Double>() {
                    // 声明一个ListState作为累加器,列表状态会保存流中所有数据,占用更多内存,不如值状态效率高
                    private ListState<Integer> listState;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 实例化状态变量
                        listState = getRuntimeContext().getListState(
                                new ListStateDescriptor<>("list", Types.INT));
                    }

                    @Override
                    public void processElement(Integer value, Context ctx, Collector<Double> out) throws Exception {
                        System.out.println("当前进来的数据是：" + value);
                        // 每来一条数据就添加到累加器
                        listState.add(value);
                        System.out.println("当前累加器是：" + listState.get());
                        // 遍历累加器求平均值
                        int sum = 0;
                        int cnt = 0;
                        for (Integer i : listState.get()) {
                            sum += i;
                            cnt += 1;
                        }
                        // 收集结果往下游发送
                        out.collect((double)sum / cnt);
                    }
                })
                .print();
    }

    // 演示MapState
    private static void demo05(StreamExecutionEnvironment env) {
        // 需求：使用MapState求网站pv平均值 = 总访问次数/用户数
        env.addSource(new Flink01.UserActionSource())
                .keyBy(r -> 1)
                .process(new KeyedProcessFunction<Integer, Flink01.Event, Double>() {
                    // 声明一个MapState作为累加器,key是用户,value是其访问次数
                    // hash表是最经典的数据结构,复杂度O(1),读写速度非常快,hbase/redis/es都用到,不知道怎么实现需求时就考虑一下HashMap
                    private MapState<String, Integer> mapState;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 实例化状态变量
                        mapState = getRuntimeContext().getMapState(
                                new MapStateDescriptor<>("user-cnt", Types.STRING, Types.INT));
                    }

                    @Override
                    public void processElement(Flink01.Event value, Context ctx, Collector<Double> out) throws Exception {
                        System.out.println("当前进来的数据是：" + value);
                        // 判断累加器状态
                        if (!mapState.contains(value.user)) {
                            // 用户不存在
                            mapState.put(value.user, 1);
                        } else {
                            // 用户已存在
                            mapState.put(value.user, mapState.get(value.user) + 1);
                        }
                        System.out.println("当前累加器是：" + mapState.entries());
                        // 遍历累加器求平均值
                        int userNum = 0;
                        int pvSum = 0;
                        for (String user : mapState.keys()) {
                            userNum += 1;
                            pvSum += mapState.get(user);
                        }
                        // 收集结果往下游发送
                        out.collect((double)pvSum / userNum);
                    }
                })
                .print();
    }

}