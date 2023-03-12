package com.okccc.flink;

import com.okccc.bean.LoginData;
import com.okccc.bean.OrderData;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternFlatSelectFunction;
import org.apache.flink.cep.PatternFlatTimeoutFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: okccc
 * @Date: 2021/9/20 下午12:46
 * @Desc: 5秒内连续3次登录失败(严格近邻)、15分钟内未支付的超时订单(宽松近邻)
 *
 * Flink-CEP(Complex Event Processing)专门处理连续多次这种复杂事件
 * 处理事件的规则叫Pattern,定义输入流中的复杂事件,用来提取符合规则的事件序列
 * .begin()          模式序列必须以begin开始,且不能以notFollowedBy结束,not类型模式不能被optional修饰
 * .where()          筛选条件 .where().or().until()
 * .next()           严格近邻,事件必须严格按顺序出现  模式"a next b"           事件序列[a,c,b1,b2]不匹配
 * .followedBy()     宽松近邻,允许中间出现不匹配事件  模式"a followedBy b"     事件序列[a,c,b1,b2]匹配为{a,b1}
 * .followedByAny()  非确定性宽松近邻,进一步放宽条件  模式"a followedByAny b"  事件序列[a,c,b1,b2]匹配为{a,b1},{a,b2}
 * .notNext()        不想让某个事件严格近邻前一个事件
 * .notFollowedBy()  不想让某个事件在两个事件之间发生
 * .times()          定义事件次数
 * .within()         定义时间窗口
 *
 * 总结：Pattern是flink语法糖,底层就是KeyedProcessFunction + 状态变量 + 定时器,一般直接cep就行,除非特别复杂的需求才会用大招
 */
public class FlinkCep {

    /**
     * 使用CEP检测5秒内连续三次登录失败的用户
     */
    private static void checkLoginFail(StreamExecutionEnvironment env) {
        KeyedStream<LoginData, String> dataStream = env
                .readTextFile("flink/input/LoginData.csv")
                // 将数据封装成POJO类
                .map((MapFunction<String, LoginData>) value -> {
                    // 5402,83.149.11.115,success,1558430815
                    String[] arr = value.split(",");
                    return LoginData.of(arr[0], arr[2], Long.parseLong(arr[3]) * 1000);
                })
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<LoginData>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                )
                .keyBy(r -> r.userId);

        // 定义匹配模板,类似正则表达式(主要就是写这玩意)
        Pattern<LoginData, LoginData> pattern = Pattern
                .<LoginData>begin("fail")
                .where(new SimpleCondition<LoginData>() {
                    @Override
                    public boolean filter(LoginData value) throws Exception {
                        return value.eventType.equals("fail");
                    }
                })
                .times(3)
                .consecutive()
                .within(Time.seconds(5));

        // 将pattern应用到数据流
        PatternStream<LoginData> patternStream = CEP.pattern(dataStream, pattern);

