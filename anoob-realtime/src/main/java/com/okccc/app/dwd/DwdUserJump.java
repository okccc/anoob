package com.okccc.app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternFlatTimeoutFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.RichPatternFlatSelectFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @Author: okccc
 * @Date: 2023/3/29 10:26:40
 * @Desc: 流量域 - 用户跳出明细(DataStream - KafkaSource - FlinkCEP - KafkaSink)
 *
 * 跳转：用户访问网站某个页面后继续访问其他页面,跳转率=跳转次数/访问次数,可以推断用户流失、订单转化、漏斗分析...
 * 跳出：用户的某次会话只访问了一个页面就退出了,跳出率=跳出次数/访问次数,可以推断渠道引流用户质量、应用页面优化效果...
 * 离线：可以获取一整天的数据,统计所有会话的页面数,筛选出页面数等于1的会话即可
 * 实时：有的用户会"装死",既不退出也不浏览就挂在那,肯定不能一直等下去,比如十分钟没动静就算跳出,条件判断加超时事件的组合用FlinkCEP
 * 情况1：两条紧邻的首页日志进入算子,那么第一条首页日志判定为跳出
 * 情况2：第一条首页日志进入算子,超时时间内没有收到第二条日志,那么第一条首页日志也判定为跳出
 * 情况3：第一条首页日志进入算子,超时时间内收到的第二条日志为非首页日志,那么第一条首页日志是跳转
 */
public class DwdUserJump {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层日志数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_page_log","dwd_user_jump_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaSource");

        // 3.将数据格式转换成JSON
        // {"common":{"ba":"Huawei", "is_new":"1"...}, "page":{"page_id":"cart"...}, "ts":1634284695000}
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.map(JSON::parseObject);

        // 4.按照mid分组,每组数据表示当前设备访问情况
        KeyedStream<JSONObject, String> keyedStream = jsonStream
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<JSONObject>forBoundedOutOfOrderness(Duration.ofSeconds(2))
                                .withTimestampAssigner((element, recordTimestamp) -> element.getLong("ts"))
                )
                .keyBy(r -> r.getJSONObject("common").getString("mid"));

        // 5.定义匹配模板(重点)
        Pattern<JSONObject, JSONObject> pattern = Pattern
                .<JSONObject>begin("first")
                .where(new SimpleCondition<JSONObject>() {
                    @Override
                    public boolean filter(JSONObject value) {
                        // 当前页面是一个会话的开头,last_page_id为空
                        return value.getJSONObject("page").getString("last_page_id") == null;
                    }
                })
                .next("second")
                .where(new SimpleCondition<JSONObject>() {
                    @Override
                    public boolean filter(JSONObject value) {
                        // 情况1：紧接着的下一个页面又是一个会话的开头,那么上面那个first就是跳出
                        return value.getJSONObject("page").getString("last_page_id") == null;
                    }
                })
                .within(Time.seconds(10));  // 情况2：超时时间内未收到第二条日志,那么上面那个first也是跳出

        // 6.将pattern应用到数据流
        PatternStream<JSONObject> patternStream = CEP.pattern(keyedStream, pattern);

        // 7.提取事件(process/select/flatSelect)
        OutputTag<String> outputTag = new OutputTag<String>("timeout") {};
        SingleOutputStreamOperator<String> selectStream = patternStream.flatSelect(
                // 超时事件放侧输出流
                outputTag,
                // 超时事件：情况2
                new PatternFlatTimeoutFunction<JSONObject, String>() {
                    @Override
                    public void timeout(Map<String, List<JSONObject>> map, long l, Collector<String> out) {
                        JSONObject first = map.get("first").get(0);
                        out.collect(first.toJSONString());
                    }
                },
                // 匹配事件：情况1
                new RichPatternFlatSelectFunction<JSONObject, String>() {
                    @Override
                    public void flatSelect(Map<String, List<JSONObject>> map, Collector<String> out) {
                        JSONObject first = map.get("first").get(0);
                        out.collect(first.toJSONString());
                    }
                }
        );
        SideOutputDataStream<String> timeoutStream = selectStream.getSideOutput(outputTag);
        selectStream.print("select");
        timeoutStream.print("timeout");

        // 8.合并数据写入kafka
        DataStream<String> unionStream = selectStream.union(timeoutStream);
        unionStream.sinkTo(FlinkUtil.getKafkaSink("dwd_user_jump", "uj_"));

        // 9.启动任务
        env.execute("DwdUserJump");
    }
}