package com.okccc.flink;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2022/1/09 11:05 上午
 * Desc: 实时同步kafka数据到hive
 */
public class KafkaToHdfs {

    public static void main(String[] args) throws Exception {
        /**
         * flink run提交jar包参数
         * -c,--class    main方法所在主类
         * Options for yarn-cluster mode:
         * -m,--jobmanager                 Address of the JobManager (master) to which to connect
         * -ynm,--yarnname                 Set a custom name for the application on YARN
         * -yjm,--yarnjobManagerMemory     Memory for JobManager Container with optional unit (default: MB)
         * -ytm,--yarntaskManagerMemory    Memory per TaskManager Container with optional unit (default: MB)
         * -ys,--yarnslots                 Number of slots per TaskManager
         * -yqu,--yarnqueue                Specify YARN queue.
         * bin/flink run -m yarn-cluster -ynm demo -yjm 2048 -ytm 4096 -ys 1 -yqu root.ai -c com.okccc.Demo ./demo.jar
         */

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 设置checkpoint时间间隔,不然hdfs文件一直处于in-progress状态
        env.enableCheckpointing(50000L, CheckpointingMode.EXACTLY_ONCE);
        System.setProperty("HADOOP_USER_NAME", "deploy");

        // 2.获取kafka数据
        ArrayList<String> topics = new ArrayList<>();
        topics.add("eduplatform01");
        topics.add("eduplatform02");

        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.18.2.4:9092,10.18.2.5:9092,10.18.2.6:9092");
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "aaa");

        FlinkKafkaConsumer<String> kafkaConsumer = new FlinkKafkaConsumer<>(topics, new SimpleStringSchema(), props);
        kafkaConsumer.setCommitOffsetsOnCheckpoints(true);
        kafkaConsumer.setStartFromEarliest();

        DataStreamSource<String> kafkaStream = env.addSource(kafkaConsumer);

        // 3.etl处理
        SingleOutputStreamOperator<String> dataStream = kafkaStream.flatMap(new FlatMapFunction<String, String>() {
            @Override
            public void flatMap(String value, Collector<String> out) throws Exception {
                /**
                 * {
                 *     "data":[
                 *         {
                 *             "score":"-1",
                 *             "update_time":"2022-01-04 14:35:39",
                 *             "create_time":"2022-01-04 14:35:39",
                 *             "time_removed":"0",
                 *             "id":"1478253777531457536",
                 *             "bid":"9388e877b40f48beb75d399e59ec7890",
                 *             "finish_time":"1641278139839",
                 *             "node_id":"efeae3ad0dc34d34bfb76dfd3edcf270"
                 *         }
                 *     ],
                 *     "type":"INSERT",
                 *     "es":1641278139000,
                 *     "database":"eduplatform5",
                 *     "table":"node_flow_record1",
                 *     "ts":1641278139917
                 * }
                 */
                // 转换结构
                JSONObject jsonObject = JSON.parseObject(value);
                // 过滤数据
                String table = jsonObject.getString("table");
                if (table.startsWith("node_flow_record")) {
                    // 解析数据
                    JSONObject data = jsonObject.getJSONArray("data").getJSONObject(0);
                    StringBuilder sb = new StringBuilder();
                    String id = data.getString("id");
                    String create_time = data.getString("create_time");
                    String update_time = data.getString("update_time");
                    String time_removed = data.getString("time_removed");
                    String bid = data.getString("bid");
                    String node_id = data.getString("node_id");
                    String score = data.getString("score");
                    String finish_timed = data.getString("finish_time");
                    // 字段分隔符要和hive建表语句保持一致,默认是\001
                    sb.append(id).append("\001").append(create_time).append("\001").append(update_time).append("\001")
                            .append(time_removed).append("\001").append(bid).append("\001").append(node_id).append("\001")
                            .append(score).append("\001").append(finish_timed);
                    // 收集结果往下游发送
                    out.collect(sb.toString());
                }
            }
        });
        // 打印测试
        dataStream.print("data");

        // 4.写入hdfs
        dataStream.addSink(
                StreamingFileSink
                        .forRowFormat(
                                new Path("hdfs://company-bigdata02/data/hive/warehouse/ods.db/ods_crs_eduplatform_node_flow_record_realtime"),
                                new SimpleStringEncoder<String>("UTF-8")
                        )
//                        .withBucketCheckInterval(1000L)
                        .withNewBucketAssignerAndPolicy(
                                new HiveBucketAssigner<>("yyyyMMdd", ZoneId.of("Asia/Shanghai"), "dt"),
                                DefaultRollingPolicy.builder()
                                        .withRolloverInterval(10*60*1000)    // 临时文件最长维持10min就会滚动生成正式文件
                                        .withInactivityInterval(10*60*1000)  // 临时文件10min不活跃就会滚动生成正式文件
                                        .withMaxPartSize(128*1024*1024)      // 临时文件最大达到128m就会滚动生成正式文件
                                        .build()
                        )
                        .build()
        );

        // 启动任务
        env.execute();
    }

    // 自定义类继承DateTimeBucketAssigner
    public static class HiveBucketAssigner<IN> extends DateTimeBucketAssigner<IN> {
        String partition;

        public HiveBucketAssigner(String formatString, ZoneId zoneId, String partition) {
            super(formatString, zoneId);
            this.partition = partition;
        }

        @Override
        public String getBucketId(IN element, Context context) {
            // hive分区对应分桶编号,最终会在hdfs路径/.../ods.db/${table}/后面生成${partition}=${formatString},比如dt=20220101
            return partition + "=" + super.getBucketId(element, context);
        }
    }
}
