package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.datagen.source.DataGeneratorSource;
import org.apache.flink.connector.datagen.source.GeneratorFunction;
import org.apache.flink.connector.elasticsearch.sink.Elasticsearch7SinkBuilder;
import org.apache.flink.connector.elasticsearch.sink.ElasticsearchEmitter;
import org.apache.flink.connector.elasticsearch.sink.ElasticsearchSink;
import org.apache.flink.connector.elasticsearch.sink.RequestIndexer;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommand;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommandDescription;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisMapper;
import org.apache.http.HttpHost;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Properties;

/**
 * @Author: okccc
 * @Date: 2023/5/31 10:54:46
 * @Desc: Flink DataStream Connectors
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/datagen/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/jdbc/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/elasticsearch/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/filesystem
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/kafka
 */
public class FlinkConnector {

    /**
     * DataGen Connector
     */
    private static void getDataGenConnector(StreamExecutionEnvironment env) {
        // DataGen生成数据
        DataGeneratorSource<String> generatorSource = new DataGeneratorSource<>(
                new GeneratorFunction<Long, String>() {
                    @Override
                    public String map(Long value) {
                        return "Number: " + value;
                    }
                },
                1000,
                RateLimiterStrategy.perSecond(1),
                Types.STRING
        );

        env.fromSource(generatorSource, WatermarkStrategy.noWatermarks(), "Generator Source").print();
    }

    /**
     * JDBC Connector
     */
    private static void getJdbcConnector(StreamExecutionEnvironment env) {
        // 获取SinkFunction
        SinkFunction<Tuple2<String, String>> jdbcSink = JdbcSink.sink(
                // sql语句中表和字段都是写死的,所以只能单表写入,多表写入需要自定义SinkFunction
                // create table war3(id int primary key auto_increment,race varchar(11) unique,player varchar(11));
                "alter table war3 auto_increment=1;insert into war3(id,race,player) values(null,?,?) on duplicate key update race=values(race),player=values(player)",
                // 填充占位符
                new JdbcStatementBuilder<Tuple2<String, String>>() {
                    @Override
                    public void accept(PreparedStatement ps, Tuple2<String, String> tuple2) throws SQLException {
                        ps.setObject(1, tuple2.f0);
                        ps.setObject(2, tuple2.f1);
                    }
                },
                // 执行选项,JdbcSink内部使用了预编译器,可以批量提交优化写入速度,但是只能操作一张表,如果是一流写多表就得自定义JdbcSink
                JdbcExecutionOptions.builder()
                        .withBatchSize(100)          // 设置批处理大小(条),减少和数据库交互次数
                        .withBatchIntervalMs(10000)  // 设置批处理时间间隔(ms),不够100条每隔10秒也会执行一次
                        .withMaxRetries(3)
                        .build(),
                // 数据库连接信息
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withDriverName("com.mysql.cj.jdbc.Driver")
                        .withUrl("jdbc:mysql://localhost:3306/test?allowMultiQueries=true")
                        .withUsername("root")
                        .withPassword("root@123")
                        .build()
        );

        // 模拟数据写入
        env.fromData(Tuple2.of("orc", "grubby"), Tuple2.of("hum", "sky"), Tuple2.of("ne", "moon")).addSink(jdbcSink);
    }

    /**
     * Elasticsearch Connector
     *
     * 报错：[TOO_MANY_REQUESTS/12/disk usage exceeded flood-stage watermark, index has read-only-allow-delete block]
     * 原因：flood stage disk watermark [95%] exceeded, all indices on this node will be marked read-only
     * 解决：es数据节点磁盘使用率超过90%,此时为避免集群出现问题es会将数据索引上锁导致无法写入数据,需要清理磁盘空间
     */
    private static void getElasticsearchConnector(StreamExecutionEnvironment env) {
        // 获取ElasticsearchSink
        ElasticsearchSink<String> elasticsearchSink = new Elasticsearch7SinkBuilder<String>()
                // es地址
                .setHosts(new HttpHost("localhost", 9200, "http"))
                // 发送数据
                .setEmitter(
                        new ElasticsearchEmitter<String>() {
                            @Override
                            public void emit(String element, SinkWriter.Context context, RequestIndexer requestIndexer) {
                                // 创建IndexRequest对象
                                IndexRequest indexRequest = Requests.indexRequest();
                                // 指定_index和_id,不写id默认生成长度20的随机字符串
                                indexRequest.index("index01");
                                // 添加_source,必须是Map类型
                                HashMap<String, String> map = new HashMap<>();
                                map.put("name", element);
                                indexRequest.source(map);
                                // 添加IndexRequest对象
                                requestIndexer.add(indexRequest);
                            }
                        }
                )
                // 将bulk批量操作的缓冲数设置为1,也就是来一条处理一条
                // Instructs the sink to emit after every element, otherwise they would be buffered
                .setBulkFlushMaxActions(1)
                .build();

        // 模拟数据写入
        env.fromData("grubby", "moon", "sky").sinkTo(elasticsearchSink);
    }

