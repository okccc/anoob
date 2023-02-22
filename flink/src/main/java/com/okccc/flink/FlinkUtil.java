package com.okccc.flink;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;
import java.util.UUID;

/**
 * @Author: okccc
 * @Date: 2022/12/16 18:23
 * @Desc: flink操作kafka、hdfs、jdbc、redis、es的工具类
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.15/zh/docs/connectors/datastream/kafka/
 * https://nightlies.apache.org/flink/flink-docs-release-1.15/zh/docs/connectors/datastream/filesystem/
 * https://nightlies.apache.org/flink/flink-docs-release-1.15/docs/connectors/datastream/elasticsearch/
 * FlinkKafkaConsumer已被弃用并将在Flink1.17中移除,请改用KafkaSource
 * FlinkKafkaProducer已被弃用并将在Flink1.15中移除,请改用KafkaSink
 *
 * 常见错误
 * Caused by: org.apache.kafka.common.errors.ProducerFencedException: Producer attempted an operation with an old epoch.
 * Either there is a newer producer with the same transactionalId, or the producer's transaction has been expired by the broker.
 * kafka生产者exactly_once：幂等性只能保证单分区单会话内数据不重复,完全不重复还得在幂等性的基础上开启事务
 *
 * Caused by: org.apache.kafka.common.KafkaException: Unexpected error in InitProducerIdResponse;
 * The transaction timeout is larger than the maximum value allowed by the broker (as configured by transaction.max.timeout.ms).
 * https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/connectors/datastream/kafka/#kafka-producer-%E5%92%8C%E5%AE%B9%E9%94%99
 * Kafka broker事务最大超时时间transaction.max.timeout.ms=15分钟,而FlinkKafkaProducer的transaction.timeout.ms=1小时,
 * 因此在使用Semantic.EXACTLY_ONCE模式之前应该调小transaction.timeout.ms的值
 */
public class FlinkUtil {

    // kafka地址
    private static final String KAFKA_SERVER = "localhost:9092";

    /**
     * 从kafka读数据的消费者
     */
    public static KafkaSource<String> getKafkaSource(String groupId, String... topics) {
        // 创建flink消费者对象
        return KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_SERVER)
                .setTopics(topics)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
    }

    /**
     * 往kafka写数据的生产者,将数据写入指定topic
     */
    public static KafkaSink<String> getKafkaSink(String topic) {
        // 生产者属性配置
        Properties prop = new Properties();
        prop.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 15 * 60 * 1000);
        // 创建flink生产者对象
        return KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_SERVER)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(topic)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                .setDeliverGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .setTransactionalIdPrefix(UUID.randomUUID().toString())
                .setKafkaProducerConfig(prop)
                .build();
    }

    /**
     * 往kafka写数据的生产者,将数据动态写入不同topic,传入KafkaRecordSerializationSchema接口,由调用者自己实现
     */
    public static <T> KafkaSink<T> getKafkaSinkBySchema(KafkaRecordSerializationSchema<T> kafkaRecordSerializationSchema) {
        return KafkaSink.<T>builder()
                .setBootstrapServers(KAFKA_SERVER)
                .setRecordSerializer(kafkaRecordSerializationSchema)
                .setDeliverGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
                .setTransactionalIdPrefix(UUID.randomUUID().toString())
                .build();
    }
}