package com.okccc.flink;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoFlatMapFunction;
import org.apache.flink.util.Collector;

/**
 * Author: okccc
 * Date: 2021/9/18 下午2:28
 * Desc: 多流合并
 */
public class Flink05 {
    public static void main(String[] args) throws Exception {
        /*
         * 合流
         * union可以合并多条流,流中的元素类型必须相同
         * connect只能连接两条流,流中的元素类型可以不同
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 演示合流操作
//        demo01(env);
        demo02(env);

        // 启动任务
        env.execute();

    }

    private static void demo01(StreamExecutionEnvironment env) {
        // 演示union合并多条流
        DataStreamSource<Integer> stream01 = env.fromElements(1, 2, 3);
        DataStreamSource<Integer> stream02 = env.fromElements(4, 5, 6);
        DataStreamSource<Integer> stream03 = env.fromElements(7, 8, 9);
        DataStream<Integer> unionStream = stream01.union(stream02, stream03);
        unionStream.print();
    }

    private static void demo02(StreamExecutionEnvironment env) {
        // 演示connect连接两条流
        DataStreamSource<Flink01.Event> actionStream = env.addSource(new Flink01.UserActionSource());
        DataStreamSource<String> queryStream = env.socketTextStream("localhost", 9999).setParallelism(1);
        actionStream
                // 将第一条流分流
                .keyBy(r -> r.user)
                // 将第二条流广播到所有并行度
                .connect(queryStream.broadcast())
                // 相当于join操作
                .flatMap(new CoFlatMapFunction<Flink01.Event, String, Flink01.Event>() {
                    // 在socket流中输入查询关键字"./home" "./cart",即对应action流中Event的url属性
                    private String query = "";
                    @Override
                    public void flatMap1(Flink01.Event value, Collector<Flink01.Event> out) throws Exception {
                        // 第一条流的数据进来时调用
                        if (value.url.equals(query)) {
                            // 满足条件就向下游发送
                            out.collect(value);
                        }
                    }

                    @Override
                    public void flatMap2(String value, Collector<Flink01.Event> out) throws Exception {
                        // 第二条流的数据进来时调用
                        query = value;
                    }
                })
                .print();
    }
}
