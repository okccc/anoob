package com.okccc.app.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.util.Collector;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;

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

        // 检查点存储路径,目录名称就是Flink Streaming Job ID
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

        // 获取命令行参数
        ParameterTool parameterTool = ParameterTool.fromArgs(args);
        // 本地调试时要设置能访问hdfs的用户
        String hdfsUser = parameterTool.get("hdfs-user", "deploy");
        System.setProperty("HADOOP_USER_NAME", hdfsUser);

        return env;
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
                // 查看SimpleStringSchema源码77行和String源码514行发现bytes[]是@NotNull,所以要自定义反序列化器
//                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setValueOnlyDeserializer(new DeserializationSchema<String>() {
                    @Override
                    public String deserialize(byte[] message) {
                        // flink sql的left join会生成null值,如果直接用SimpleStringSchema会报空指针异常
                        if (message != null && message.length > 0) {
                            return new String(message, StandardCharsets.UTF_8);
                        }
                        // 这里返回null不会报错,下游消费者过滤即可
                        return null;
                    }

                    @Override
                    public boolean isEndOfStream(String nextElement) {
                        return false;  // kafka是无界流
                    }

                    @Override
                    public TypeInformation<String> getProducedType() {
                        return BasicTypeInfo.STRING_TYPE_INFO;
                    }
                })
                .setStartingOffsets(OffsetsInitializer.latest())
                .build();
    }

    /**
     * KafkaSink,将数据写入指定topic
     * FlinkKafkaProducer已被弃用并将在Flink1.15中移除,请改用KafkaSink
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/zh/docs/connectors/datastream/kafka/#kafka-sink
     */
    public static KafkaSink<String> getKafkaSink(String topic, String transactionId) {
        // 创建flink生产者对象
        return KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_SERVER)
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic(topic)
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                // Sink端的EXACTLY_ONCE依赖事务,影响性能且容易故障,大多数场景AT_LEAST_ONCE就行,只要下游kafka消费者能保证幂等性即可
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
//                // There is a newer producer with the same transactionalId which fences the current one
//                // https://kafka.apache.org/documentation/#producerconfigs_transactional.id
//                .setTransactionalIdPrefix(transactionId)
//                // The transaction timeout is larger than the maximum value allowed by the broker (transaction.max.timeout.ms)
//                // https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/connectors/datastream/kafka/#kafka-producer-%E5%92%8C%E5%AE%B9%E9%94%99
//                .setProperty(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 15 * 60 * 1000 + "")
                .build();
    }

    /**
     * KafkaSink,将数据动态写入不同topic,传入KafkaRecordSerializationSchema接口,由调用者自己实现
     */
    public static <T> KafkaSink<T> getKafkaSinkBySchema(KafkaRecordSerializationSchema<T> kafkaRecordSerializationSchema) {
        return KafkaSink.<T>builder()
                .setBootstrapServers(KAFKA_SERVER)
                .setRecordSerializer(kafkaRecordSerializationSchema)
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();
    }

    /**
     * KafkaSource DDL
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/kafka
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/datastream/formats/json/
     * The Kafka connector allows for reading data from and writing data into Kafka topics.
     */
    public static String getKafkaSourceDdl(String topic, String groupId) {
        // Could not find any factory for identifier 'json' that implements 'org.apache.flink.table.factories.DeserializationFormatFactory' in the classpath.
        return " WITH ( " +
                "  'connector' = 'kafka',\n" +
                "  'topic' = '" + topic + "',\n" +
                "  'properties.bootstrap.servers' = '" + KAFKA_SERVER + "',\n" +
                "  'properties.group.id' = '" + groupId + "',\n" +
                "  'scan.startup.mode' = 'latest-offset',\n" +
                "  'format' = 'json',\n" +
                "  'json.ignore-parse-errors' = 'true'\n" +  // 过滤非json数据
                ")";
    }

    /**
     * KafkaSink DDL
     */
    public static String getKafkaSinkDdl(String topic) {
        return " WITH ( " +
                "  'connector' = 'kafka',\n" +
                "  'topic' = '" + topic + "',\n" +
                "  'properties.bootstrap.servers' = '" + KAFKA_SERVER + "',\n" +
                "  'format' = 'json'\n" +
                ")";
    }

    /**
     * UpsertKafkaSink DDL
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/upsert-kafka/
     * The Upsert Kafka connector allows for reading data from and writing data into Kafka topics in the upsert fashion.
     */
    public static String getUpsertKafkaSinkDdl(String topic) {
        return " WITH ( " +
                "  'connector' = 'upsert-kafka',\n" +
                "  'topic' = '" + topic + "',\n" +
                "  'properties.bootstrap.servers' = '" + KAFKA_SERVER + "',\n" +
                "  'key.format' = 'json',\n" +
                "  'value.format' = 'json'\n" +
                ")";
    }

    /**
     * 读取kafka业务主题ods_base_db,加购、下单、支付、退款、评论、收藏等场景都会用到
     * {"database":"mock","table":"order_info","type":"update","data":{"user_id":"1493",...},"old":{...},"ts":1686551814}
     */
    public static String getOdsBaseDb(String groupId) {
        // 数据库关键字要加``
        return "CREATE TABLE IF NOT EXISTS ods_base_db (\n" +
                "    database     STRING,\n" +
                "    `table`      STRING,\n" +
                "    type         STRING,\n" +
                "    data         MAP<STRING, STRING>,\n" +
                "    `old`        MAP<STRING, STRING>,\n" +
                "    ts           BIGINT,\n" +
                // 调用PROCTIME()函数获取系统时间,作为与字典表lookup join的处理时间字段
                "    proc_time    AS PROCTIME()\n" +
                ") " + getKafkaSourceDdl("ods_base_db", groupId);
    }

    /**
     * MysqlSource DDL
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/jdbc/
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/table/jdbc/#lookup-cache
     */
    public static String getMysqlSourceDdl(String tableName) {
        // 通常是读取mysql维度表和kafka事实表做lookup join
        return " WITH ( " +
                "  'connector' = 'jdbc',\n" +
                "  'driver' = 'com.mysql.cj.jdbc.Driver',\n" +
                "  'url' = 'jdbc:mysql://localhost:3306/mock',\n" +
                "  'username' = 'root',\n" +
                "  'password' = 'root@123',\n" +
                "  'table-name' = '" + tableName + "',\n" +
                // lookup缓存配置
                "  'lookup.cache' = 'PARTIAL',\n" +
                "  'lookup.partial-cache.max-rows' = '100',\n" +
                "  'lookup.partial-cache.expire-after-write' = '10 min',\n" +
                "  'lookup.partial-cache.cache-missing-key' = 'false'\n" +
                ")";
    }

    /**
     * 读取mysql字典表base_dic,加购、下单、支付、退款、评论等场景都会用到
     */
    public static String getBaseDic() {
        return "CREATE TABLE IF NOT EXISTS base_dic (\n" +
                "    dic_code        STRING,\n" +
                "    dic_name        STRING,\n" +
                "    parent_code     STRING,\n" +
                "    create_time     STRING,\n" +
                "    operate_time    STRING,\n" +
                "PRIMARY KEY(dic_code) NOT ENFORCED\n" +
                ")" + FlinkUtil.getMysqlSourceDdl("base_dic");
    }

    /**
     * 将流中数据格式转换成JSON
     */
    public static SingleOutputStreamOperator<JSONObject> convertStrToJson(DataStreamSource<String> dataStream) {
        return dataStream.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String value, Collector<JSONObject> out) {
                try {
                    JSONObject jsonObject = JSON.parseObject(value);
                    out.collect(jsonObject);
                } catch (Exception e) {
                    // 上游数据有left join时会生成null值,需要过滤
                    System.out.println(">>>" + value);
                }
            }
        });
    }

    /**
     * 设置状态存活时间
     */
    public static ValueStateDescriptor<String> setStateTtl(String stateName, int ttl) {
        // 创建状态描述符
        ValueStateDescriptor<String> stateDescriptor = new ValueStateDescriptor<>(stateName, Types.STRING);

        // 独立访客的状态用来筛选当天是否访问过,第二天就没用了,所以要设置失效时间ttl,避免状态常驻内存
        StateTtlConfig stateTtlConfig = StateTtlConfig
                // 设置状态存活时间为1天
                .newBuilder(Time.days(ttl))
                // 状态更新策略：比如状态是今天10点创建11点更新12点读取,那么失效时间是明天Disabled(10点)/OnCreateAndWrite(11点)/OnReadAndWrite(12点)
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                // 状态可见性：内存中的状态过期后,如果没有被jvm垃圾回收,是否还会返回给调用者
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .build();
        stateDescriptor.enableTimeToLive(stateTtlConfig);

        return stateDescriptor;
    }

    /**
     * 针对left join生成的回撤流进行去重,保留主键(唯一键)的第一条数据
     */
    public static SingleOutputStreamOperator<JSONObject> getEarliestData(SingleOutputStreamOperator<JSONObject> dataStream, String key, int ttl) {
        return dataStream
                // 按照主键(唯一键)分组
                .keyBy(r -> r.getString(key))
                .filter(new RichFilterFunction<JSONObject>() {
                    // 声明状态变量,记录最新数据
                    private ValueState<JSONObject> earliestData;

                    @Override
                    public void open(Configuration parameters) {
                        // 创建状态描述符
                        ValueStateDescriptor<JSONObject> stateDescriptor = new ValueStateDescriptor<>("earliest", JSONObject.class);

                        // 设置状态存活时间
                        StateTtlConfig stateTtlConfig = new StateTtlConfig.Builder(Time.seconds(ttl))
                                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                                .build();
                        stateDescriptor.enableTimeToLive(stateTtlConfig);

                        // 初始化状态变量
                        earliestData = getRuntimeContext().getState(stateDescriptor);
                    }

                    @Override
                    public boolean filter(JSONObject value) throws Exception {
                        // 获取状态数据
                        JSONObject jsonObject = earliestData.value();

                        // 判断状态是否为空
                        if (jsonObject == null) {
                            earliestData.update(value);
                            return true;
                        } else {
                            return false;
                        }
                    }
                });
    }

    /**
     * 针对left join生成的回撤流进行去重,保留主键(唯一键)的最后一条数据
     */
    public static SingleOutputStreamOperator<JSONObject> getLatestData(SingleOutputStreamOperator<JSONObject> dataStream, String key, long timer, String createTime) {
        return dataStream
                // 按照主键(唯一键)分组
                .keyBy(r -> r.getString(key))
                // 涉及定时器操作用process
                .process(new KeyedProcessFunction<String, JSONObject, JSONObject>() {
                    // 声明状态变量,记录最新数据
                    private ValueState<JSONObject> latestData;

                    @Override
                    public void open(Configuration parameters) {
                        // 初始化状态变量,这里不设置ttl,后面定时器触发时会手动清空状态
                        latestData = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("latest", JSONObject.class));
                    }

                    @Override
                    public void processElement(JSONObject value, KeyedProcessFunction<String, JSONObject, JSONObject>.Context ctx, Collector<JSONObject> out) throws Exception {
                        // 获取状态中的数据
                        JSONObject jsonObject = latestData.value();

                        // 判断状态是否为空
                        if (jsonObject == null) {
                            latestData.update(value);
                            // 注册5秒后的定时器,和数据乱序程度保持一致
                            ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + timer);
                        } else {
                            // 不为空就要比较两条数据的生成时间
                            String stateTs = jsonObject.getString(createTime);
                            String currentTs = value.getString(createTime);
                            int diff = DateUtil.compare(stateTs, currentTs);
                            // 将后来的数据更新到状态
                            if (diff != 1) {
                                latestData.update(value);
                            }
                        }
                    }

                    @Override
                    public void onTimer(long timestamp, KeyedProcessFunction<String, JSONObject, JSONObject>.OnTimerContext ctx, Collector<JSONObject> out) throws Exception {
                        // 定时器触发,输出数据并清空状态
                        out.collect(latestData.value());
                        latestData.clear();
                    }
                });
    }

    /**
     * 往hdfs写数据的生产者
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/zh/docs/connectors/datastream/filesystem/
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
}
