package com.okccc.flink;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
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

import java.sql.Timestamp;
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
public class FlinkCEP {
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
        demo02(env);
//        demo03(env);

        // 启动任务
        env.execute();
    }

    private static void demo01(StreamExecutionEnvironment env) {
        // 使用CEP检测5秒内连续三次登录失败
        SingleOutputStreamOperator<LoginEvent> inputStream = env
//                .fromElements(
//                        LoginEvent.of("sky", "fail", 1000L),
//                        LoginEvent.of("sky", "fail", 2000L),
//                        LoginEvent.of("sky", "fail", 5000L),
//                        LoginEvent.of("fly", "success", 1000L),
//                        LoginEvent.of("sky", "fail", 4000L)
//                )
                .readTextFile("input/LoginLog.csv")
                .map(new MapFunction<String, LoginEvent>() {
                    @Override
                    public LoginEvent map(String value) throws Exception {
                        String[] arr = value.split(",");
                        return LoginEvent.of(arr[0], arr[2], Long.parseLong(arr[3]) * 1000);
                    }
                })
                // 提前时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<LoginEvent>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                );

        // 定义匹配模板,类似正则表达式(主要就是写这玩意)
        Pattern<LoginEvent, LoginEvent> pattern = Pattern
                .<LoginEvent>begin("fail")
                .where(new SimpleCondition<LoginEvent>() {
                    @Override
                    public boolean filter(LoginEvent value) throws Exception {
                        return value.eventType.equals("fail");
                    }
                })
                .times(3).consecutive()
                .within(Time.seconds(5));

        // 将pattern应用到数据流
        PatternStream<LoginEvent> patternStream = CEP.pattern(inputStream.keyBy(r -> r.userId), pattern);

        // select提取匹配事件
        patternStream
                .select(new PatternSelectFunction<LoginEvent, String>() {
                    @Override
                    public String select(Map<String, List<LoginEvent>> pattern) throws Exception {
                        Iterator<LoginEvent> iterator = pattern.get("fail").iterator();
                        LoginEvent first = iterator.next();
                        LoginEvent second = iterator.next();
                        LoginEvent third = iterator.next();
                        return "用户 " + first.userId + " 在时间 " + first.timestamp + "、" + second.timestamp + "、"
                                + third.timestamp + " 连续三次登录失败！";
                    }
                })
                .print();
    }

    private static void demo02(StreamExecutionEnvironment env) {
        // 使用状态机检测连续三次登录失败
        env
                .fromElements(
                        LoginEvent.of("sky", "fail", 1000L),
                        LoginEvent.of("sky", "fail", 2000L),
                        LoginEvent.of("sky", "fail", 5000L),
                        LoginEvent.of("fly", "success", 1000L),
                        LoginEvent.of("sky", "fail", 4000L)
                )
                // 提前时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<LoginEvent>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner(new SerializableTimestampAssigner<LoginEvent>() {
                                    @Override
                                    public long extractTimestamp(LoginEvent element, long recordTimestamp) {
                                        return element.timestamp;
                                    }
                                })
                )
                .keyBy(r -> r.userId)
                .process(new KeyedProcessFunction<String, LoginEvent, String>() {
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
                        // 生成状态转移矩阵图,一切业务逻辑都可以抽象成状态机,写大量if/else很难调试,状态机可以实现0bug
                        stateMachine.put(Tuple2.of("INITIAL", "success"), "SUCCESS");
                        stateMachine.put(Tuple2.of("INITIAL", "fail"), "S1");
                        stateMachine.put(Tuple2.of("S1", "success"), "SUCCESS");
                        stateMachine.put(Tuple2.of("S1", "fail"), "S2");
                        stateMachine.put(Tuple2.of("S2", "success"), "SUCCESS");
                        stateMachine.put(Tuple2.of("S2", "fail"), "FAIL");
                    }

                    @Override
                    public void processElement(LoginEvent value, Context ctx, Collector<String> out) throws Exception {
                        System.out.println("当前进来元素是 " + value);
                        // 判断当前状态
                        if (currentState.value() == null) {
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
                            out.collect("用户 " + value.userId + " 在时间 " + value.timestamp + " 已经连续三次登录失败！");
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
        // 使用CEP检测超时订单
        SingleOutputStreamOperator<OrderEvent> inputStream = env
                .fromElements(
                        OrderEvent.of("order01", "create", 1000L),
                        OrderEvent.of("order01", "pay", 300 * 1000L),
                        OrderEvent.of("order02", "create", 1000L),
                        OrderEvent.of("order02", "pay", 901 * 1000L)
                )
                // 提前时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner(new SerializableTimestampAssigner<OrderEvent>() {
                                    @Override
                                    public long extractTimestamp(OrderEvent element, long recordTimestamp) {
                                        return element.timestamp;
                                    }
                                })
                );

        // 定义匹配模板
        Pattern<OrderEvent, OrderEvent> pattern = Pattern
                .<OrderEvent>begin("create")
                .where(new SimpleCondition<OrderEvent>() {
                    @Override
                    public boolean filter(OrderEvent value) throws Exception {
                        return value.eventType.equals("create");
                    }
                })
                // 连续事件必须用next,其它的就用followedBy
                .followedBy("pay")
                .where(new SimpleCondition<OrderEvent>() {
                    @Override
                    public boolean filter(OrderEvent value) throws Exception {
                        return value.eventType.equals("pay");
                    }
                })
                .within(Time.minutes(15));

        // 将pattern应用到数据流
        PatternStream<OrderEvent> patternStream = CEP.pattern(inputStream.keyBy(r -> r.orderId), pattern);

        // flatSelect也能提取匹配事件
        SingleOutputStreamOperator<String> result = patternStream
                .flatSelect(
                        // 将超时订单放到侧输出流
                        new OutputTag<String>("timeout") {
                        },
                        // 超时订单：创建后未支付或超时支付
                        new PatternFlatTimeoutFunction<OrderEvent, String>() {
                            @Override
                            public void timeout(Map<String, List<OrderEvent>> pattern, long timeoutTimestamp, Collector<String> out) throws Exception {
                                OrderEvent create = pattern.get("create").get(0);
                                out.collect("订单 " + create.orderId + " 已超时！当前时间为 " + timeoutTimestamp);
                            }
                        },
                        // 正常订单：创建后及时支付
                        new PatternFlatSelectFunction<OrderEvent, String>() {
                            @Override
                            public void flatSelect(Map<String, List<OrderEvent>> pattern, Collector<String> out) throws Exception {
                                OrderEvent pay = pattern.get("pay").get(0);
                                out.collect("订单 " + pay.orderId + " 已支付！");
                            }
                        }
                );

        result.print("print");
        result.getSideOutput(new OutputTag<String>("timeout"){}).print("output");
    }

    // POJO类
    public static class LoginEvent {
        public String userId;
        public String eventType;
        public Long timestamp;

        public LoginEvent() {
        }

        public LoginEvent(String userId, String eventType, Long timestamp) {
            this.userId = userId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }

        public static LoginEvent of(String user, String eventType, Long timestamp) {
            return new LoginEvent(user, eventType, timestamp);
        }

        @Override
        public String toString() {
            return "LoginEvent{" +
                    "userId='" + userId + '\'' +
                    ", eventType='" + eventType + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    public static class OrderEvent {
        public String orderId;
        public String eventType;
        public Long timestamp;

        public OrderEvent() {
        }

        public OrderEvent(String orderId, String eventType, Long timestamp) {
            this.orderId = orderId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }

        public static OrderEvent of(String orderId, String eventType, Long timestamp) {
            return new OrderEvent(orderId, eventType, timestamp);
        }

        @Override
        public String toString() {
            return "OrderEvent{" +
                    "orderId='" + orderId + '\'' +
                    ", eventType='" + eventType + '\'' +
                    ", timestamp=" + new Timestamp(timestamp) +
                    '}';
        }
    }

}
