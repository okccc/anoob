package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.execution.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.producer.ProducerConfig;

/**
 * @Author: okccc
 * @Date: 2025-07-30 21:15:42
 * @Desc: Flink Sink端两阶段提交之生产者
 */
public class FlinkTwoPhaseCommitProducer {
    public static void main(String[] args) throws Exception {
        // 创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // 开启检查点,设置为精准一次
        env.enableCheckpointing(10000, CheckpointingMode.EXACTLY_ONCE);

        // 使用命令行模拟生产者 kafka-console-producer.sh --bootstrap-server localhost:9092 --topic aaa
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("aaa")
                .setGroupId("g1")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setStartingOffsets(OffsetsInitializer.latest())
                .build();
        DataStreamSource<String> dataStream = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "kafka source");

        // 使用2PC往topic写数据
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic("bbb")
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                // EXACTLY_ONCE 开启两阶段提交
                .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                // EXACTLY_ONCE 必须设置事务前缀
                .setTransactionalIdPrefix("tx-")
                // EXACTLY_ONCE 必须设置事务超时时间,大于checkpoint间隔,小于kafka事务最大超时时间15分钟
                .setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 10 * 60 * 1000 + "")
                .build();
        dataStream.sinkTo(kafkaSink);

        // 启动任务
        env.execute();
    }
}
