package com.okccc.app.dwd;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;

/**
 * @Author: okccc
 * @Date: 2023/7/31 18:22:29
 * @Desc: 业务数据分流(DataStream - KafkaSource - KafkaSink)
 */
public class BaseDbApp {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 设置检查点和状态后端
//        FlinkUtil.setCheckpointAndStateBackend(env);

        // 2.读取ods层业务数据(Canal/Maxwell/FlinkCDC)
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("ods_base_db", "base_db_app_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaSource");

        // 3.将数据格式转换成JSON
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String value, Collector<JSONObject> out) {
                try {
                    JSONObject jsonObject = JSON.parseObject(value);
                    String type = jsonObject.getString("type");
                    // 保留新增、更新以及初始化数据
                    if ("insert".equals(type) || "update".equals(type) || "bootstrap-insert".equals(type)) {
                        // {"database":"mock","table":"comment_info","data":{"sku_id":32,"appraise":"A"},"type":"insert","ts":1690885529}
                        out.collect(jsonObject);
                    }
                } catch (Exception e) {
                    // 脏数据可以直接过滤或写侧输出流
                    System.out.println(">>>" + value);
                }
            }
        });

        // 4.按照表名拆分将数据写入dwd层对应topic
        jsonStream.sinkTo(FlinkUtil.getKafkaSinkBySchema(new KafkaRecordSerializationSchema<JSONObject>() {
            @Override
            public ProducerRecord<byte[], byte[]> serialize(JSONObject element, KafkaSinkContext context, Long timestamp) {
                // 这里包含所有事实表和维度表,其实kafka应该只写事实表,所以该方案不够完善
                String table = element.getString("table");
                return new ProducerRecord<>("dwd_" + table, element.toJSONString().getBytes(StandardCharsets.UTF_8));
            }
        }));

        // 5.启动任务
        env.execute("BaseDbApp");
    }
}
