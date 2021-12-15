package com.okccc.realtime.app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONAware;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.utils.DateUtil;
import com.okccc.realtime.utils.MyKafkaUtil;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/**
 * Author: okccc
 * Date: 2021/10/25 下午6:20
 * Desc: 独立访客统计,从用户行为日志中过滤去重得到
 */
public class UniqueVisitorApp {
    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // flink并行度和kafka分区数保持一致
        env.setParallelism(1);

        // 2.获取kafka数据
        String topic = "dwd_page_log";
        String groupId = "unique_visitor_app_group";
        DataStreamSource<String> kafkaStream = env.addSource(MyKafkaUtil.getKafkaSource(topic, groupId));
        // 打印测试
        kafkaStream.print("pv");

        // 3.结构转化,jsonStr -> JSONObject
        // {"common":{"mid":"mid_12"...},"page":{"page_id":"cart","last_page_id":"home"...},"ts":1634284695000}
        SingleOutputStreamOperator<JSONObject> jsonStream = kafkaStream.map(JSON::parseObject);

        // 4.过滤数据
        SingleOutputStreamOperator<JSONObject> filterStream = jsonStream
                // 按照mid分组,每组数据表示当前设备的访问情况
                .keyBy(r -> r.getJSONObject("common").getString("mid"))
                // filter算子针对每个输入元素输出0/1个元素
                .filter(new RichFilterFunction<JSONObject>() {
                    // 声明状态,记录设备上次访问日期
                    private ValueState<String> lastVisitDate;

                    @Override
                    public void open(Configuration parameters) {
                        // 创建状态描述符
                        ValueStateDescriptor<String> valueState = new ValueStateDescriptor<>("lastVisitDate", String.class);
                        // 独立访客就是统计日活,状态主要用来筛选当天是否访问过,所以要设置有效时间ttl,避免状态常驻内存
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
                        // 初始化状态变量
                        lastVisitDate = getRuntimeContext().getState(valueState);
                    }

                    @Override
                    public boolean filter(JSONObject value) throws Exception {
                        // a.先判断是否从别的页面跳转过来
                        String lastPageId = value.getJSONObject("page").getString("last_page_id");
                        if (lastPageId != null && lastPageId.length() > 0) {
                            // 有上一页,说明肯定不是第一次访问,直接过滤
                            return false;
                        }
                        // b.不是跳转页面,就继续判断当前访问日期和上次访问日期是否相同
                        String lastDate = lastVisitDate.value();
                        String curDate = DateUtil.parseUnixToDateTime(value.getLong("ts"));
                        if (lastDate != null && lastDate.length() > 0 && curDate.equals(lastDate)) {
                            // 已经访问过,过滤该条数据
                            return false;
                        } else {
                            // 还没访问过,更新状态变量,保留该条数据
                            lastVisitDate.update(curDate);
                            return true;
                        }
                    }
                });
        // 打印测试
        filterStream.print("uv");

        // 5.将过滤后的数据写入dwm层对应的topic
        filterStream
                .map(JSONAware::toJSONString)
                .addSink(MyKafkaUtil.getKafkaSink("dwm_unique_visitor"));

        // 启动任务
        env.execute();
    }
}
