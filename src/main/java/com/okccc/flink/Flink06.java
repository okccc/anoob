package com.okccc.flink;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Author: okccc
 * Date: 2021/9/20 下午12:46
 * Desc: CEP检测连续3次登录失败
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
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 插入水位线之前要保证流的并行度是1,不然就乱套了
        env.setParallelism(1);

        // 获取数据源
        KeyedStream<Event, String> keyedStream = env
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
                .keyBy(r -> r.user);

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

        // 将模板应用到数据流
        PatternStream<Event> patternStream = CEP.pattern(keyedStream, pattern);

        // 提取匹配事件
        patternStream
                .select(new PatternSelectFunction<Event, String>() {
                    @Override
                    public String select(Map<String, List<Event>> pattern) throws Exception {
                        Iterator<Event> iterator = pattern.get("fail").iterator();
                        Event first = iterator.next();
                        Event second = iterator.next();
                        Event third = iterator.next();
                        return "用户：" + first.user + " 在时间：" + first.timestamp + "、" + second.timestamp + "、"
                                + third.timestamp + " 连续三次登录失败！";
                    }
                })
                .print();

        // 启动任务
        env.execute();
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
    }
}
