package com.okccc.app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.PaymentWide;
import com.okccc.func.DimAsyncFunction;
import com.okccc.util.DateUtil;
import com.okccc.util.FlinkUtil;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import java.util.concurrent.TimeUnit;

/**
 * @Author: okccc
 * @Date: 2023/8/14 18:51:00
 * @Desc: 支付宽表
 */
public class DwdPaymentWide {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层下单数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_payment_detail", "dwd_payment_wide_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaSource");

        // 3.将数据格式转换成JSON
        SingleOutputStreamOperator<JSONObject> jsonStream = FlinkUtil.convertStrToJson(dataStream);

        // 4.主键id数据去重,状态编程
        SingleOutputStreamOperator<JSONObject> fixedStream = FlinkUtil.getLatestData(jsonStream, "order_id", 5000L, "crt");

        // 5.将数据格式转换成JavaBean
        SingleOutputStreamOperator<PaymentWide> paymentWideStream = fixedStream.map(new MapFunction<JSONObject, PaymentWide>() {
            @Override
            public PaymentWide map(JSONObject value) {
                // {"order_id":"31924","user_id":"1100","sku_id":"29","province_id":"29","split_total_amount":null,"callback_time":"2023-08-23 11:39:31"...}
                // 先将dwd_order_detail包含的字段添加进来,剩下的维度关联补充
                return PaymentWide.builder()
                        .orderId(value.getString("id"))
                        .userId(value.getString("user_id"))
                        .skuId(value.getString("sku_id"))
                        .skuName(value.getString("sku_name"))
                        .provinceId(value.getString("province_id"))
                        .totalAmount(value.getDouble("split_total_amount"))
                        .callbackTime(value.getString("callback_time"))
                        .build();
            }
        });

        // 6.维表关联
        // 用户维度
        SingleOutputStreamOperator<PaymentWide> paymentWideWithUser = AsyncDataStream.unorderedWait(
                paymentWideStream,
                new DimAsyncFunction<PaymentWide>("DIM_USER_INFO") {
                    @Override
                    public String getKey(PaymentWide paymentWide) {
                        return paymentWide.getUserId();
                    }

                    @Override
                    public void join(PaymentWide paymentWide, JSONObject dimInfo) {
                        paymentWide.setUserName(dimInfo.getString("NAME"));
                        paymentWide.setGender(dimInfo.getString("GENDER"));
                        String birthday = dimInfo.getString("BIRTHDAY");
                        long diff = System.currentTimeMillis() - DateUtil.parseDate(birthday).getTime();
                        long age = diff / (365 * 24 * 3600 * 1000L);
                        paymentWide.setAge((int) age);
                    }
                },
                10, TimeUnit.SECONDS
        );

        // 地区维度
        SingleOutputStreamOperator<PaymentWide> paymentWideWithArea = AsyncDataStream.unorderedWait(
                paymentWideWithUser,
                new DimAsyncFunction<PaymentWide>("DIM_BASE_PROVINCE") {
                    @Override
                    public String getKey(PaymentWide paymentWide) {
                        return paymentWide.getProvinceId();
                    }

                    @Override
                    public void join(PaymentWide paymentWide, JSONObject dimInfo) {
                        paymentWide.setProvinceName(dimInfo.getString("NAME"));
                        paymentWide.setAreaCode(dimInfo.getString("AREA_CODE"));
                        paymentWide.setIsoCode(dimInfo.getString("ISO_CODE"));
                    }
                },
                10, TimeUnit.SECONDS
        );

        // sku维度
        SingleOutputStreamOperator<PaymentWide> paymentWideWithSku = AsyncDataStream.unorderedWait(
                paymentWideWithArea,
                new DimAsyncFunction<PaymentWide>("DIM_SKU_INFO") {
                    @Override
                    public String getKey(PaymentWide paymentWide) {
                        return paymentWide.getSkuId();
                    }

                    @Override
                    public void join(PaymentWide paymentWide, JSONObject dimInfo) {
                        paymentWide.setSpuId(dimInfo.getString("SPU_ID"));
                        paymentWide.setTmId(dimInfo.getString("TM_ID"));
                        paymentWide.setCategory3Id(dimInfo.getString("CATEGORY3_ID"));
                    }
                },
                10, TimeUnit.SECONDS
        );

        // spu维度
        SingleOutputStreamOperator<PaymentWide> paymentWideWithSpu = AsyncDataStream.unorderedWait(
                paymentWideWithSku,
                new DimAsyncFunction<PaymentWide>("DIM_SPU_INFO") {
                    @Override
                    public String getKey(PaymentWide paymentWide) {
                        return paymentWide.getSpuId();
                    }

                    @Override
                    public void join(PaymentWide paymentWide, JSONObject dimInfo) {
                        paymentWide.setSpuName(dimInfo.getString("SPU_NAME"));
                    }
                },
                10, TimeUnit.SECONDS
        );

        // 品牌维度
        SingleOutputStreamOperator<PaymentWide> paymentWideWithTm = AsyncDataStream.unorderedWait(
                paymentWideWithSpu,
                new DimAsyncFunction<PaymentWide>("DIM_BASE_TRADEMARK") {
                    @Override
                    public String getKey(PaymentWide paymentWide) {
                        return paymentWide.getTmId();
                    }

                    @Override
                    public void join(PaymentWide paymentWide, JSONObject dimInfo) {
                        paymentWide.setTmName(dimInfo.getString("TM_NAME"));
                    }
                },
                10, TimeUnit.SECONDS
        );

        // 类别维度
        SingleOutputStreamOperator<PaymentWide> paymentWideWithCategory3 = AsyncDataStream.unorderedWait(
                paymentWideWithTm,
                new DimAsyncFunction<PaymentWide>("DIM_BASE_CATEGORY3") {
                    @Override
                    public String getKey(PaymentWide paymentWide) {
                        return paymentWide.getCategory3Id();
                    }

                    @Override
                    public void join(PaymentWide paymentWide, JSONObject dimInfo) {
                        paymentWide.setCategory3Name(dimInfo.getString("NAME"));
                    }
                },
                10, TimeUnit.SECONDS
        );

        paymentWideWithCategory3.print("paymentWide");

        // 7.将订单宽表写入kafka
        paymentWideWithCategory3
                .map(JSON::toJSONString)
                .sinkTo(FlinkUtil.getKafkaSink("dwd_payment_wide", "pw_"));

        // 8.启动任务
        env.execute("DwdPaymentWide");
    }
}
