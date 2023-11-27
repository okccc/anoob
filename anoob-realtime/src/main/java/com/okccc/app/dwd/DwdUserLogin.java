package com.okccc.app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.UserLogin;
import com.okccc.func.LoginProcessFunction;
import com.okccc.util.DateUtil;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2023/9/6 18:28:26
 * @Desc: 用户域 - 用户登录明细(DataStream - KafkaSource - State & Timer - KafkaSink)
 */
public class DwdUserLogin {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层日志数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_page_log","dwd_user_login_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaSource");

        // 3.转换数据格式：需要清洗数据就先转换成JSON,不然就直接转换成java代码更通用的JavaBean
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.flatMap(new FlatMapFunction<String, JSONObject>() {
            @Override
            public void flatMap(String value, Collector<JSONObject> out) {
                JSONObject jsonObject = JSON.parseObject(value);
                if (jsonObject.getJSONObject("common").getString("uid") != null) {
                    out.collect(jsonObject);
                }
            }
        });

        // 4.筛选登录用户,状态编程
        SingleOutputStreamOperator<JSONObject> loginStream = jsonStream
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner((element, recordTimestamp) -> element.getLong("ts")))
                .keyBy(r -> r.getJSONObject("common").getString("mid"))
                .process(new LoginProcessFunction());

        // 5.将数据写入kafka
        loginStream
                .map(new MapFunction<JSONObject, String>() {
                    @Override
                    public String map(JSONObject value) {
                        // {"common":{"ar":"230000","uid":"12","os":"Android 10.0","ch":"xiaomi","is_new":"1","md":"Huawei P30","mid":"mid_2","vc":"v2.1.132","ba":"Huawei"},"page":{"page_id":"home","during_time":11169},"ts":1693217356000}
                        JSONObject common = value.getJSONObject("common");
                        Long ts = value.getLong("ts");
                        // dwd层是明细数据,通常对应一个JavaBean
                        UserLogin dwdUserLoginBean = UserLogin.builder()
                                .userId(common.getString("uid"))
                                .provinceId(common.getString("ar"))
                                .midId(common.getString("mid"))
                                .brand(common.getString("ba"))
                                .model(common.getString("md"))
                                .channel(common.getString("ch"))
                                .version(common.getString("vc"))
                                .os(common.getString("os"))
                                .loginTime(DateUtil.parseUnixToDateTime(ts))
                                .ts(ts)
                                .build();
                        return JSON.toJSONString(dwdUserLoginBean);
                    }
                })
                .sinkTo(FlinkUtil.getKafkaSink("dwd_user_login", "ul_"));

        // 6.启动任务
        env.execute("DwdUserLogin");
    }
}