        // 提取匹配事件(process/select)
        patternStream
                .process(new PatternProcessFunction<LoginData, String>() {
                    @Override
                    public void processMatch(Map<String, List<LoginData>> match, Context ctx, Collector<String> out) throws Exception {
                        LoginData firstFail = match.get("fail").get(0);
                        LoginData secondFail = match.get("fail").get(1);
                        LoginData thirdFail = match.get("fail").get(2);
                        out.collect(firstFail.userId + " 连续三次登录失败 " + firstFail.timestamp + ", " + secondFail.timestamp + ", " + thirdFail.timestamp);
                    }
                })
                .print();
    }

    /**
     * 使用CEP检测15min内未支付的超时订单
     */
    private static void checkOrderPay(StreamExecutionEnvironment env) {
        // 业务系统需要不停判断订单支付时间是否超时,类似618这种促销场景数据量暴增对系统压力很大,可以考虑使用低延迟高吞吐的flink处理
        KeyedStream<OrderData, String> dataStream = env
                .readTextFile("flink/input/OrderData.csv")
                // 将数据封装成POJO类
                .map((MapFunction<String, OrderData>) value -> {
                    // 34729,create,,1558430842 | 34729,pay,sd76f87d6,1558430844
                    String[] arr = value.split(",");
                    return OrderData.of(arr[0], arr[1], arr[2], Long.parseLong(arr[3]) * 1000);
                })
                // 提前时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OrderData>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                )
                .keyBy(r -> r.orderId);

        // 定义匹配模板
        Pattern<OrderData, OrderData> pattern = Pattern
                .<OrderData>begin("create")
                .where(new SimpleCondition<OrderData>() {
                    @Override
                    public boolean filter(OrderData value) throws Exception {
                        return value.eventType.equals("create");
                    }
                })
                .followedBy("pay")
                .where(new SimpleCondition<OrderData>() {
                    @Override
                    public boolean filter(OrderData value) throws Exception {
                        return value.eventType.equals("pay");
                    }
                })
                .within(Time.minutes(15));

        // 将pattern应用到数据流
        PatternStream<OrderData> patternStream = CEP.pattern(dataStream, pattern);

        // 声明侧输出流标签
        OutputTag<String> outputTag = new OutputTag<String>("timeout") {};
        // 提取匹配事件(process/flatSelect)
        SingleOutputStreamOperator<String> result = patternStream
                .flatSelect(
                        // 将超时订单放到侧输出流
                        outputTag,
                        // 超时订单：创建后未支付或超时支付
                        new PatternFlatTimeoutFunction<OrderData, String>() {
                            @Override
                            public void timeout(Map<String, List<OrderData>> pattern, long timeoutTimestamp, Collector<String> out) throws Exception {
                                OrderData create = pattern.get("create").get(0);
                                out.collect("订单 " + create.orderId + " 已超时,创建时间为" + create.timestamp + ",当前时间为" + timeoutTimestamp);
                            }
                        },
                        // 正常订单：创建后及时支付
                        new PatternFlatSelectFunction<OrderData, String>() {
                            @Override
                            public void flatSelect(Map<String, List<OrderData>> pattern, Collector<String> out) throws Exception {
                                OrderData pay = pattern.get("pay").get(0);
                                out.collect("订单 " + pay.orderId + " 已支付,支付时间为" + pay.timestamp);
                            }
                        }
                );

        // 打印测试
        result.print();
        // 获取侧输出流
        result.getSideOutput(outputTag).print("output");
    }

    /**
     * 使用状态机检测连续三次登录失败的用户
     */
    private static void testStateMachine(StreamExecutionEnvironment env) {
        env
                .readTextFile("flink/input/LoginData.csv")
                // 将数据封装成POJO类
                .map((MapFunction<String, LoginData>) value -> {
                    // 5402,83.149.11.115,success,1558430815
                    String[] arr = value.split(",");
                    return LoginData.of(arr[0], arr[2], Long.parseLong(arr[3]) * 1000);
                })
                // 按照用户分组
                .keyBy(r -> r.userId)
                // 处理函数
                .process(new KeyedProcessFunction<String, LoginData, String>() {
                    // 创建一个状态机,key是当前状态和接收到的事件类型,value是即将跳转到的状态
                    private final HashMap<Tuple2<String, String>, String> stateMachine = new HashMap<>();
                    // 声明一个ValueState表示当前状态
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
                    public void processElement(LoginData value, Context ctx, Collector<String> out) throws Exception {
                        // 数据进来了
                        System.out.println("当前进来数据：" + value);
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
                            // 登录失败,输出结果(这里有缺陷,如果有延迟数据结果就有点问题,比如时间戳为43,44,47,42)
                            out.collect(value.userId + " 在 " + value.timestamp + " 已经连续三次登录失败！");
                        } else {
                            // 还处于中间状态,正常跳转
                            currentState.update(nextState);
                        }
                    }
                })
                .print();
    }

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 插入水位线之前要保证流的并行度是1,不然就乱套了
        env.setParallelism(1);

//        checkLoginFail(env);
        checkOrderPay(env);
//        testStateMachine(env);

        // 启动任务
        env.execute();
    }
}
