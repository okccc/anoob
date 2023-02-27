package com.okccc.source;

import com.okccc.bean.OrderInfo;
import lombok.SneakyThrows;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.datagen.DataGeneratorSource;
import org.apache.flink.streaming.api.functions.source.datagen.RandomGenerator;
import org.apache.flink.streaming.api.functions.source.datagen.SequenceGenerator;

/**
 * @Author: okccc
 * @Date: 2022/8/30 6:12 下午
 * @Desc: DataGeneratorSource模拟生成随机数
 */
public class DataGenSource {

    public static void main(String[] args) throws Exception {
        // 创建流处理环境
        Configuration conf = new Configuration();
        conf.set(RestOptions.ENABLE_FLAMEGRAPH, true);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);
        env.setParallelism(1);

        // RandomGenerator<T>
        env
            .addSource(
                new DataGeneratorSource<>(
                    new RandomGenerator<OrderInfo>() {
                        @SneakyThrows
                        @Override
                        public OrderInfo next() {
                            Thread.sleep(1000);
                            return OrderInfo.of(
                                    random.nextLong(1, 10000),
                                    random.nextUniform(1, 10000),
                                    System.currentTimeMillis()
                            );
                        }
                    }
                )
            )
            .returns(Types.POJO(OrderInfo.class))
            .print("RandomGenerator");

        // SequenceGenerator<T>
        env
            .addSource(
                new DataGeneratorSource<>(
                    new SequenceGenerator<OrderInfo>(1, 10000) {
                        @SneakyThrows
                        @Override
                        public OrderInfo next() {
                            Thread.sleep(1000);
                            return OrderInfo.of(
                                    valuesToEmit.poll(),                // poll取数据并从双端队列移除
                                    valuesToEmit.peek().doubleValue(),  // peek取数据不从双端队列移除
                                    System.currentTimeMillis()
                            );
                        }
                    }
                )
            )
            .returns(Types.POJO(OrderInfo.class))
            .print("SequenceGenerator");


        // 启动任务
        env.execute();
    }
}
