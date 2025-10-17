package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.IsolationLevel;

import java.util.Locale;

/**
 * @Author: okccc
 * @Date: 2025-07-30 21:37:02
 * @Desc: Flink Sink端两阶段提交之消费者
 */
public class FlinkTwoPhaseCommitConsumer {
    public static void main(String[] args) throws Exception {
        // 创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 消费被两阶段提交的topic
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("bbb")
                .setGroupId("g2")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setStartingOffsets(OffsetsInitializer.latest())
                // kafka消费者默认事务隔离级别是读未提交,因此Flink Sink端2PC预提交的数据也会被读到
                // 修改前：生产者生产的数据这里会立马读到
                // 修改后：生产者生产的数据这里10秒后才会读到,因为检查点时间间隔设置的是10秒,Sink端2PC要等检查点完成才会正式提交事务
                .setProperty(ConsumerConfig.ISOLATION_LEVEL_CONFIG, IsolationLevel.READ_COMMITTED.toString().toLowerCase(Locale.ROOT))
                .build();
        env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "kafka source")
                .print();

        // 启动任务
        env.execute();
    }
}
