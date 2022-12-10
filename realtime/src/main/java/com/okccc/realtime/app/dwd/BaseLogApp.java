package com.okccc.realtime.app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.util.ClickHouseUtil;
import com.okccc.realtime.util.DateUtil;
import com.okccc.realtime.util.MyFlinkUtil;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

/**
 * @Author: okccc
 * @Date: 2021/10/3 下午4:32
 * @Desc: 日志数据分流
 */
public class BaseLogApp {
    public static void main(String[] args) throws Exception {
        /*
         * 离线和实时区别：1.数据处理方式 -> 流处理/批处理  2.数据处理延迟 -> T+0/T+1
         * 普通实时计算时效性更好,实时数仓分层可以提高数据复用性
         * ODS：日志和业务原始数据
         * DWD：将原始数据分流为订单和用户等明细数据
         * DIM：维度数据
         * DWM：将明细数据进一步加工,DWD到DWS中间的部分计算结果会被多个DWS层主题复用,所以会有中间层,比如独立访客/跳出明细/订单宽表/支付宽表
         * DWS：将数据轻度聚合形成主题宽表
         * ADS：将ClickHouse中的数据继续筛选聚合做可视化
         *
         * ==========================================================
         * 统计主题  |  需求指标  |  数仓层级  |  输出方式  |  计算来源
         * ==========================================================
         *         |  pv    |  dwd  |  大屏展示  | dwd_page_log直接计算
         *         |  uv    |  dwm  |  大屏展示  | dwd_page_log过滤去重
         * user    | 进入页面 |  dwd  |  大屏展示  | dwd_page_log行为判断
         *         | 跳出页面 |  dwm  |  大屏展示  | dwd_page_log行为判断
         *         | 连续访问时长 | dwd | 大屏展示 | dwd_page_log直接获取
         * ==========================================================
         *         |  点击	 |  dwd  |  多维分析  | dwd_page_log直接计算
         *         |  曝光	 |  dwd  |  多维分析  | dwd_page_log直接计算
         *         |  收藏	 |  dwd  |  多维分析  | 收藏表
         * product |  购物车  |  dwd  |  多维分析  | 购物车表
         *         |  下单	 |  dwm  |  大屏展示  | 订单宽表
         *         |  支付	 |  dwm  |  多维分析  | 支付宽表
         *         |  退款	 |  dwd  |  多维分析  | 退款表
         *         |  评论	 |  dwd  |  多维分析  | 评论表
         * ==========================================================
         *         |  pv	 |  dwd  |  多维分析	 | dwd_page_log直接计算
         * area    |  uv	 |  dwm  |  多维分析	 | dwd_page_log过滤去重
         *         |  下单	 |  dwm  |  大屏展示	 | 订单宽表
         * ==========================================================
         *         | 搜索关键词	 | dwd | 大屏展示 | dwd_page_log直接计算
         * keyword | 点击商品关键词 | dws | 大屏展示 | 商品主题下单再次聚合
         *         | 下单商品关键词 | dws | 大屏展示 | 商品主题下单再次聚合
         * ==========================================================
         *
         * flink实时流处理步骤
         * 流处理环境 - (检查点设置) - 获取kafka/binlog数据源 - 结构转换 - {etl处理} - 将数据写入kafka/hbase/clickhouse/redis
         * etl处理：分流/过滤/去重/状态修复/cep/双流join/维表关联/多流union/分组开窗聚合等具体业务需求,也是代码的核心所在
         * 当处理较复杂的业务逻辑时,步骤会很繁琐,可以写一段逻辑就测试一下中间结果,便于调试代码排查问题
         *
         * -- clickhouse建表语句
         * create table if not exists dirty_log (
         *   log            String,
         *   insert_time    datetime
         * ) engine = MergeTree order by (insert_time);
         */

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // flink并行度和kafka分区数保持一致
        env.setParallelism(1);

//        // 2.检查点相关设置
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

        // 3.获取kafka数据(flume)
        String topic = "ods_base_log";
        String groupId = "ods_base_log_group";
        DataStreamSource<String> kafkaStream = env.addSource(MyFlinkUtil.getKafkaSource(topic, groupId));
        // 打印测试
        kafkaStream.print(">>>");

        // 4.结构转化,jsonStr -> JSONObject
        // {"common":{...},"start":{...},"page":{...},"displays":[{},{}...],"ts":1634284695000}
//        SingleOutputStreamOperator<JSONObject> jsonStream = kafkaStream.map(JSON::parseObject);
        // nginx日志可能存在格式不规范的脏数据,可以直接过滤或者写侧输出流
        OutputTag<String> dirtyTag = new OutputTag<String>("dirty"){};
        SingleOutputStreamOperator<JSONObject> jsonStream = kafkaStream.process(new ProcessFunction<String, JSONObject>() {
            @Override
            public void processElement(String value, Context ctx, Collector<JSONObject> out) {
                try {
                    JSONObject jsonObject = JSON.parseObject(value);
                    out.collect(jsonObject);
                } catch (Exception e) {
                    ctx.output(dirtyTag, value);
                }
            }
        });
        // 将脏数据写入clickhouse,方便比对日志
        DataStream<String> dirtyStream = jsonStream.getSideOutput(dirtyTag);
        dirtyStream.addSink(ClickHouseUtil.getJdbcSink("insert into dirty_log values(?,?)"));

        // 5.新老访客校验,状态编程
        SingleOutputStreamOperator<JSONObject> fixedStream = jsonStream
                // 按照mid分组
                .keyBy(r -> r.getJSONObject("common").getString("mid"))
                // map(映射)针对每个输入元素输出1个元素
                .map(new RichMapFunction<JSONObject, JSONObject>() {
                    // 声明状态,记录设备上次访问日期,此时还获取不到RuntimeContext,必须在open方法里初始化
                    private ValueState<String> lastVisitDate;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 初始化状态变量
                        lastVisitDate = getRuntimeContext().getState(new ValueStateDescriptor<>("lastVisitDate", Types.STRING));
                    }

                    @Override
                    public JSONObject map(JSONObject value) throws Exception {
                        // 获取当前进来数据的访客状态,0是老用户 1是新用户
                        String isNew = value.getJSONObject("common").getString("is_new");
                        // 新访客才需要修复,老访客不需要
                        if ("1".equals(isNew)) {
                            // 获取当前日期和上次访问日期
                            String lastDate = lastVisitDate.value();
                            String curDate = DateUtil.parseUnixToDateTime(value.getLong("ts"));
                            // 判断是否是第一次访问
                            if (lastDate == null) {
                                // 更新状态值
                                lastVisitDate.update(curDate);
                            } else {
                                // 不是第一次就得比较最近两次访问时间
                                if (!curDate.equals(lastDate)) {
                                    // 不是同一天就是老用户,同一天的话比如今天刚注册那不管访问多少次都是新客户
                                    value.getJSONObject("common").put("is_new", "0");
                                }
                            }
                        }
                        return value;
                    }
                });

