package com.okccc.flink;

import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.datagen.DataGeneratorSource;
import org.apache.flink.streaming.api.functions.source.datagen.RandomGenerator;

import java.util.UUID;

/**
 * @Author: okccc
 * @Date: 2023/5/31 10:54:46
 * @Desc: Flink DataStream Connectors
 */
@SuppressWarnings("unused")
public class FlinkConnector {

    /**
     * DataGen Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/datastream/datagen/
     * The DataGen connector provides a Source implementation that allows for generating input data for Flink pipelines.
     */
    private static void getDataGenConnector(StreamExecutionEnvironment env) {
        // DataGen生成数据
        DataGeneratorSource<String> dataGeneratorSource = new DataGeneratorSource<>(
                new RandomGenerator<String>() {
                    @Override
                    public String next() {
                        return UUID.randomUUID().toString();
                    }
                },
                100,
                10000L
        );
        // 获取数据
        env.addSource(dataGeneratorSource).returns(Types.STRING).print();
    }

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        getDataGenConnector(env);

        // 启动任务
        env.execute();
    }
}
