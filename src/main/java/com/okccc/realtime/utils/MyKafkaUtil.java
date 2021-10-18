package com.okccc.realtime.utils;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/10/4 下午7:41
 * Desc: 操作kafka的工具类
 */
public class MyKafkaUtil {
    private static final String KAFKA_SERVER = "localhost:9092";

    // 获取kafka的消费者
    public static FlinkKafkaConsumer<String> getKafkaSource(String topic, String groupId) {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return new FlinkKafkaConsumer<>(topic, new SimpleStringSchema(), props);
    }

    // 获取kafka的生产者
    public static FlinkKafkaProducer<String> getKafkaSink(String topic) {
        return new FlinkKafkaProducer<>(KAFKA_SERVER, topic, new SimpleStringSchema());
    }
}
