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
 * Desc: 全窗口函数ProcessWindowFunction
 */
public class Flink03 {
    public static void main(String[] args) throws Exception {
        /*
         * flink也有窗口函数,但不代表这就是微批处理,flink流处理指的是数据来一条处理一条,开窗是业务需求,有些场景必须要攒一批数据
         * keyBy()是分组后聚合,keyBy() + window()是分组并各自开窗后再聚合,分组就是将数据分到不同的流
         *
         * 滚动窗口：窗口大小固定,没有重叠,一个元素只会属于一个窗口,是滑动窗口的特殊情况,窗口大小和滑动间隔相等
         * 滑动窗口：窗口大小固定,有滑动间隔所以会有重叠,一个元素可以属于多个窗口
         * 会话窗口：前两者统计的是网站PV/UV这种固定时间的,而用户访问行为是不固定的,超时时间内没收到新数据就生成新的窗口,只有flink支持会话窗口
         *
         * 全窗口函数
         * ProcessWindowFunction<IN, OUT, KEY, W extends Window>
         * IN： 输入元素类型
         * OUT：输出元素类型
         * KEY：分组字段类型
         * W：  窗口类型
         * process(KEY key, Context context, Iterable<IN> elements, Collector<OUT> out)
         * 窗口关闭时会驱动其运行,key是分组字段,context可以访问窗口元数据信息,elements保存窗口内所有元素,out收集结果往下游发送
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 演示ProcessWindowFunction
//        demo01(env);

        // 启动任务
        env.execute();
    }

    private static void demo01(StreamExecutionEnvironment env) {
        // 需求：使用全窗口函数,统计每个用户每5秒钟的pv
        // 分析：按照用户分组,窗口大小5秒,pv对应全窗口函数的迭代器,所以是keyBy(user) + window(5s) + process(Iterable)
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
                        out.collect("用户 " + key + " 在窗口 " + new Timestamp(start) + " ~ " + new Timestamp(end) + " 的pv是 " + cnt);
                    }
                })
                .print();
    }

}
