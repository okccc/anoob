package com.okccc.realtime.util;

import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * @Author: okccc
 * @Date: 2021/10/4 19:41
 * @Desc: flink读写kafka的工具类
 * https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/connectors/datastream/kafka/
 * https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/connectors/datastream/file_sink/
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
public class MyFlinkUtil {

    private static final String KAFKA_SERVER = "localhost:9092";
    private static final String DEFAULT_TOPIC = "default";

    /**
     * 从kafka读数据的消费者
     */
    public static FlinkKafkaConsumer<String> getKafkaSource(String topic, String groupId) {
        // 消费者属性配置
        Properties prop = new Properties();
        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        prop.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        prop.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        // 创建flink消费者对象
        FlinkKafkaConsumer<String> kafkaConsumer = new FlinkKafkaConsumer<>(topic, new SimpleStringSchema(), prop);
        // 偏移量会作为算子状态存储在状态后端,并且在checkpoint的同时会提交到kafka,方便通过kafka-consumer-groups.sh监控
        kafkaConsumer.setCommitOffsetsOnCheckpoints(true);
        // 偏移量读取顺序：先读状态后端,没有就读kafka消费者组,还没有就读latest,也可以手动设置偏移量的起始位置
        kafkaConsumer.setStartFromLatest();
        return kafkaConsumer;
    }

    public static FlinkKafkaConsumer<String> getKafkaSource(List<String> topics, String groupId) {
        // 消费者属性配置
        Properties prop = new Properties();
        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        prop.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // 创建flink消费者对象
        return new FlinkKafkaConsumer<>(topics, new SimpleStringSchema(), prop);
    }

    /**
     * 往kafka写数据的生产者,将数据写入指定topic
     */
    public static FlinkKafkaProducer<String> getKafkaSink(String topic) {
        // 生产者属性配置
        Properties prop = new Properties();
        prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        prop.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        prop.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, UUID.randomUUID().toString());
        prop.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 15 * 60 * 1000);
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
        prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_SERVER);
        prop.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        prop.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, UUID.randomUUID().toString());
        prop.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 15 * 60 * 1000);
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

    /**
     * 往hdfs写数据的生产者
     */
    public static FileSink<String> getHdfsSink(String output) {
        return FileSink
                // 行编码格式 Row-encoded Formats
                .forRowFormat(new Path(output), new SimpleStringEncoder<String>("UTF-8"))
                // 桶分配
                .withBucketAssigner(new HiveBucketAssigner<>("yyyyMMdd", ZoneId.of("Asia/Shanghai")))
                // 滚动策略,如果hadoop < 2.7就只能使用OnCheckpointRollingPolicy
                .withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                // part file何时由inprogress变成finished
                                .withRolloverInterval(10 * 60 * 1000L)
                                .withInactivityInterval(10 * 60 * 1000L)
                                .withMaxPartSize(1024 * 1024 * 128)
                                .build()
                ).build();
        // 批量编码格式 Bulk-encoded Formats: Parquet Format、Avro Format、ORC Format
    }

    public static class HiveBucketAssigner<IN> extends DateTimeBucketAssigner<IN> {
        public HiveBucketAssigner(String formatString, ZoneId zoneId) {
            super(formatString, zoneId);
        }

        @Override
        public String getBucketId(IN element, Context context) {
            // flink分桶将文件放入不同文件夹,桶号对应hive分区,桶号默认返回时间字符串,而hive分区通常是dt=开头,所以要重写该方法
            return "dt=" + super.getBucketId(element, context);
        }
    }
}
