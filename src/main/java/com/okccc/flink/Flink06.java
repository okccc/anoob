package com.okccc.flink;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.cep.*;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Author: okccc
 * Date: 2021/9/20 下午12:46
 * Desc: 5秒内连续3次登录失败、15分钟内未支付的超时订单
 */
public class Flink06 {
    public static void main(String[] args) throws Exception {
        /*
         * Flink-CEP(Complex Event Processing)专门处理连续多次这种复杂事件
         * 处理事件的规则叫Pattern,定义输入流中的复杂事件,用来提取符合规则的事件序列
         * .begin()          模式序列必须以begin开始,且不能以notFollowedBy结束,not类型模式不能被optional修饰
         * .where()          筛选条件 .where().or().until()
         * .next()           严格近邻,事件必须严格按顺序出现  模式"a next b"  事件序列[a,c,b1,b2]不匹配
         * .followedBy()     宽松近邻,允许中间出现不匹配事件  模式"a followedBy b"  事件序列[a,c,b1,b2]匹配为{a,b1}
         * .followedByAny()  非确定性宽松近邻,进一步放宽条件  模式"a followedByAny b"  事件序列[a,c,b1,b2]匹配为{a,b1},{a,b2}
         * .notNext()        不想让某个事件严格近邻前一个事件
         * .notFollowedBy()  不想让某个事件在两个事件之间发生
         * .times()          定义事件次数
         * .within()         定义时间窗口
         *
         * 总结：Pattern是flink的语法糖,实际上底层就是KeyedProcessFunction+状态变量,一般直接使用cep就行,除非特别复杂的需求才用到状态机
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 插入水位线之前要保证流的并行度是1,不然就乱套了
        env.setParallelism(1);

//        demo01(env);
//        demo02(env);
        demo03(env);

        // 启动任务
        env.execute();
    }

    private static void demo01(StreamExecutionEnvironment env) {
        // 使用CEP检测5秒内连续三次登录失败
        SingleOutputStreamOperator<Event> inputStream = env
                .fromElements(
                        Event.of("sky", "fail", 1000L),
                        Event.of("sky", "fail", 2000L),
                        Event.of("sky", "fail", 5000L),
                        Event.of("fly", "success", 1000L),
                        Event.of("sky", "fail", 4000L)
                )
                // 提前时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner(new SerializableTimestampAssigner<Event>() {
                                    @Override
                                    public long extractTimestamp(Event element, long recordTimestamp) {
                                        return element.timestamp;
                                    }
                                })
                );

        // 定义匹配模板,类似正则表达式(主要就是写这玩意)
        Pattern<Event, Event> pattern = Pattern
                .<Event>begin("fail")
                .where(new SimpleCondition<Event>() {
                    @Override
                    public boolean filter(Event value) throws Exception {
                        return value.eventType.equals("fail");
                    }
                })
                .times(3).consecutive()
                .within(Time.seconds(5));

        // 将pattern应用到数据流
        PatternStream<Event> patternStream = CEP.pattern(inputStream.keyBy(r -> r.user), pattern);

        // select提取匹配事件
        patternStream
                .select(new PatternSelectFunction<Event, String>() {
                    @Override
                    public String select(Map<String, List<Event>> pattern) throws Exception {
                        Iterator<Event> iterator = pattern.get("fail").iterator();
                        Event first = iterator.next();
                        Event second = iterator.next();
                        Event third = iterator.next();
                        return "用户 " + first.user + " 在时间 " + first.timestamp + "、" + second.timestamp + "、"
                                + third.timestamp + " 连续三次登录失败！";
                    }
                })
                .print();
    }

    private static void demo02(StreamExecutionEnvironment env) {
        // 使用状态机检测连续三次登录失败
        env
                .fromElements(
                        Event.of("sky", "fail", 1000L),
                        Event.of("sky", "fail", 2000L),
                        Event.of("sky", "fail", 5000L),
                        Event.of("fly", "success", 1000L),
                        Event.of("sky", "fail", 4000L)
                )
                // 提前时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner(new SerializableTimestampAssigner<Event>() {
                                    @Override
                                    public long extractTimestamp(Event element, long recordTimestamp) {
                                        return element.timestamp;
                                    }
                                })
                )
                .keyBy(r -> r.user)
                .process(new KeyedProcessFunction<String, Event, String>() {
                    // 创建一个状态机,key是当前状态和接收到的事件类型,value是即将跳转到的状态
                    private final HashMap<Tuple2<String, String>, String> stateMachine = new HashMap<>();
                    // 当前状态
                    private ValueState<String> currentState;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 初始化状态变量
                        currentState = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("current-state", Types.STRING));
                        // 添加元素生成状态转移矩阵,一切业务逻辑都可以抽象成状态机,写大量if/else很难调试,状态机可以实现0bug
                        stateMachine.put(Tuple2.of("INITIAL", "success"), "SUCCESS");
                        stateMachine.put(Tuple2.of("INITIAL", "fail"), "S1");
                        stateMachine.put(Tuple2.of("S1", "success"), "SUCCESS");
                        stateMachine.put(Tuple2.of("S1", "fail"), "S2");
                        stateMachine.put(Tuple2.of("S2", "success"), "SUCCESS");
                        stateMachine.put(Tuple2.of("S2", "fail"), "FAIL");
                    }

                    @Override
                    public void processElement(Event value, Context ctx, Collector<String> out) throws Exception {
                        System.out.println("当前进来元素是 " + value);
                        // 判断当前状态
                        if (currentState.value() == null) {
                            // 刚开始状态为空
                            currentState.update("INITIAL");
                        }
                        // 计算即将要跳转的状态
                        String nextState = stateMachine.get(Tuple2.of(currentState.value(), value.eventType));
                        if (nextState.equals("SUCCESS")) {
                            // 登录成功,清空状态变量
                            currentState.clear();
                        } else if (nextState.equals("FAIL")) {
                            // 登录失败,重置为S2,输出结果
                            currentState.update("S2");
                            out.collect("用户 " + value.user + " 在时间 " + value.timestamp + " 已经连续三次登录失败！");
                        } else {
                            // 还处于中间状态,正常跳转
                            currentState.update(nextState);
                            // 注册定时器
                            ctx.timerService().registerEventTimeTimer(ctx.timerService().currentWatermark() + 5000L);
                        }
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);

                    }
                })
                .print();
    }

    private static void demo03(StreamExecutionEnvironment env) {
        // 使用CEP检测订单超时
        SingleOutputStreamOperator<Event> inputstream = env
                .fromElements(
                        Event.of("fly", "create", 1000L),
                        Event.of("fly", "pay", 300 * 1000L),
                        Event.of("sky", "create", 1000L),
                        Event.of("sky", "pay", 901 * 1000L)
                )
                // 提前时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<Event>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner(new SerializableTimestampAssigner<Event>() {
                                    @Override
                                    public long extractTimestamp(Event element, long recordTimestamp) {
                                        return element.timestamp;
                                    }
                                })
                );

        // 定义匹配模板
        Pattern<Event, Event> pattern = Pattern
                .<Event>begin("create")
                .where(new SimpleCondition<Event>() {
                    @Override
                    public boolean filter(Event value) throws Exception {
                        return value.eventType.equals("create");
                    }
                })
                // 连续事件必须用next,其它的就用followedBy
                .followedBy("pay")
                .where(new SimpleCondition<Event>() {
                    @Override
                    public boolean filter(Event value) throws Exception {
                        return value.eventType.equals("pay");
                    }
                })
                .within(Time.minutes(15));

        // 将pattern应用到数据流
        PatternStream<Event> patternStream = CEP.pattern(inputstream.keyBy(r -> r.user), pattern);

        // flatSelect提取匹配事件(需要侧输出流时使用)
        SingleOutputStreamOperator<String> result = patternStream
                .flatSelect(
                        // 设置侧输出流
                        new OutputTag<String>("timeout") {
                        },
                        // 超时事件：只有create没有pay
                        new PatternFlatTimeoutFunction<Event, String>() {
                            @Override
                            public void timeout(Map<String, List<Event>> pattern, long timeoutTimestamp, Collector<String> out) throws Exception {
                                Event create = pattern.get("create").get(0);
                                out.collect("订单 " + create.user + " 已超时！当前时间为" + timeoutTimestamp);
                            }
                        },
                        // 正常事件：有pay那肯定就有create
                        new PatternFlatSelectFunction<Event, String>() {
                            @Override
                            public void flatSelect(Map<String, List<Event>> pattern, Collector<String> out) throws Exception {
                                Event pay = pattern.get("pay").get(0);
                                out.collect("订单 " + pay.user + " 已支付！");
                            }
                        }
                );

        result.print();
        result.getSideOutput(new OutputTag<String>("timeout"){}).print();

    }

    // 自定义POJO类
    public static class Event {
        public String user;
        public String eventType;
        public Long timestamp;

        public Event() {
        }

        public Event(String user, String eventType, Long timestamp) {
            this.user = user;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }

        // 模拟flink源码常用的of语法糖
        public static Event of(String user, String eventType, Long timestamp) {
            return new Event(user, eventType, timestamp);
        }

        @Override
        public String toString() {
            return "Event{" +
                    "user='" + user + '\'' +
                    ", eventType='" + eventType + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
