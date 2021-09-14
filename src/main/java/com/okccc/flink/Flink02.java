package com.okccc.flink;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.*;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.Random;

/**
 * Author: okccc
 * Date: 2021/9/7 下午4:49
 * Desc: 富函数RichFunction、处理函数KeyedProcessFunction
 */
public class Flink02 {
    public static void main(String[] args) throws Exception {
        /*
         * 富函数
         * DataStream API提供的转换算子都有其Rich版本,都继承自RichFunction接口,有额外三个方法
         * open()：生命周期初始化,比如创建数据库连接,在map/filter这些算子之前被调用
         * getRuntimeContext()：处理数据时可以获取函数执行的上下文信息,比如任务并行度、子任务名称和索引、访问分区状态等
         * close()：生命周期结束,比如关闭数据库连接、清空状态
         *
         * 处理函数
         * DataStream API提供的转换算子功能有限,更底层的处理函数可以访问时间戳、水位线、注册定时事件
         * flink提供了8个ProcessFunction,都继承自RichFunction接口,最常用的是操作KeyedStream的KeyedProcessFunction<K, I, O>
         * KeyedProcessFunction<K, I, O>
         * K：分组字段类型
         * I：输入元素类型
         * O：输出元素类型
         * processElement(I value, Context ctx, Collector<O> out)：
         * 处理流中的每个元素并输出0/1/N个元素,结合状态变量和定时器使用功能极其强大,是flatMap和reduce的终极加强版,也是flink精髓所在
         * 每来一条数据都会驱动该方法运行,I是输入元素,Context可以访问元素的时间戳和key、注册定时器、写侧输出流,Collector收集结果往下游发送
         * onTimer(long timestamp, OnTimerContext ctx, Collector<O> out)：
         * 定时器timestamp会驱动该回调函数运行,OnTimerContext类似Context,Collector收集结果往下游发送
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 演示RichFunction
//        demo01(env);
        // 演示KeyedProcessFunction
        demo02(env);

        // 启动任务
        env.execute();
    }

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
                    public Integer map(Integer value) throws Exception {
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

    private static void demo02(StreamExecutionEnvironment env) {
        env.socketTextStream("localhost", 9999)
                .keyBy(r -> 1)
                .process(new KeyedProcessFunction<Integer, String, String>() {
                    @Override
                    public void processElement(String value, Context ctx, Collector<String> out) throws Exception {
                        // 获取当前机器时间
                        long ts = ctx.timerService().currentProcessingTime();
                        out.collect("元素 " + value + " 到达时间：" + new Timestamp(ts));
                        // 注册一个5秒钟后的定时器
                        ctx.timerService().registerProcessingTimeTimer(ts + 5000L);
                        out.collect("注册了一个即将在 " + new Timestamp(ts + 5000L) + " 触发的定时器");
                    }

                    // 每个key都可以注册自己的独有定时器,每个key在某个时间戳只能注册一个定时器,放在队列里面时间到了就触发
                    // 定时器本质上也是一个状态变量,checkpoint是为了故障恢复,所以定期保存检查点时也会将定时器保存到状态后端
                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        out.collect("定时器触发了,触发时间是：" + new Timestamp(timestamp));
                    }
                })
                .print();
    }

}
