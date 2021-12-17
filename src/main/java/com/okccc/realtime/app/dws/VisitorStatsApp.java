package com.okccc.realtime.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.bean.VisitorStats;
import com.okccc.realtime.utils.DateUtil;
import com.okccc.realtime.utils.MyKafkaUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * Author: okccc
 * Date: 2021/12/13 10:14 上午
 * Desc: 访客主题宽表
 */
public class VisitorStatsApp {
    public static void main(String[] args) throws Exception {
        /*
         * dws层将多个实时数据以主题的方式轻度聚合方便查询,主题表包含维度(分组)和度量(聚合)两部分数据
         * connect双流连接的元素类型可以不同,union多流合并的元素类型必须相同,所以要整合各个实时数据的字段统一生成主题宽表
         * 维度数据：渠道、地区、版本、新老用户
         * 事实数据：PV、UV、进入页面、跳出页面、连续访问时长
         * 访客主题如果按照mid分组,记录的都是当前设备的行为,数据量很小聚合效果不明显,起不到数据压缩的作用
         * 商品主题可以按照pid分组,因为会同时有很多人对该商品做操作,数据量很可观可以达到聚合效果
         */

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // flink并行度和kafka分区数保持一致
        env.setParallelism(1);

        // 2.获取kafka数据
        String pageViewTopic = "dwd_page_log";
        String uniqueVisitorTopic = "dwm_unique_visitor";
        String userJumpDetailTopic = "dwm_user_jump_detail";
        String groupId = "visitor_stats_app_group";
        DataStreamSource<String> pvStream = env.addSource(MyKafkaUtil.getKafkaSource(pageViewTopic, groupId));
        DataStreamSource<String> uvStream = env.addSource(MyKafkaUtil.getKafkaSource(uniqueVisitorTopic, groupId));
        DataStreamSource<String> ujdStream = env.addSource(MyKafkaUtil.getKafkaSource(userJumpDetailTopic, groupId));
        // 打印测试
//        pvStream.print("pv");
//        uvStream.print("uv");
//        ujdStream.print("ujd");

        // 3.结构转换
        SingleOutputStreamOperator<VisitorStats> pvStatsStream = pvStream
                // 将pv流数据封装成访客主题实体类
                .map(new MapFunction<String, VisitorStats>() {
                    @Override
                    public VisitorStats map(String value) {
                        // {"common":{"ar":"","ch":"","vc":""...},"page":{...},"displays":[{},{}...],"ts":1639123347000}
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject common = jsonObject.getJSONObject("common");
                        JSONObject page = jsonObject.getJSONObject("page");
                        VisitorStats visitorStats = new VisitorStats(
                                "",
                                "",
                                common.getString("vc"),
                                common.getString("ch"),
                                common.getString("ar"),
                                common.getString("is_new"),
                                1L,
                                0L,
                                0L,
                                0L,
                                page.getLong("during_time"),
                                jsonObject.getLong("ts")
                        );
                        // 先判断是否从别的页面跳转过来
                        String lastPageId = page.getString("last_page_id");
                        if (lastPageId == null || lastPageId.length() == 0) {
                            // 不是跳转过来的说明是新页面,进入次数+1
                            visitorStats.setSv_ct(1L);
                        }
                        return visitorStats;
                    }
                });

        SingleOutputStreamOperator<VisitorStats> uvStatsStream = uvStream
                // 将uv流数据封装成访客主题实体类
                .map(new MapFunction<String, VisitorStats>() {
                    @Override
                    public VisitorStats map(String value) {
                        // {"common":{"ar":"","ch":"","vc":""...},"page":{...},"displays":[{},{}...],"ts":1639123347000}
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject common = jsonObject.getJSONObject("common");
                        JSONObject page = jsonObject.getJSONObject("page");
                        return new VisitorStats(
                                "",
                                "",
                                common.getString("vc"),
                                common.getString("ch"),
                                common.getString("ar"),
                                common.getString("is_new"),
                                0L,
                                1L,
                                0L,
                                0L,
                                0L,
                                jsonObject.getLong("ts")
                        );
                    }
                });

        SingleOutputStreamOperator<VisitorStats> ujdStatsStream = ujdStream
                // 将跳出明细流数据封装成访客主题实体类
                .map(new MapFunction<String, VisitorStats>() {
                    @Override
                    public VisitorStats map(String value) {
                        // {"common":{"ar":"","ch":"","vc":""...},"page":{...},"displays":[{},{}...],"ts":1639123347000}
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject common = jsonObject.getJSONObject("common");
                        return new VisitorStats(
                                "",
                                "",
                                common.getString("vc"),
                                common.getString("ch"),
                                common.getString("ar"),
                                common.getString("is_new"),
                                0L,
                                0L,
                                0L,
                                1L,
                                0L,
                                jsonObject.getLong("ts")
                        );
                    }
                });

        // 4.合并多条流
        DataStream<VisitorStats> unionStream = pvStatsStream.union(uvStatsStream, ujdStatsStream);
        // 打印测试
//        unionStream.print("union");  // VisitorStats(stt=, edt=, vc=v2.1.132, ch=Appstore, ar=440000, is_new=1, uv_ct=0, pv_ct=1, sv_ct=0, uj_ct=0, dur_sum=8042, ts=1639120380000)


        // 启动任务
        env.execute();
    }
}
