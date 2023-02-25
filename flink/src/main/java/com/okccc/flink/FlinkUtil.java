package com.okccc.flink;

import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
     * 配置检查点和状态后端
     */
    public static StreamExecutionEnvironment getExecutionEnvironment() {
        // 创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 禁用算子链,方便定位导致反压的具体算子
        env.disableOperatorChaining();
        // 设置状态后端
//        env.setStateBackend(new HashMapStateBackend());
        env.setStateBackend(new EmbeddedRocksDBStateBackend());
        // 检查点时间间隔：通常1~5分钟,查看Checkpoints - Summary - End to End Duration,综合考虑性能和时效性
        env.enableCheckpointing(TimeUnit.MINUTES.toMillis(2), CheckpointingMode.EXACTLY_ONCE);
        CheckpointConfig config = env.getCheckpointConfig();
        // 检查点存储路径
        config.setCheckpointStorage("hdfs://cdh1/flink/ck");
        // 检查点超时时间
        config.setCheckpointTimeout(TimeUnit.MINUTES.toMillis(5));
        // 检查点可容忍的连续失败次数
        config.setTolerableCheckpointFailureNumber(3);
        // 检查点最小等待间隔,通常是时间间隔一半
        config.setMinPauseBetweenCheckpoints(TimeUnit.MINUTES.toMillis(1));
        // 检查点保留策略：job取消时默认会自动删除检查点,可以保留防止任务故障重启失败,还能从检查点恢复任务,后面手动删除即可
        config.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // 重启策略：重试间隔调大一点,不然flink监控页面一下子就刷新过去变成job failed,看不到具体异常信息
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, TimeUnit.MINUTES.toMillis(1)));
        // 本地调试时要指定能访问hadoop的用户
        System.setProperty("HADOOP_USER_NAME", "deploy");
        return env;
    }

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
                                .withRolloverInterval(Duration.ofSeconds(300))
                                .withInactivityInterval(Duration.ofSeconds(300))
                                .withMaxPartSize(MemorySize.ofMebiBytes(128 * 1024 * 1024))
                                .build()
                )
                // Flink1.15版本开始FileSink支持已经提交pending文件的合并,避免生成大量小文件
//                .enableCompact(
//                        FileCompactStrategy.Builder.newBuilder()
//                                .setNumCompactThreads(1)
//                                // 每隔10个检查点就触发一次合并
//                                .enableCompactionOnCheckpoint(10)
//                                .build(),
//                        new RecordWiseFileCompactor<>(
//                                new DecoderBasedReader.Factory<>(SimpleStringDecoder::new)
//                        )
//                )
                .build();
        // 批量编码格式 Bulk-encoded Formats: Parquet Format、Avro Format、ORC Format
    }

    /**
     * 自定义hive桶分配器,继承默认的基于时间的分配器DateTimeBucketAssigner
     */
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

    /**
     * 从文件读数据的消费者(有界流,批处理)
     * flink是流批统一的,离线数据集也会当成流来处理,每来一条数据都会驱动程序运行并输出一个结果,spark批处理只会输出最终结果
     */
    public static FileSource<String> getFileSource(String path) {
        return FileSource.forRecordStreamFormat(
                new TextLineInputFormat("UTF-8"),
                new Path(path)
        ).build();
    }
}