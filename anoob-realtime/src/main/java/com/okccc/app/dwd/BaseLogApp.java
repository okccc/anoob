package com.okccc.app.dwd;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.okccc.func.CheckUserFunction;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

/**
 * @Author: okccc
 * @Date: 2023/3/22 18:28:20
 * @Desc: 日志数据分流(DataStream - KafkaSource - State - SideOutput - KafkaSink)
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/zh/docs/connectors/datastream/kafka/#kafka-source
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/side_output/
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/zh/docs/connectors/datastream/kafka/#kafka-sink
 *
 * 计算框架flink,存储框架kafka(消息队列,实时读 & 实时写)
 * 存储框架为什么选kafka而不是hive
 * 实时计算必须同时满足实时读和实时写,实时写mysql/hive/ck等存储系统都可以,但实时读显然需要一个消息队列中间件,总不能每次都去查数据源
 *
 * 离线和实时区别：1.数据处理方式 -> 流处理/批处理  2.数据处理延迟 -> T+0/T+1
 * 普通实时计算时效性更好,实时数仓分层可以提高数据复用性
 * ODS层：nginx日志(flume)、业务数据(maxwell),存kafka
 * DIM层：维度数据,贯穿整个数仓,存hbase
 * DWD层：将原始数据按照业务类型分流并简单处理得到明细数据,存kafka
 * DWS层：每来一条数据都会分组,按照用户、商品、地区等维度聚合,聚合结果放列存储方便查询,且一张dws表后面会出很多不同指标,存clickhouse比较好
 * ADS层：实时大屏最终展示的聚合结果每分每秒都在变,无需落盘,调用接口模块中查询clickhouse的sql语句即
 *
 * flink实时流处理步骤
 * 流处理环境 - (检查点设置) - 获取kafka/mysql数据源 - 结构转换 - {etl处理} - 将数据写入kafka/hbase/clickhouse/redis
 * etl处理：分流/过滤/去重/状态修复/cep/双流join/维表关联/多流union/分组开窗聚合等具体业务需求,也是代码的核心所在
 * 当处理较复杂的业务逻辑时,步骤会很繁琐,可以写一段逻辑就测试一下中间结果,便于调试代码排查问题
 *
 * -- clickhouse建表语句
 * create table if not exists dirty_log (
 *   log            String,
 *   insert_time    datetime
 * ) engine = MergeTree order by (insert_time);
 */
public class BaseLogApp {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 设置检查点和状态后端
//        FlinkUtil.setCheckpointAndStateBackend(env);

        // 2.读取ods层日志数据(Flume)
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("ods_base_log","base_log_app_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaSource");

        // 3.将数据格式转换成JSON
        OutputTag<String> outputTag = new OutputTag<>("dirty") {};
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.process(new ProcessFunction<>() {
            @Override
            public void processElement(String value, Context ctx, Collector<JSONObject> out) {
                try {
                    JSONObject jsonObject = JSON.parseObject(value);
                    // {"common":{"uid":"31","is_new":"1"},"page":{"page_id":"mine"},"ts":1679627986000}
                    out.collect(jsonObject);
                } catch (Exception e) {
                    // 脏数据放侧输出流
                    ctx.output(outputTag, value);
                }
            }
        });
        // 将脏数据写入clickhouse,方便比对日志
//        DataStream<String> dirtyStream = jsonStream.getSideOutput(outputTag);
//        dirtyStream.addSink(ClickHouseUtil.getJdbcSink());

        // 4.新老访客校验,状态编程
        SingleOutputStreamOperator<JSONObject> checkStream = jsonStream
                .keyBy(r -> r.getJSONObject("common").getString("mid"))
                .map(new CheckUserFunction());

        // 5.使用侧输出流将日志按照类型分流
        OutputTag<String> startTag = new OutputTag<>("start") {};
        OutputTag<String> displayTag = new OutputTag<>("display") {};
        OutputTag<String> actionTag = new OutputTag<>("action") {};
        OutputTag<String> errorTag = new OutputTag<>("error") {};

        SingleOutputStreamOperator<String> pageStream = checkStream.process(new ProcessFunction<>() {
            @Override
            public void processElement(JSONObject value, Context ctx, Collector<String> out) {
                // 尝试获取错误日志
                JSONObject err = value.getJSONObject("err");
                if (err != null) {
                    ctx.output(errorTag, value.toJSONString());
                }
                value.remove("err");

                // 尝试获取启动日志
                JSONObject start = value.getJSONObject("start");
                if (start != null) {
                    ctx.output(startTag, value.toJSONString());
                } else {
                    // 获取非启动日志的公共信息、页面id、时间戳
                    JSONObject common = value.getJSONObject("common");
                    String pageId = value.getJSONObject("page").getString("page_id");
                    Long ts = value.getLong("ts");

                    // 尝试获取曝光日志
                    JSONArray displays = value.getJSONArray("displays");
                    if (displays != null && !displays.isEmpty()) {
                        for (int i = 0; i < displays.size(); i++) {
                            JSONObject display = displays.getJSONObject(i);
                            display.put("common", common);
                            display.put("page_id", pageId);
                            display.put("ts", ts);
                            ctx.output(displayTag, display.toJSONString());
                        }
                    }
                    value.remove("displays");

                    // 尝试获取动作日志
                    JSONArray actions = value.getJSONArray("actions");
                    if (actions != null && !actions.isEmpty()) {
                        for (int i = 0; i < actions.size(); i++) {
                            JSONObject action = actions.getJSONObject(i);
                            action.put("common", common);
                            action.put("page_id", pageId);
                            ctx.output(actionTag, action.toJSONString());
                        }
                    }
                    value.remove("actions");

                    // 剩下的就是页面日志
                    out.collect(value.toJSONString());
                }
            }
        });

        DataStream<String> startStream = pageStream.getSideOutput(startTag);
        DataStream<String> displayStream = pageStream.getSideOutput(displayTag);
        DataStream<String> actionStream = pageStream.getSideOutput(actionTag);
        DataStream<String> errorStream = pageStream.getSideOutput(errorTag);

        // 6.将不同流写入dwd层对应的topic
        // 为什么不在判断日志类型的时候直接写入kafka而是先分流再写入？
        // KafkaSink<IN>已经实现了TwoPhaseCommittingSink<IN, KafkaCommittable>接口保证精准一次性,自己写的话要手动实现
        pageStream.sinkTo(FlinkUtil.getKafkaSink("dwd_page_log", "page_"));
        startStream.sinkTo(FlinkUtil.getKafkaSink("dwd_start_log", "start_"));
        displayStream.sinkTo(FlinkUtil.getKafkaSink("dwd_display_log", "display_"));
        actionStream.sinkTo(FlinkUtil.getKafkaSink("dwd_action_log", "action_"));
        errorStream.sinkTo(FlinkUtil.getKafkaSink("dwd_error_log", "error_"));

        // 7.启动任务
        env.execute("BaseLogApp");
    }
}
