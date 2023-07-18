package com.okccc.app.util;

import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Properties;

/**
 * @Author: okccc
 * @Date: 2022/12/16 18:23
 * @Desc: flink工具类
 */
public class FlinkUtil {

    // kafka地址
    private static final String KAFKA_SERVER = "localhost:9092";

    /**
     * 配置状态后端和检查点
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/dev/datastream/fault-tolerance/checkpointing/
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/ops/monitoring/checkpoint_monitoring/
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/ops/monitoring/back_pressure/
     */
    public static void setCheckpointAndStateBackend(StreamExecutionEnvironment env) {
        // 禁用算子链,方便定位导致反压的具体算子
        env.disableOperatorChaining();

        // 设置状态后端
//        env.setStateBackend(new HashMapStateBackend());
        env.setStateBackend(new EmbeddedRocksDBStateBackend());

        // 开启检查点：通常1~5分钟执行一次,查看Checkpoints - Summary - End to End Duration,综合考虑性能和时效性
        env.enableCheckpointing(60 * 1000, CheckpointingMode.EXACTLY_ONCE);
        CheckpointConfig config = env.getCheckpointConfig();

        // 检查点存储路径
        config.setCheckpointStorage("hdfs://${ip}:${port}/flink/cp");

        // 检查点超时时间,防止状态数据过大或反压导致检查点耗时过长 Checkpoint expired before completing.
        config.setCheckpointTimeout(3 * 60 * 1000);

        // 检查点可容忍的连续失败次数,不然一故障就报错 Exceeded checkpoint tolerable failure threshold.
        config.setTolerableCheckpointFailureNumber(3);

        // 检查点之间的最小时间间隔,保证执行检查点的并发是1,防止检查点耗时过长导致积压,密集触发检查点操作会占用大量资源
        // 场景1：检查点60s执行一次,最小时间间隔30s,某次检查点耗时40s,理论上下一次检查点20s后就会执行,但是实际上会等30s
        // 场景2：检查点60s执行一次,最小时间间隔30s,某次检查点耗时90s,理论上下一次检查点已经在执行中了,但是实际上会等30s
        config.setMinPauseBetweenCheckpoints(30 * 1000);

        // barrier对齐：快的barrier到达后,算子不会继续处理数据,而是放到缓冲区,等所有输入流的barrier到齐才会进行checkpoint
        // 缓冲区数据变多容易造成阻塞 -> 出现反压时阻塞数据会加剧反压 -> 反压进一步导致barrier流动变慢 -> checkpoint耗时变长
        // barrier不对齐：有barrier到达就触发检查点,不用等待所有输入流的barrier,可以避免阻塞但是会增加IO,因为检查点要保存更多数据
        // barrier对齐可以保证exactly_once,不对齐的话从checkpoint故障恢复时快的那部分数据会重复消费只能保证at_least_once
        config.enableUnalignedCheckpoints();

        // 检查点保留策略：job取消时默认会自动删除检查点,可以保留防止任务故障重启失败,还能从检查点恢复任务,后面手动删除即可
        config.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);

        // 重启策略：重试间隔调大一点,不然flink监控页面一下子就刷新过去变成job failed,看不到具体异常信息
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, 60 * 1000));

        // 本地调试时要指定能访问hadoop的用户
        System.setProperty("HADOOP_USER_NAME", "deploy");
    }

    /**
     * KafkaSource
     * FlinkKafkaConsumer已被弃用并将在Flink1.17中移除,请改用KafkaSource
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/zh/docs/connectors/datastream/kafka/#kafka-source
     */
    public static KafkaSource<String> getKafkaSource(String groupId, String... topics) {
        // 创建flink消费者对象
        return KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_SERVER)
                .setTopics(topics)
                .setGroupId(groupId)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();
    }

    /**
     * KafkaSink,将数据写入指定topic
     * FlinkKafkaProducer已被弃用并将在Flink1.15中移除,请改用KafkaSink
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/zh/docs/connectors/datastream/kafka/#kafka-sink
     * Sink端的EXACTLY_ONCE依赖于事务,会影响性能且容易出故障,大多数场景AT_LEAST_ONCE就行,只要下游kafka消费者能保证幂等性即可
     */
    public static KafkaSink<String> getKafkaSink(String topic) {
        // 生产者属性配置
        Properties prop = new Properties();

        // The transaction timeout is larger than the maximum value allowed by the broker (transaction.max.timeout.ms)
        // https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/connectors/datastream/kafka/#kafka-producer-%E5%92%8C%E5%AE%B9%E9%94%99
        // Kafka broker事务最大超时时间transaction.max.timeout.ms=15分钟,而FlinkKafkaProducer的transaction.timeout.ms=1小时,因此在使用Semantic.EXACTLY_ONCE模式之前应该调小transaction.timeout.ms的值
//        prop.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 15 * 60 * 1000);

        // There is a newer producer with the same transactionalId which fences the current one
        // kafka生产者exactly_once：幂等性只能保证单分区单会话内数据不重复,完全不重复还得在幂等性的基础上开启事务
//        prop.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, UUID.randomUUID().toString());

        // javax.management.InstanceAlreadyExistsException: kafka.producer:type=app-info,id=producer-kafka-sink-0-1
        // 创建kafka客户端指定的clientId是一个固定值,并发量小时用完就销毁没问题,并发量大时比如ods层日志分流会连续创建多个kafka生产者,
        // 可能会出现上次创建的clientId还没来得及销毁就又创建了一个新的连接导致clientId重复,这种场景必须保证每次建立连接的clientId唯一
//        prop.put(ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString());

        // 创建flink生产者对象
        return KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_SERVER)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(topic)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .setKafkaProducerConfig(prop)
                .build();
    }
}
