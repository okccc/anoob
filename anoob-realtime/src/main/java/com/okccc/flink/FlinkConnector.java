package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.api.functions.source.datagen.DataGeneratorSource;
import org.apache.flink.streaming.api.functions.source.datagen.RandomGenerator;

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

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        getDataGenConnector(env);
        getFileSystemConnector(env);

        // 启动任务
        env.execute();
    }
}
