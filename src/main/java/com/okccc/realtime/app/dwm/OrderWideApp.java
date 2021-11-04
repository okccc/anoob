package com.okccc.realtime.app.dwm;

import com.alibaba.fastjson.JSON;
import com.okccc.realtime.bean.OrderDetail;
import com.okccc.realtime.bean.OrderInfo;
import com.okccc.realtime.bean.OrderWide;
import com.okccc.realtime.utils.MyKafkaUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import java.text.SimpleDateFormat;
import java.time.Duration;

/**
 * Author: okccc
 * Date: 2021/10/28 下午5:43
 * Desc: 订单宽表
 */
public class OrderWideApp {
    public static void main(String[] args) throws Exception {
        /*
         * 事实数据(kafka)：订单表 & 订单明细表
         * 维度数据(hbase)：订单表(用户表 & 地区表)、订单明细表(商品表 -> 品牌表/分类表/SPU表)
         * 实时数仓会将订单相关的事实数据和维度数据关联整合成一张订单宽表
         * 事实数据和事实数据关联：双流join
         * 事实数据和维度数据关联：维表关联,就是在流计算中查询hbase数据,速度肯定不如双流join,外部数据源的查询往往是流计算性能瓶颈所在需要优化
         *
         * flink中的流join分两种
         * 基于时间窗口的join
         * 基于状态缓存的join
         *
         * 先往配置表table_process插入数据
         * INSERT INTO table_process VALUES('order_info','insert','kafka','dwd_order_info','id,consignee...','id',NULL);
         * INSERT INTO table_process VALUES('order_info','update','kafka','dwd_order_info_update','id,consignee...',NULL,NULL);
         * INSERT INTO table_process VALUES('order_detail','insert','kafka','dwd_order_detail','id,order_id...','id',NULL);
         */

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // flink并行度和kafka分区数保持一致
        env.setParallelism(1);

        // 2.获取kafka数据
        String orderInfoTopic = "dwd_order_info";
        String orderDetailTopic = "dwd_order_detail";
        String groupId = "order_wide_app_group";
        DataStreamSource<String> orderInfoStream = env.addSource(MyKafkaUtil.getKafkaSource(orderInfoTopic, groupId));
        DataStreamSource<String> orderDetailStream = env.addSource(MyKafkaUtil.getKafkaSource(orderDetailTopic, groupId));

        // 订单流
        KeyedStream<OrderInfo, Long> orderInfoKeyedStream = orderInfoStream
                // 将输入数据封装成订单实体类
                .map(new RichMapFunction<String, OrderInfo>() {
                    private SimpleDateFormat sdf;
                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    }
                    @Override
                    public OrderInfo map(String value) throws Exception {
                        // {"data":[{"id":"1493"...}],"database":"maxwell","table":"order_info","type":"INSERT"}
                        OrderInfo orderInfo = JSON.parseObject(value, OrderInfo.class);
                        // 生成Long类型时间戳字段后面设定水位线用
                        orderInfo.setCreate_ts(sdf.parse(orderInfo.getCreate_time()).getTime());
                        return orderInfo;
                    }
                })
                // 涉及时间判断,要提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OrderInfo>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner(new SerializableTimestampAssigner<OrderInfo>() {
                                    @Override
                                    public long extractTimestamp(OrderInfo element, long recordTimestamp) {
                                        return element.getCreate_ts();
                                    }
                                })
                )
                // 按照订单id分组
                .keyBy(OrderInfo::getId);

        // 订单明细流
        KeyedStream<OrderDetail, Long> orderDetailKeyedStream = orderDetailStream
                // 将输入数据封装成订单明细实体类
                .map(new RichMapFunction<String, OrderDetail>() {
                    private SimpleDateFormat sdf;
                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    }
                    @Override
                    public OrderDetail map(String value) throws Exception {
                        // {"data":[{"id":"1493"...}],"database":"maxwell","table":"order_detail","type":"INSERT"}
                        OrderDetail orderDetail = JSON.parseObject(value, OrderDetail.class);
                        // 生成Long类型时间戳字段后面设定水位线用
                        orderDetail.setCreate_ts(sdf.parse(orderDetail.getCreate_time()).getTime());
                        return orderDetail;
                    }
                })
                // 涉及时间判断,要提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OrderDetail>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner(new SerializableTimestampAssigner<OrderDetail>() {
                                    @Override
                                    public long extractTimestamp(OrderDetail element, long recordTimestamp) {
                                        return element.getCreate_ts();
                                    }
                                })
                )
                // 按照订单id分组
                .keyBy(OrderDetail::getOrder_id);

        // 打印测试
        // OrderInfo(id=31794, province_id=30, order_status=1001,... create_ts=1608541488000)
        orderInfoKeyedStream.print("order_info");
        // OrderDetail(id=92163, order_id=31794, sku_id=32,..., create_ts=1608541488000)
        orderDetailKeyedStream.print("order_detail");

        // 3.双流join合成订单宽表流
        SingleOutputStreamOperator<OrderWide> orderWideStream = orderInfoKeyedStream
                // 基于时间间隔的连接,查看源码发现intervalJoin底层就是调用的connect
                .intervalJoin(orderDetailKeyedStream)
                // 设置时间上下边界
                .between(Time.seconds(-5), Time.seconds(5))
                // 处理关联函数
                .process(new ProcessJoinFunction<OrderInfo, OrderDetail, OrderWide>() {
                    @Override
                    public void processElement(OrderInfo left, OrderDetail right, Context ctx, Collector<OrderWide> out) {
                        out.collect(new OrderWide(left, right));
                    }
                });

        // 打印测试
        // 订单宽表应该和订单明细表记录数相等,但是订单明细表更新100条数据时流中输出不止100条,因为订单表的insert和update都写入了
        // dwd_order_info,导致订单表与订单明细表关联时同一个订单被多次匹配,所以应该将订单表的insert和update记录放到不同的topic
        orderWideStream.print("order_wide");

        // 4.维表关联
        // TODO

        // 启动任务
        env.execute();
    }
}
