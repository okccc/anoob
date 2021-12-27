package com.okccc.realtime.utils;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
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

    /**
     * 往kafka写数据的生产者,将数据写入指定topic
     */
    public static FlinkKafkaProducer<String> getKafkaSink(String topic) {
        // 生产者属性配置
        Properties prop = new Properties();
        prop.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        prop.setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 900 * 1000 + "");
        // 这个只能保证AT_LEAST_ONCE(96行源码)
//        return new FlinkKafkaProducer<>(KAFKA_SERVER, topic, new SimpleStringSchema());
        // 创建flink生产者对象
        return new FlinkKafkaProducer<>(
                DEFAULT_TOPIC,
                new KafkaSerializationSchema<String>() {
                    @Override
                    public ProducerRecord<byte[], byte[]> serialize(String element, @Nullable Long timestamp) {
                        return new ProducerRecord<>(topic, element.getBytes(StandardCharsets.UTF_8));
                    }
                },
                prop,
                // FlinkKafkaProducer实现了两阶段提交,Semantic.EXACTLY_ONCE会开启事务保证精准一次性(975行源码)
                FlinkKafkaProducer.Semantic.EXACTLY_ONCE);
    }
}
