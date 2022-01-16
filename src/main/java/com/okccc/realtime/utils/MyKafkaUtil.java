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
import java.util.List;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/10/4 下午7:41
 * Desc: flink读写kafka的工具类
 * https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/connectors/datastream/kafka/
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

    public static FlinkKafkaConsumer<String> getKafkaSource(List<String> topics, String groupId) {
        // 消费者属性配置
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // 创建flink消费者对象
        return new FlinkKafkaConsumer<>(topics, new SimpleStringSchema(), props);
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

    /**
     * 往kafka写数据的生产者,将数据动态写入不同topic,传入KafkaSerializationSchema接口,由调用者自己实现
     */
    public static <T> FlinkKafkaProducer<T> getKafkaSinkBySchema(KafkaSerializationSchema<T> kafkaSerializationSchema) {
        // 生产者属性配置
        Properties prop = new Properties();
        prop.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        prop.setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 900 * 1000 + "");
        // 创建flink生产者对象
        return new FlinkKafkaProducer<>(DEFAULT_TOPIC, kafkaSerializationSchema, prop, FlinkKafkaProducer.Semantic.EXACTLY_ONCE);
    }

    /**
     * flink-sql创建kafka表的WITH语句模板
     */
    public static String getKafkaDDL(String topic, String groupId) {
        return "'connector' = 'kafka',\n" +
                "  'topic' = '" + topic + "',\n" +
                "  'properties.bootstrap.servers' = '" + KAFKA_SERVER + "',\n" +
                "  'properties.group.id' = '" + groupId + "',\n" +
                "  'scan.startup.mode' = 'latest-offset',\n" +
                "  'format' = 'json'";
    }
}
