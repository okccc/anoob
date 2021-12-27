package com.okccc.realtime.utils;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/10/4 下午7:41
 * Desc: flink读写kafka的工具类
 */
public class MyKafkaUtil {
    private static final String KAFKA_SERVER = "localhost:9092";
    private static final String DEFAULT_TOPIC = "default";

    /**
     * 从kafka读数据的消费者
     */
    public static FlinkKafkaConsumer<String> getKafkaSource(String topic, String groupId) {
        // 消费者属性配置
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // 创建flink消费者对象
        return new FlinkKafkaConsumer<>(topic, new SimpleStringSchema(), props);
    }
}
