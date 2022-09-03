package com.okccc.flink.tuning;

import com.alibaba.fastjson.JSON;
import com.okccc.flink.source.MockSourceFunction;
import com.okccc.flink.tuning.function.LocalAggFunction;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Author: okccc
 * Date: 2022/8/3 4:05 下午
 * Desc: flink数据倾斜优化之localAgg
 */
public class SkewDemo02 extends BaseEnvironment {
    public static void main(String[] args) throws Exception {
        // 流处理环境
        StreamExecutionEnvironment env = getEnv();
        // 获取数据源
        DataStreamSource<String> dataStream = env.addSource(new MockSourceFunction());
        // 数据处理
        SingleOutputStreamOperator<Tuple2<String, Integer>> jsonStream = dataStream
                .map(JSON::parseObject)
                .filter(data -> StringUtils.isEmpty(data.getString("start")))
                // 将数据映射成(mid, 1)
                .map(data -> Tuple2.of(data.getJSONObject("common").getString("mid"), 1))
                .returns(Types.TUPLE(Types.STRING, Types.INT));

        // 按照mid分组,统计每个mid出现的次数
        ParameterTool parameterTool = ParameterTool.fromArgs(args);
        boolean isLocalKeyBy = parameterTool.getBoolean("local-keyBy", false);
        // 判断是否开启本地聚合,对比开启前后效果Overview - SubTasks - Bytes Received & Bytes Sent
        if (!isLocalKeyBy) {
            jsonStream
                    .keyBy(r -> r.f0)
                    .reduce(((value1, value2) -> Tuple2.of(value1.f0, value1.f1 + value2.f1)))
                    .print().setParallelism(1);
        } else {
            jsonStream
                    // 先进行本地聚合,减少往下游传输的数据量
                    .flatMap(new LocalAggFunction(10000))
                    // 再进行全局聚合
                    .keyBy(r -> r.f0)
                    .reduce((value1, value2) -> Tuple2.of(value1.f0, value1.f1 + value2.f1))
                    .print().setParallelism(1);
        }

        // 启动任务
        env.execute();
    }
}
