package com.okccc.app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONAware;
import com.alibaba.fastjson.JSONObject;
import com.okccc.func.FilterUserFunction;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * @Author: okccc
 * @Date: 2023/3/28 17:00:14
 * @Desc: 流量域 - 独立访客明细(DataStream - KafkaSource - State - KafkaSink)
 */
public class DwdUniqueVisitor {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层日志数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_page_log","dwd_unique_visitor_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaSource");

        // 3.将数据格式转换成JSON
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.map((JSON::parseObject));

        // 4.筛选独立访客,状态编程
        SingleOutputStreamOperator<JSONObject> filterStream = jsonStream
                .keyBy(r -> r.getJSONObject("common").getString("mid"))
                .filter(new FilterUserFunction());

        // 5.将数据写入kafka
        filterStream
                .map(JSONAware::toJSONString)
                .sinkTo(FlinkUtil.getKafkaSink("dwd_unique_visitor", "uv_"));

        // 6.启动任务
        env.execute("DwdUniqueVisitor");
    }
}