        // 6.按照日志类型分流
        // 声明侧输出流标签
        OutputTag<String> startTag = new OutputTag<String>("start"){};
        OutputTag<String> displayTag = new OutputTag<String>("display"){};
        // 页面日志放主流,启动日志和曝光日志放侧输出流
        SingleOutputStreamOperator<String> pageStream = fixedStream.process(new ProcessFunction<JSONObject, String>() {
            @Override
            public void processElement(JSONObject value, Context ctx, Collector<String> out) {
                // 判断当前进来的数据是否是启动日志
                JSONObject startLog = value.getJSONObject("start");
                if (startLog != null && startLog.size() > 0) {
                    // 是就放到启动侧输出流,toString()就是调用的toJsonString()
                    ctx.output(startTag, value.toJSONString());
                } else {
                    // 不是启动日志那就是页面日志,在主流中往下游传输
                    out.collect(value.toJSONString());
                    // 继续判断页面日志中是否包含曝光日志
                    JSONArray displayLogArr = value.getJSONArray("displays");
                    if (displayLogArr != null && displayLogArr.size() > 0) {
                        String pageId = value.getJSONObject("page").getString("page_id");
                        Long ts = value.getLong("ts");
                        // 包含就遍历数据放到曝光侧输出流
                        for (int i = 0; i < displayLogArr.size(); i++) {
                            JSONObject displayLog = displayLogArr.getJSONObject(i);
                            displayLog.put("page_id", pageId);
                            displayLog.put("ts", ts);
                            ctx.output(displayTag, displayLog.toJSONString());
                        }
                    }
                }
            }
        });

        // 获取侧输出流
        DataStream<String> startStream = pageStream.getSideOutput(startTag);
        DataStream<String> displayStream = pageStream.getSideOutput(displayTag);
        // 打印测试
        pageStream.print("page");
        startStream.print("start");
        displayStream.print("display");

        // 7.将不同流写入dwd层对应的topic
        // 为什么不在判断日志类型的时候直接写入kafka而是先分流再写入？
        // FlinkKafkaProducer<>是TwoPhaseCommitSinkFunction<>子类,已经实现了两阶段提交从而保证精准一次性,自己写的话要手动实现
        pageStream.addSink(MyFlinkUtil.getKafkaSink("dwd_page_log"));
        startStream.addSink(MyFlinkUtil.getKafkaSink("dwd_start_log"));
        displayStream.addSink(MyFlinkUtil.getKafkaSink("dwd_display_log"));

        // 启动任务
        env.execute();
    }
}
