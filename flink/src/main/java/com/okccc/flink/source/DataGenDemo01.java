package com.okccc.flink.source;

import com.okccc.flink.source.bean.OrderInfo;
import com.okccc.flink.source.bean.UserInfo;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.RestOptions;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.datagen.DataGeneratorSource;
import org.apache.flink.streaming.api.functions.source.datagen.RandomGenerator;
import org.apache.flink.streaming.api.functions.source.datagen.SequenceGenerator;

/**
 * @Author: okccc
 * @Date: 2022/8/30 6:12 下午
 * @Desc: flink使用DataGeneratorSource造数据,生成随机数
 */
public class DataGenDemo01 {
    public static void main(String[] args) throws Exception {
        // 创建流处理环境
        Configuration conf = new Configuration();
        conf.set(RestOptions.ENABLE_FLAMEGRAPH, true);
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(conf);
        env.setParallelism(1);

        // 生成数据源
        SingleOutputStreamOperator<OrderInfo> orderInfoStream = env
                .addSource(
                        new DataGeneratorSource<>(
                                new RandomGenerator<OrderInfo>() {
                                    @Override
                                    public OrderInfo next() {
                                        return OrderInfo.of(
                                                random.nextInt(1, 100000),
                                                random.nextLong(1, 1000000),
                                                random.nextUniform(1, 10000),
                                                System.currentTimeMillis()
                                        );
                                    }
                                }
                        )
                )
                .returns(Types.POJO(OrderInfo.class));

        SingleOutputStreamOperator<UserInfo> userInfoStream = env
                .addSource(
                        new DataGeneratorSource<>(
                                new SequenceGenerator<UserInfo>(1, 1000000) {
                                    final RandomDataGenerator random = new RandomDataGenerator();

                                    @Override
                                    public UserInfo next() {
                                        return UserInfo.of(
                                                valuesToEmit.peek().intValue(),  // peek取数据不从双端队列移除
                                                valuesToEmit.poll(),             // poll取数据并从双端队列移除
                                                random.nextInt(1, 100),
                                                random.nextInt(0, 1)
                                        );
                                    }
                                }
                        )
                )
                .returns(Types.POJO(UserInfo.class));

        orderInfoStream.print("order");
        userInfoStream.print("user");

        // 启动任务
        env.execute();
    }
}
