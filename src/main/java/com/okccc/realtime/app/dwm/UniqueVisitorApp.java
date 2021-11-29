package com.okccc.realtime.app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.utils.MyKafkaUtil;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.text.SimpleDateFormat;

/**
 * Author: okccc
 * Date: 2021/10/25 下午6:20
 * Desc: 独立访客统计
 */
public class UniqueVisitorApp {
    public static void main(String[] args) throws Exception {
        // 1.环境准备
        // 创建流处理执行环境
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

        // 3.获取kafka数据
        String topic = "dwd_page_log";
        String groupId = "unique_visitor_app_group";
        DataStreamSource<String> kafkaStream = env.addSource(MyKafkaUtil.getKafkaSource(topic, groupId));

        // 4.结构转化,jsonStr -> JSONObject
        SingleOutputStreamOperator<JSONObject> jsonStream = kafkaStream.map(new MapFunction<String, JSONObject>() {
            @Override
            public JSONObject map(String value) {
                // {"common":{"ba":"Huawei","is_new":"1"...}, "page":{"page_id":"cart"...}, "ts":1634284695000}
                return JSON.parseObject(value);
            }
        });

        // 5.过滤数据
        SingleOutputStreamOperator<JSONObject> filterStream = jsonStream
                // 按照mid分组
                .keyBy(r -> r.getJSONObject("common").getString("mid"))
                // filter(过滤)针对每个输入元素输出0/1个元素
                .filter(new RichFilterFunction<JSONObject>() {
                    // 声明状态,记录设备上次访问日期
                    private ValueState<String> lastVisitDate;
                    private SimpleDateFormat sdf;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 初始化状态变量
                        ValueStateDescriptor<String> valueState = new ValueStateDescriptor<>("last-visit", String.class);
                        // 独立访客其实就是统计日活,状态值主要用来筛选当天是否访问过,所以要设置状态的有效时间ttl
                        valueState.enableTimeToLive(
                                StateTtlConfig
                                        // 将状态的存活时间设置为1天
                                        .newBuilder(Time.days(1))
                                        // 默认值,当状态创建或写入的时候更新其失效时间
//                                        .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                                        // 默认值,状态过期后如果没有被及时垃圾回收,是否返回给调用者
//                                        .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                                        .build()
                        );
                        lastVisitDate = getRuntimeContext().getState(valueState);
                        sdf = new SimpleDateFormat("yyyyMMdd");
                    }

                    @Override
                    public boolean filter(JSONObject value) throws Exception {
                        // 先判断是否从别的页面跳转过来
                        String lastPageId = value.getJSONObject("common").getString("last_page_id");
                        if (lastPageId != null && lastPageId.length() > 0) {
                            return false;
                        }
                        // 再判断上次访问日期
                        String lastDate = lastVisitDate.value();
                        String curDate = sdf.format(value.getLong("ts"));
                        if (lastDate != null && lastDate.length() > 0 && curDate.equals(lastDate)) {
                            // 已经访问过
                            return false;
                        } else {
                            // 还没访问过
                            lastVisitDate.update(curDate);
                            return true;
                        }
                    }
                });

        // 打印测试
        filterStream.print();

        // 6.将过滤后的数据写入dwm层对应的topic
        filterStream
                .map(new MapFunction<JSONObject, String>() {
                    @Override
                    public String map(JSONObject value) throws Exception {
                        return value.toJSONString();
                    }
                })
                .addSink(MyKafkaUtil.getKafkaSink("dwm_unique_visitor"));

        // 启动任务
        env.execute();
    }
}
