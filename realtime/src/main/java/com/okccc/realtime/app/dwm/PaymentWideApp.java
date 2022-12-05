package com.okccc.realtime.app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.okccc.realtime.bean.OrderWide;
import com.okccc.realtime.bean.PaymentInfo;
import com.okccc.realtime.bean.PaymentWide;
import com.okccc.realtime.util.DateUtil;
import com.okccc.realtime.util.MyFlinkUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * Author: okccc
 * Date: 2021/11/29 下午3:36
 * Desc: 支付宽表
 */
public class PaymentWideApp {
    public static void main(String[] args) throws Exception {
        /*
         * 支付表只到订单级别,无法查看商品级别的支付情况,可以将支付表和订单宽表关联生成支付宽表
         */

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // flink并行度和kafka分区数保持一致
        env.setParallelism(1);

        // 2.获取kafka数据
        String paymentInfoTopic = "dwd_payment_info";
        String orderWideTopic = "dwm_order_wide";
        String groupId = "payment_wide_app_group";
        DataStreamSource<String> paymentInfoStream = env.addSource(MyFlinkUtil.getKafkaSource(paymentInfoTopic, groupId));
        DataStreamSource<String> orderWideStream = env.addSource(MyFlinkUtil.getKafkaSource(orderWideTopic, groupId));

        // 3.结构转换
        KeyedStream<PaymentInfo, Long> paymentInfoKeyedStream = paymentInfoStream
                // 将支付流封装成支付实体类
                .map(new MapFunction<String, PaymentInfo>() {
                    @Override
                    public PaymentInfo map(String value) throws Exception {
                        /* {
                         *     "id":"22092",
                         *     "order_id":"34713",
                         *     "user_id":"281",
                         *     "total_amount":"19411.0",
                         *     "subject":"Redmi 10X 4G Helio G85游戏芯 4800万超清四摄 5020mAh大电量 小孔全面屏 128GB大存储",
                         *     "payment_type":"1103",
                         *     "create_time":"2021-11-24 16:37:49",
                         *     "callback_time":"2021-11-24 16:38:09",
                         *     // PaymentInfo实体类不包含下面这些字段,JSON解析时会过滤掉,缺省的字段会给null值
                         *     "trade_no":"8987655733386389522345722657825366",
                         *     "out_trade_no":"296115468596733"
                         * }
                         */
                        return JSON.parseObject(value, PaymentInfo.class);
                    }
                })
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<PaymentInfo>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner(new SerializableTimestampAssigner<PaymentInfo>() {
                                    @Override
                                    public long extractTimestamp(PaymentInfo element, long recordTimestamp) {
                                        // 将字符串转换成Long类型时间戳
                                        return DateUtil.parseDateTimeToUnix(element.getCallback_time());
                                    }
                                })
                )
                // 按照订单id分组
                .keyBy(PaymentInfo::getOrder_id);

        KeyedStream<OrderWide, Long> orderWideKeyedStream = orderWideStream
                // 将订单宽表流封装成订单宽表实体类
                .map(new MapFunction<String, OrderWide>() {
                    @Override
                    public OrderWide map(String value) throws Exception {
                        /** {
                         *     "order_id":34958,
                         *     "order_status":"1001",
                         *     "user_id":34,
                         *     "province_id":20,
                         *     "total_amount":20106,
                         *     "activity_reduce_amount":0,
                         *     "coupon_reduce_amount":0,
                         *     "original_total_amount":20097,
                         *     "feight_fee":9,
                         *     "create_time":"2021-11-30 15:42:20",
                         *     "create_date":null,
                         *     "detail_id":97291,
                         *     "sku_id":17,
                         *     "sku_num":3,
                         *     "order_price":6699,
                         *     "sku_name":"TCL 65Q10 65英寸 QLED原色量子点电视 AI声控智慧屏 超薄全面屏3+32GB 平板电视",
                         *     "split_total_amount":20097,
                         *     "split_activity_amount":null,
                         *     "split_coupon_amount":null,
                         *     "user_age":24,
                         *     "user_gender":"F",
                         *     "province_name":"青海",
                         *     "province_area_code":"630000",
                         *     "province_iso_code":"CN-63",
                         *     "province_3166_2_code":"CN-QH",
                         *     "spu_id":5,
                         *     "tm_id":4,
                         *     "category3_id":86,
                         *     "spu_name":"TCL巨幕私人影院电视 4K超高清 AI智慧屏  液晶平板电视机",
                         *     "tm_name":"TCL",
                         *     "category3_name":"平板电视"
                         * }
                         */
                        return JSON.parseObject(value, OrderWide.class);
                    }
                })
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OrderWide>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner(new SerializableTimestampAssigner<OrderWide>() {
                                    @Override
                                    public long extractTimestamp(OrderWide element, long recordTimestamp) {
                                        // 将字符串转换成Long类型时间戳
                                        return DateUtil.parseDateTimeToUnix(element.getCreate_time());
                                    }
                                })
                )
                // 按照订单id分组
                .keyBy(OrderWide::getOrder_id);

        // 打印测试
        paymentInfoKeyedStream.print("payment_info");
        orderWideKeyedStream.print("order_wide");

        // 4.双流join合成支付宽表流
        SingleOutputStreamOperator<PaymentWide> paymentWideStream = paymentInfoKeyedStream
                // 基于时间间隔的连接
                .intervalJoin(orderWideKeyedStream)
                // 设置时间上下边界,因为订单肯定在支付前面,所以是支付流往前面找订单流数据,一般下单后有效支付时间是30min
                .between(Time.seconds(-1800), Time.seconds(0))
                // 自定义处理关联函数
                .process(new ProcessJoinFunction<PaymentInfo, OrderWide, PaymentWide>() {
                    @Override
                    public void processElement(PaymentInfo left, OrderWide right, Context ctx, Collector<PaymentWide> out) throws Exception {
                        out.collect(new PaymentWide(left, right));
                    }
                });

        // 打印测试
        paymentWideStream.print("payment_wide");

        // 5.将支付宽表数据写入dwm层对应的topic
        paymentWideStream
                .map(new MapFunction<PaymentWide, String>() {
                    @Override
                    public String map(PaymentWide value) throws Exception {
                        // 将java对象转换成json字符串
                        return JSON.toJSONString(value, SerializerFeature.WriteMapNullValue);
                    }
                })
                .addSink(MyFlinkUtil.getKafkaSink("dwm_payment_wide"));

        // 启动任务
        env.execute();
    }
}
