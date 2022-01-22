package com.okccc.realtime.app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.ververica.cdc.connectors.mysql.MySQLSource;
import com.alibaba.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.okccc.realtime.app.func.DimSinkFunction;
import com.okccc.realtime.app.func.MyDeserialization;
import com.okccc.realtime.app.func.TableProcessFunction;
import com.okccc.realtime.bean.TableProcess;
import com.okccc.realtime.utils.MyKafkaUtil;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.flink.util.OutputTag;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.annotation.Nullable;

/**
 * Author: okccc
 * Date: 2021/10/7 下午3:45
 * Desc: 业务数据动态分流
 */
public class BaseDBApp {
    public static void main(String[] args) throws Exception {
        /*
         * canal/maxwell会把所有数据都写入一个topic,所以需要按照表的类型进行拆分
         * 事实数据写入kafka流中进一步处理形成宽表,维度数据写入可以通过主键查询的数据库,综合考虑键值对存储以及读写性能优先选择hbase
         * 那么flink该如何区分事实表和维度表呢？
         * 如果写在工程配置文件,那么业务端每次新增表都要修改配置重启程序,可以在mysql维护一张配置表,由flink-cdc动态获取表中更新的少量数据
         * 并以广播流的形式向下游传递,主流从广播流中获取配置信息,然后根据输出类型写入kafka/hbase,大量更新数据还是得用canal/maxwell抓取
         *
         * 本地运行任务时发现同一条数据会被重复多次写入kafka,是因为idea异常退出导致之前进程没有及时关闭,jps查看任务名称杀掉旧进程
         */

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // flink并行度和kafka分区数保持一致
        env.setParallelism(1);

        // 2.检查点相关设置
//        // 开启检查点
//        env.enableCheckpointing(5000, CheckpointingMode.EXACTLY_ONCE);
//        // 设置检查点超时时间
//        env.getCheckpointConfig().setCheckpointTimeout(60000);
//        // 设置重启策略
//        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3,3000));
//        // 设置job取消后检查点是否保留
//        env.getCheckpointConfig().enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
//        // 设置状态后端  内存|文件|RocksDB
//        env.setStateBackend(new FsStateBackend("hdfs://cdh1:8020/cp"));
//        // 设置操作hdfs的用户
//        System.setProperty("HADOOP_USER_NAME", "root");

        // 3.获取kafka数据(canal/maxwell抓取的主流数据)
        String topic = "ods_base_db";
        String groupId = "ods_base_db_group";
        DataStreamSource<String> kafkaStream = env.addSource(MyKafkaUtil.getKafkaSource(topic, groupId));
        // 打印测试
//        kafkaStream.print(">>>");

        // 4.结构转化,jsonStr -> JSONObject
        SingleOutputStreamOperator<JSONObject> jsonStream = kafkaStream.map(new MapFunction<String, JSONObject>() {
            @Override
            public JSONObject map(String value) {
                // {"data":[{"user_id":"1493"...}],"database":"maxwell","table":"comment_info","type":"INSERT"}
                return JSON.parseObject(value);
            }
        });

        // 5.过滤空数据
        SingleOutputStreamOperator<JSONObject> filterStream = jsonStream.filter(new FilterFunction<JSONObject>() {
            @Override
            public boolean filter(JSONObject value) {
                return value.getString("table") != null
                        && value.getString("table").length() > 0
                        && value.getString("data") != null
                        && value.getString("data").length() > 0
                        && !"delete".equalsIgnoreCase(value.getString("type"));
            }
        });

        // 6.获取flink-cdc抓取的mysql配置表数据
        SourceFunction<String> sourceFunction = MySQLSource.<String>builder()
                .hostname("localhost")
                .port(3306)
                .databaseList("realtime")
                .tableList("realtime.table_process")
                .username("root")
                .password("root@123")
                .startupOptions(StartupOptions.initial())  // initial启动时会扫描历史数据,然后继续读取最新的binlog
                .deserializer(new MyDeserialization())
                .build();
        // 广播流需要定义广播状态,且必须是MapState类型,key=表:操作类型,value=TableProcess对象
        MapStateDescriptor<String, TableProcess> mapState =
                new MapStateDescriptor<>("map-state", String.class, TableProcess.class);
        // 将配置表数据作为广播流往下游传输,让每个并行度的主流都能读取到
        BroadcastStream<String> broadcastStream = env
                .addSource(sourceFunction)
                .broadcast(mapState);

        // 7.按照业务类型分流
        // 声明侧输出流标签
        OutputTag<JSONObject> dimTag = new OutputTag<JSONObject>("dim"){};
        // 将主流(非广播流)和配置流(广播流)进行双流连接
        SingleOutputStreamOperator<JSONObject> factStream = filterStream
                .connect(broadcastStream)
                // 业务数据放主流,维度数据放侧输出流
                .process(new TableProcessFunction(dimTag, mapState));
        // 获取侧输出流
        DataStream<JSONObject> dimStream = factStream.getSideOutput(dimTag);
        // 打印测试
        factStream.print("fact");
        // {"data":[{"id":"34861"...}],"type":"INSERT","database":"maxwell","sink_table":"dwd_order_info","table":"order_info","ts":1638174777426}
        dimStream.print("dim");
        // {"data":[{"name":"tim"...}],"type":"UPDATE","database":"maxwell","sink_table":"dim_user_info","table":"user_info","ts":1638174766675}

        // 8.将维度数据写入hbase
        dimStream.addSink(new DimSinkFunction());

        // 9.将事实数据写入dwd层对应的topic
        factStream.addSink(MyKafkaUtil.getKafkaSinkBySchema(new KafkaSerializationSchema<JSONObject>() {
            @Override
            public ProducerRecord<byte[], byte[]> serialize(JSONObject element, @Nullable Long timestamp) {
                // 获取业务流的输出topic
                String topic = element.getString("sink_table");
                // 获取业务流的输出内容,提取data部分往下游传输{"user_id":"1493","name":"aaa","id":"1450387954178547736"}
                String data = element.getJSONArray("data").getJSONObject(0).toJSONString();
                return new ProducerRecord<>(topic, data.getBytes());
            }
        }));

        // 启动任务
        env.execute();
    }
}
