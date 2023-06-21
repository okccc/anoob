package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.api.functions.source.datagen.DataGeneratorSource;
import org.apache.flink.streaming.api.functions.source.datagen.RandomGenerator;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.UUID;

/**
 * @Author: okccc
 * @Date: 2023/5/31 10:54:46
 * @Desc: Flink DataStream Connectors
 */
@SuppressWarnings("unused")
public class FlinkConnector {

    /**
     * DataGen Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/datastream/datagen/
     * The DataGen connector provides a Source implementation that allows for generating input data for Flink pipelines.
     */
    private static void getDataGenConnector(StreamExecutionEnvironment env) {
        // DataGen生成数据
        DataGeneratorSource<String> dataGeneratorSource = new DataGeneratorSource<>(
                new RandomGenerator<String>() {
                    @Override
                    public String next() {
                        return UUID.randomUUID().toString();
                    }
                },
                100,
                10000L
        );
        // 获取数据
        env.addSource(dataGeneratorSource).returns(Types.STRING).print();
    }

    /**
     * FileSystem Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/datastream/filesystem
     * This connector provides a unified Source and Sink for BATCH and STREAMING that reads or writes (partitioned) files.
     *
     * flink是流批统一的,离线数据集也会当成流来处理,每来一条数据都会驱动程序运行并输出一个结果,SparkStreaming批处理只会输出最终结果
     */
    private static void getFileSystemConnector(StreamExecutionEnvironment env) {
        // 读文件
        FileSource<String> fileSource = FileSource
                .forRecordStreamFormat(new TextLineInputFormat(), new Path("anoob-realtime/input/LoginData.csv"))
                .build();
        DataStreamSource<String> dataStream = env.fromSource(fileSource, WatermarkStrategy.noWatermarks(), "File Source");
        dataStream.print();

        // 写文件
        FileSink<String> fileSink = FileSink
                // 行编码格式 Row-encoded Formats
                .forRowFormat(new Path("anoob-realtime/output"), new SimpleStringEncoder<String>())
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
     * JDBC Connector
     * https://nightlies.apache.org/flink/flink-docs-release-1.17/docs/connectors/datastream/jdbc/
     * This connector provides a sink that writes data to a JDBC database.
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
        env.fromElements(Tuple2.of("orc", "grubby"), Tuple2.of("hum", "sky"), Tuple2.of("ne", "moon")).addSink(jdbcSink);
    }

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        getDataGenConnector(env);
        getFileSystemConnector(env);
        getJdbcConnector(env);

        // 启动任务
        env.execute();
    }
}
