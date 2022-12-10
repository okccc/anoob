package com.okccc.realtime.app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.util.MyFlinkUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternFlatSelectFunction;
import org.apache.flink.cep.PatternFlatTimeoutFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @Author: okccc
 * @Date: 2021/10/27 下午4:29
 * @Desc: 用户跳出明细统计
 */
public class UserJumpDetailApp {
    public static void main(String[] args) throws Exception {
        /*
         * 跳转：用户访问网站某个页面后继续访问其他页面,跳转率=跳转次数/访问次数,可以推断用户流失、订单转化、漏斗分析...
         * 跳出：用户访问网站某个页面后直接退出,不再继续访问其他页面,跳出率=跳出次数/访问次数,可以推断渠道引流用户质量、应用页面优化效果...
         * 如何判断用户已经跳出了呢？
         * 1.当前页面是用户近期访问的第一个页面,如果已经是从别的页面跳转过来就不是跳出,可根据last_page_id判断
         * 2.之后不再访问其它页面,比如10分钟没有跳转就算跳出,放到侧输出流单独处理,跳出行为是条件判断加超时事件的组合,可通过flink-cep实现
         */

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // flink并行度和kafka分区数保持一致
        env.setParallelism(1);

        // 2.获取kafka数据
        String topic = "dwd_page_log";
        String groupId = "user_jump_detail_app_group";
        DataStreamSource<String> kafkaStream = env.addSource(MyFlinkUtil.getKafkaSource(topic, groupId));
        // 打印测试
//        kafkaStream.print("pv");

        // 3.结构转化,jsonStr -> JSONObject
        KeyedStream<JSONObject, String> keyedStream = kafkaStream
                // {"common":{"ba":"Huawei","is_new":"1"...}, "page":{"page_id":"cart"...}, "ts":1634284695000}
                .map((MapFunction<String, JSONObject>) JSON::parseObject)
                // 因为涉及时间判断,所以要设定事件时间和水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner(new SerializableTimestampAssigner<JSONObject>() {
                                    @Override
                                    public long extractTimestamp(JSONObject element, long recordTimestamp) {
                                        return element.getLong("ts");
                                    }
                                })
                )
                // 按照mid分组,每组数据表示当前设备访问情况
                .keyBy(r -> r.getJSONObject("common").getString("mid"));

        // 4.定义匹配模板
        Pattern<JSONObject, JSONObject> pattern = Pattern
                .<JSONObject>begin("first")
                .where(new SimpleCondition<JSONObject>() {
                    @Override
                    public boolean filter(JSONObject value) {
                        // 条件1.当前页面是访问的第一个页面
                        String lastPageId = value.getJSONObject("page").getString("last_page_id");
                        return lastPageId == null || lastPageId.length() <= 0;
                    }
                })
                .followedBy("second")
                .where(new SimpleCondition<JSONObject>() {
                    @Override
                    public boolean filter(JSONObject value) {
                        // 条件2.之后10分钟内访问过其它页面
                        String pageId = value.getJSONObject("page").getString("page_id");
                        return pageId != null && pageId.length() > 0;
                    }
                })
                .within(Time.seconds(10));

        // 5.将pattern应用到数据流
        PatternStream<JSONObject> patternStream = CEP.pattern(keyedStream, pattern);

        // 声明侧输出流标签
        OutputTag<String> jumpTag = new OutputTag<String>("jump"){};
        // 6.select/flatSelect提取匹配事件
        SingleOutputStreamOperator<Object> result = patternStream.flatSelect(
                // 将超时事件放到侧输出流
                jumpTag,
                // 处理超时事件
                new PatternFlatTimeoutFunction<JSONObject, String>() {
                    @Override
                    public void timeout(Map<String, List<JSONObject>> pattern, long timeoutTimestamp, Collector<String> out) {
                        List<JSONObject> first = pattern.get("first");
                        for (JSONObject jsonObject : first) {
                            // 收集结果往下游传输
                            out.collect(jsonObject.toJSONString());
                        }
                    }
                },
                // 处理匹配事件,该需求用不到
                new PatternFlatSelectFunction<JSONObject, Object>() {
                    @Override
                    public void flatSelect(Map<String, List<JSONObject>> pattern, Collector<Object> out) {
                    }
                }
        );

        // 7.获取侧输出流
        DataStream<String> jumpStream = result.getSideOutput(jumpTag);
        // 打印测试
        jumpStream.print("ujd");
        // 8.将跳出数据写入dwm层对应的topic
        jumpStream.addSink(MyFlinkUtil.getKafkaSink("dwm_user_jump_detail"));

        // 启动任务
        env.execute();
    }
}