    /**
     * Redis Connector(很少用,redis只能存少量数据但速度极快,适合做缓存以及大数据场景下根据key查询的聚合结果、临时数据)
     */
    private static void getRedisConnector(StreamExecutionEnvironment env) {
        // 获取RedisSink
        RedisSink<Tuple2<String, String>> redisSink = new RedisSink<>(
                new FlinkJedisPoolConfig.Builder().setHost("localhost").setPort(6379).build(),
                new RedisMapper<Tuple2<String, String>>() {
                    @Override
                    public RedisCommandDescription getCommandDescription() {
                        return new RedisCommandDescription(RedisCommand.HSET, "war3");
                    }

                    @Override
                    public String getKeyFromData(Tuple2<String, String> data) {
                        return data.f0;
                    }

                    @Override
                    public String getValueFromData(Tuple2<String, String> data) {
                        return data.f1;
                    }
                }
        );

        // 模拟数据写入
        env.fromData(Tuple2.of("orc", "grubby"), Tuple2.of("hum", "sky"), Tuple2.of("ne", "moon")).addSink(redisSink);
    }

    /**
     * FileSystem Connector
     */
    private static void getFileSystemConnector(StreamExecutionEnvironment env) {
        // 读文件,可以是文件也可以是目录
        FileSource<String> fileSource = FileSource
                .forRecordStreamFormat(new TextLineInputFormat(), new Path("hdfs:///ods.db/ods_user_info"))
                .build();
        DataStreamSource<String> dataStream = env.fromSource(fileSource, WatermarkStrategy.noWatermarks(), "File Source");
        dataStream.print();

        // 写文件
        FileSink<String> fileSink = FileSink
                // 行编码格式 Row-encoded Formats
                .forRowFormat(new Path("/tmp"), new SimpleStringEncoder<String>())
                // 桶分配
                .withBucketAssigner(new DateTimeBucketAssigner<>("yyyyMMdd", ZoneId.of("Asia/Shanghai")))
                // 滚动策略,如果hadoop < 2.7就只能使用OnCheckpointRollingPolicy
                .withRollingPolicy(
                        DefaultRollingPolicy.builder()
                                .withRolloverInterval(Duration.ofSeconds(10))
                                .withInactivityInterval(Duration.ofSeconds(10))
                                .withMaxPartSize(MemorySize.ofMebiBytes(1024))
                                .build()
                )
                .build();
        dataStream.sinkTo(fileSink);
    }

    /**
     * Kafka Connector
     */
    private static void getKafkaConnector(StreamExecutionEnvironment env) {
        // 读kafka
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setTopics("ods_base_db")
                .setGroupId("g01")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
                .build();
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source");
        dataStream.print();

        // 生产者属性配置
        Properties prop = new Properties();

        // The transaction timeout is larger than the maximum value allowed by the broker (transaction.max.timeout.ms)
        // https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/connectors/datastream/kafka/#kafka-producer-%E5%92%8C%E5%AE%B9%E9%94%99
        // Kafka broker事务最大超时时间transaction.max.timeout.ms=15分钟,而FlinkKafkaProducer的transaction.timeout.ms=1小时,因此在使用Semantic.EXACTLY_ONCE模式之前应该调小transaction.timeout.ms的值
        prop.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 15 * 60 * 1000);

        // There is a newer producer with the same transactionalId which fences the current one
        // kafka生产者exactly_once：幂等性只能保证单分区单会话内数据不重复,完全不重复还得在幂等性的基础上开启事务
//        prop.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, UUID.randomUUID().toString());

        // javax.management.InstanceAlreadyExistsException: kafka.producer:type=app-info,id=producer-kafka-sink-0-1
        // 创建kafka客户端指定的clientId是一个固定值,并发量小时用完就销毁没问题,并发量大时比如ods层日志分流会连续创建多个kafka生产者,
        // 可能会出现上次创建的clientId还没来得及销毁就又创建了一个新的连接导致clientId重复,这种场景必须保证每次建立连接的clientId唯一
//        prop.put(ProducerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString());

        // 写kafka
        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                .setBootstrapServers("localhost:9092")
                .setRecordSerializer(
                        KafkaRecordSerializationSchema.builder()
                                .setTopic("aaa")
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build()
                )
                // Sink端的EXACTLY_ONCE依赖于事务,会影响性能且容易出故障,大多数场景不丢数据就行,只要下游kafka消费者能保证幂等性即可
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .setKafkaProducerConfig(prop)
                .build();
        dataStream.sinkTo(kafkaSink);
    }

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

//        getDataGenConnector(env);
//        getJdbcConnector(env);
//        getElasticsearchConnector(env);
//        getRedisConnector(env);
//        getFileSystemConnector(env);
        getKafkaConnector(env);

        // 启动任务
        env.execute();
    }
}
