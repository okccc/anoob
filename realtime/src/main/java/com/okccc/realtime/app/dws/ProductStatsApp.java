package com.okccc.realtime.app.dws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.app.func.DimAsyncFunction;
import com.okccc.realtime.bean.OrderWide;
import com.okccc.realtime.bean.PaymentWide;
import com.okccc.realtime.bean.ProductStats;
import com.okccc.realtime.common.MyConstant;
import com.okccc.realtime.util.ClickHouseUtil;
import com.okccc.realtime.util.DateUtil;
import com.okccc.realtime.util.MyFlinkUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * @Author: okccc
 * @Date: 2021/12/24 11:28 上午
 * @Desc: 商品主题宽表
 */
public class ProductStatsApp {
    public static void main(String[] args) throws Exception {
        /*
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
         *
         * 维度数据：商品
         * 度量数据：点击、曝光、收藏、购物车、下单、支付、退款、评论
         * 商品主题可以按照sku_id分组,因为会有很多人同时操作该商品,数据量很可观可以达到聚合效果
         *
         * -- clickhouse建表语句
         * create table if not exists product_stats (
         *   stt               DateTime,
         *   edt               DateTime,
         *   sku_id            UInt64,
         *   sku_name          String,
         *   sku_price         Decimal64(2),
         *   spu_id            UInt64,
         *   spu_name          String,
         *   tm_id             UInt64,
         *   tm_name           String,
         *   category3_id      UInt64,
         *   category3_name    String,
         *   click_ct          UInt64,
         *   display_ct        UInt64,
         *   favor_ct          UInt64,
         *   cart_ct           UInt64,
         *   order_ct          UInt64,
         *   order_sku_num     UInt64,
         *   order_amount      Decimal64(2),
         *   paid_order_ct     UInt64,
         *   payment_amount    Decimal64(2),
         *   refund_order_ct   UInt64,
         *   refund_amount     Decimal64(2),
         *   comment_ct        UInt64,
         *   good_comment_ct   UInt64,
         *   ts                UInt64
         *  ) engine = ReplacingMergeTree(ts) partition by toYYYYMMDD(stt) order by (stt,edt,sku_id);
         */

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // flink并行度和kafka分区数保持一致
        env.setParallelism(1);

        // 2.获取kafka数据
        String pageViewTopic = "dwd_page_log";
        String favorInfoTopic = "dwd_favor_info";
        String cartInfoTopic = "dwd_cart_info";
        String orderWideTopic = "dwm_order_wide";
        String paymentWideTopic = "dwm_payment_wide";
        String orderRefundInfoTopic = "dwd_order_refund_info";
        String commentInfoTopic = "dwd_comment_info";
        String groupId = "product_stats_app_group";
        DataStreamSource<String> pageViewStream = env.addSource(MyFlinkUtil.getKafkaSource(pageViewTopic, groupId));
        DataStreamSource<String> favorInfoStream = env.addSource(MyFlinkUtil.getKafkaSource(favorInfoTopic, groupId));
        DataStreamSource<String> cartInfoStream = env.addSource(MyFlinkUtil.getKafkaSource(cartInfoTopic, groupId));
        DataStreamSource<String> orderWideStream = env.addSource(MyFlinkUtil.getKafkaSource(orderWideTopic, groupId));
        DataStreamSource<String> paymentWideStream = env.addSource(MyFlinkUtil.getKafkaSource(paymentWideTopic, groupId));
        DataStreamSource<String> refundInfoStream = env.addSource(MyFlinkUtil.getKafkaSource(orderRefundInfoTopic, groupId));
        DataStreamSource<String> commentInfoStream = env.addSource(MyFlinkUtil.getKafkaSource(commentInfoTopic, groupId));

        // 3.结构转换
        SingleOutputStreamOperator<ProductStats> clickAndDisplayStatsStream = pageViewStream
                // 需要从dwd_page_log拆分出点击和曝光两个流,而map算子只能输出1个结果,所以要用底层的process
                .process(new ProcessFunction<String, ProductStats>() {
                    @Override
                    public void processElement(String value, Context ctx, Collector<ProductStats> out) {
                        // {"common":{"ar":"","ch":"","vc":""...},"page":{...},"displays":[{},{}...],"ts":1639123347000}
                        JSONObject jsonObject = JSON.parseObject(value);
                        JSONObject page = jsonObject.getJSONObject("page");
                        Long ts = jsonObject.getLong("ts");
                        // 判断是否是点击行为
                        String pageId = page.getString("page_id");
                        if ("good_detail".equals(pageId)) {
                            // 如果当前页面是商品详情页,说明是点击行为
                            Long skuId = page.getLong("item");
                            ProductStats productStats = ProductStats.builder().sku_id(skuId).click_ct(1L).ts(ts).build();
                            out.collect(productStats);
                        }
                        // 判断是否是曝光行为
                        JSONArray displaysArr = jsonObject.getJSONArray("displays");
                        if (displaysArr != null && displaysArr.size() > 0) {
                            // 不为空就遍历输出
                            for (int i = 0; i < displaysArr.size(); i++) {
                                JSONObject display = displaysArr.getJSONObject(i);
                                // 继续判断曝光的是不是商品
                                String itemType = display.getString("item_type");
                                if ("sku_id".equals(itemType)) {
                                    Long skuId = display.getLong("item");
                                    ProductStats productStats = ProductStats.builder().sku_id(skuId).display_ct(1L).ts(ts).build();
                                    out.collect(productStats);
                                }
                            }
                        }
                    }
                });

        SingleOutputStreamOperator<ProductStats> favorInfoStatsStream = favorInfoStream
                // 将favor流数据封装成商品主题实体类
                .map(new MapFunction<String, ProductStats>() {
                    @Override
                    public ProductStats map(String value) throws Exception {
                        // {"create_time":"2021-12-29 14:37:34","user_id":"18","sku_id":"24","id":"43","is_cancel":"0"}
                        JSONObject jsonObject = JSON.parseObject(value);
                        Long skuId = jsonObject.getLong("sku_id");
                        Long createTime = DateUtil.parseDateTimeToUnix(jsonObject.getString("create_time"));
                        return ProductStats.builder().sku_id(skuId).favor_ct(1L).ts(createTime).build();
                    }
                });

        SingleOutputStreamOperator<ProductStats> cartInfoStatsStream = cartInfoStream
                // 将cart流数据封装成商品主题实体类
                .map(new MapFunction<String, ProductStats>() {
                    @Override
                    public ProductStats map(String value) throws Exception {
                        /*{
                         *     "is_ordered":"0",
                         *     "cart_price":"129.0",
                         *     "sku_num":"2",
                         *     "create_time":"2021-12-29 14:37:34",
                         *     "sku_id":"28",
                         *     "source_type":"2402",
                         *     "user_id":"6",
                         *     "sku_name":"iphone6s",
                         *     "id":"774937",
                         *     "source_id":"87"
                         * }
                         */
                        JSONObject jsonObject = JSON.parseObject(value);
                        Long skuId = jsonObject.getLong("sku_id");
                        Long createTime = DateUtil.parseDateTimeToUnix(jsonObject.getString("create_time"));
                        return ProductStats.builder().sku_id(skuId).cart_ct(1L).ts(createTime).build();
                    }
                });

        SingleOutputStreamOperator<ProductStats> refundInfoStatsStream = refundInfoStream
                // 将refund流数据封装成商品主题实体类
                .map(new MapFunction<String, ProductStats>() {
                    @Override
                    public ProductStats map(String value) throws Exception {
                        /* {
                         *     "refund_type":"1502",
                         *     "create_time":"2021-12-29 14:43:58",
                         *     "user_id":"3498",
                         *     "refund_num":"3",
                         *     "refund_reason_type":"1301",
                         *     "refund_amount":"20097.0",
                         *     "sku_id":"17",
                         *     "id":"6487",
                         *     "order_id":"35662",
                         *     "refund_reason_txt":"退款原因具体：3124169519"
                         * }
                         */
                        JSONObject jsonObject = JSON.parseObject(value);
                        Long skuId = jsonObject.getLong("sku_id");
                        Long createTime = DateUtil.parseDateTimeToUnix(jsonObject.getString("create_time"));
                        // 多条退款记录可能对应同一个订单的商品,所以不能直接给refund_order_ct + 1,而是要对order_id去重
                        HashSet<Long> hashSet = new HashSet<>();
                        hashSet.add(jsonObject.getLong("order_id"));
                        BigDecimal refundAmount = jsonObject.getBigDecimal("refund_amount");
                        return ProductStats.builder().sku_id(skuId).refundOrderIdSet(hashSet).refund_amount(refundAmount).ts(createTime).build();
                    }
                });

        SingleOutputStreamOperator<ProductStats> orderWideStatsStream = orderWideStream
                // 将orderWide流数据封装成商品主题实体类
                .map(new MapFunction<String, ProductStats>() {
                    @Override
                    public ProductStats map(String value) throws Exception {
                        // 订单和支付已经存在宽表实体类
                        OrderWide orderWide = JSON.parseObject(value, OrderWide.class);
                        return ProductStats.builder()
                                .sku_id(orderWide.getSku_id())
                                .order_sku_num(orderWide.getSku_num())
                                .order_amount(orderWide.getSplit_total_amount())
                                .orderIdSet(new HashSet<>(Collections.singleton(orderWide.getOrder_id())))
                                .ts(DateUtil.parseDateTimeToUnix(orderWide.getCreate_time()))
                                .build();
                    }
                });

        SingleOutputStreamOperator<ProductStats> paymentWideStatsStream = paymentWideStream
                // 将paymentWide流数据封装成商品主题实体类
                .map(new MapFunction<String, ProductStats>() {
                    @Override
                    public ProductStats map(String value) throws Exception {
                        PaymentWide paymentWide = JSON.parseObject(value, PaymentWide.class);
                        return ProductStats.builder()
                                .sku_id(paymentWide.getSku_id())
                                .payment_amount(paymentWide.getSplit_total_amount())
                                .paidOrderIdSet(new HashSet<>(Collections.singleton(paymentWide.getOrder_id())))
                                .ts(DateUtil.parseDateTimeToUnix(paymentWide.getCallback_time()))
                                .build();
                    }
                });

        SingleOutputStreamOperator<ProductStats> commentInfoStatsStream = commentInfoStream
                // 将comment流数据封装成商品主题实体类
                .map(new MapFunction<String, ProductStats>() {
                    @Override
                    public ProductStats map(String value) throws Exception {
                        /*{
                         *     "create_time":"2021-12-29 14:43:58",
                         *     "user_id":"3605",
                         *     "appraise":"1204",
                         *     "comment_txt":"评论内容：92882847143823855652298236869189893242413788668748",
                         *     "sku_id":"22",
                         *     "id":"1476081541179265026",
                         *     "spu_id":"7",
                         *     "order_id":"35654"
                         * }
                         */
                        JSONObject jsonObject = JSON.parseObject(value);
                        Long skuId = jsonObject.getLong("sku_id");
                        Long createTime = DateUtil.parseDateTimeToUnix(jsonObject.getString("create_time"));
                        // 类似好评/中评/差评这种常量一般会在数据库里有一个码表
                        String appraise = jsonObject.getString("appraise");
                        long gct = MyConstant.APPRAISE_GOOD.equals(appraise) ? 1L : 0L;
                        return ProductStats.builder().sku_id(skuId).comment_ct(1L).good_comment_ct(gct).ts(createTime).build();
                    }
                });

        // 4.合并多条流
        DataStream<ProductStats> unionStream = clickAndDisplayStatsStream.union(favorInfoStatsStream, cartInfoStatsStream,
                orderWideStatsStream, paymentWideStatsStream, refundInfoStatsStream, commentInfoStatsStream);
        // 打印测试
//        unionStream.print("union");  // union> ProductStats(stt=null, edt=null, sku_id=9, sku_name=null, sku_price=null, spu_id=null, spu_name=null, tm_id=null, tm_name=null, category3_id=null, category3_name=null, click_ct=0, display_ct=1, favor_ct=0, cart_ct=0, order_ct=0, order_sku_num=0, order_amount=0, paid_order_ct=0, payment_amount=0, refund_order_ct=0, refund_amount=0, comment_ct=0, good_comment_ct=0, orderIdSet=[], paidOrderIdSet=[], refundOrderIdSet=[], ts=1640768946000)

        // 5.分组/开窗/聚合
        SingleOutputStreamOperator<ProductStats> productStatsStream = unionStream
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<ProductStats>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                                .withTimestampAssigner(new SerializableTimestampAssigner<ProductStats>() {
                                    @Override
                                    public long extractTimestamp(ProductStats element, long recordTimestamp) {
                                        return element.getTs();
                                    }
                                })
                )
                // 分组
                .keyBy(new KeySelector<ProductStats, Long>() {
                    @Override
                    public Long getKey(ProductStats value) throws Exception {
                        return value.getSku_id();
                    }
                })
                // 开窗
                .window(TumblingEventTimeWindows.of(Time.seconds(10)))
                // 聚合
                .reduce(
                        // 先增量聚合
                        new ReduceFunction<ProductStats>() {
                            @Override
                            public ProductStats reduce(ProductStats value1, ProductStats value2) {
                                // 按照维度分组后,将度量值进行两两相加
                                // 点击数
                                value1.setClick_ct(value1.getClick_ct() + value2.getClick_ct());
                                // 曝光数
                                value1.setDisplay_ct(value1.getDisplay_ct() + value2.getDisplay_ct());
                                // 收藏数
                                value1.setFavor_ct(value1.getFavor_ct() + value2.getFavor_ct());
                                // 购物车数
                                value1.setCart_ct(value1.getCart_ct() + value2.getCart_ct());
                                // 订单数,两个Set集合相加进一步去重,BigDecimal类型不能直接相加得用add方法
                                value1.getOrderIdSet().addAll(value2.getOrderIdSet());
                                value1.setOrder_ct((long) value1.getOrderIdSet().size());
                                value1.setOrder_sku_num(value1.getOrder_sku_num() + value2.getOrder_sku_num());
                                value1.setOrder_amount(value1.getOrder_amount().add(value2.getOrder_amount()));
                                // 支付数
                                value1.getPaidOrderIdSet().addAll(value2.getPaidOrderIdSet());
                                value1.setPaid_order_ct((long) value1.getPaidOrderIdSet().size());
                                value1.setPayment_amount(value1.getPayment_amount().add(value2.getPayment_amount()));
                                // 退款数
                                value1.getRefundOrderIdSet().addAll(value2.getRefundOrderIdSet());
                                value1.setRefund_order_ct((long) value1.getRefundOrderIdSet().size());
                                value1.setRefund_amount(value1.getRefund_amount().add(value2.getRefund_amount()));
                                // 评论数
                                value1.setComment_ct(value1.getComment_ct() + value2.getComment_ct());
                                value1.setGood_comment_ct(value1.getGood_comment_ct() + value2.getGood_comment_ct());
                                return value1;
                            }
                        },
                        // 再全窗口处理
                        new ProcessWindowFunction<ProductStats, ProductStats, Long, TimeWindow>() {
                            @Override
                            public void process(Long aLong, Context context, Iterable<ProductStats> elements, Collector<ProductStats> out) {
                                // 获取窗口信息
                                long windowStart = context.window().getStart();
                                long windowEnd = context.window().getEnd();
                                // 遍历迭代器
                                for (ProductStats productStats : elements) {
                                    // 补全窗口区间及统计时间
                                    productStats.setStt(DateUtil.parseUnixToDateTime(windowStart));
                                    productStats.setEdt(DateUtil.parseUnixToDateTime(windowEnd));
                                    productStats.setTs(System.currentTimeMillis());
                                    // 收集结果往下游发送
                                    out.collect(productStats);
                                }
                            }
                        }
                );
        // 打印测试
        productStatsStream.print("ProductStats");

        // 6.补全商品维度信息
        // sku维度
        SingleOutputStreamOperator<ProductStats> productStatsWithSkuStream = AsyncDataStream.unorderedWait(
                productStatsStream,
                new DimAsyncFunction<ProductStats>("dim_sku_info") {
                    @Override
                    public String getKey(ProductStats productStats) {
                        return productStats.getSku_id().toString();
                    }
                    @Override
                    public void join(ProductStats productStats, JSONObject dimInfo) throws ParseException {
                        productStats.setSku_name(dimInfo.getString("SKU_NAME"));
                        productStats.setSku_price(dimInfo.getBigDecimal("PRICE"));
                        productStats.setSpu_id(dimInfo.getLong("SPU_ID"));
                        productStats.setTm_id(dimInfo.getLong("TM_ID"));
                        productStats.setCategory3_id(dimInfo.getLong("CATEGORY3_ID"));
                    }
                },
                60, TimeUnit.SECONDS);

        // spu维度
        SingleOutputStreamOperator<ProductStats> productStatsWithSpuStream = AsyncDataStream.unorderedWait(
                productStatsWithSkuStream,
                new DimAsyncFunction<ProductStats>("dim_spu_info") {
                    @Override
                    public String getKey(ProductStats productStats) {
                        return productStats.getSpu_id().toString();
                    }
                    @Override
                    public void join(ProductStats productStats, JSONObject dimInfo) throws ParseException {
                        productStats.setSpu_name(dimInfo.getString("SPU_NAME"));
                    }
                },
                60, TimeUnit.SECONDS);

        // 品牌维度
        SingleOutputStreamOperator<ProductStats> productStatsWithTmStream = AsyncDataStream.unorderedWait(
                productStatsWithSpuStream,
                new DimAsyncFunction<ProductStats>("dim_base_trademark") {
                    @Override
                    public String getKey(ProductStats productStats) {
                        return productStats.getTm_id().toString();
                    }
                    @Override
                    public void join(ProductStats productStats, JSONObject dimInfo) throws ParseException {
                        productStats.setTm_name(dimInfo.getString("TM_NAME"));
                    }
                },
                60, TimeUnit.SECONDS);

        // 类别维度
        SingleOutputStreamOperator<ProductStats> productStatsWithCategoryStream = AsyncDataStream.unorderedWait(
                productStatsWithTmStream,
                new DimAsyncFunction<ProductStats>("dim_base_category3") {
                    @Override
                    public String getKey(ProductStats productStats) {
                        return productStats.getCategory3_id().toString();
                    }
                    @Override
                    public void join(ProductStats productStats, JSONObject dimInfo) throws ParseException {
                        productStats.setCategory3_name(dimInfo.getString("NAME"));
                    }
                },
                60, TimeUnit.SECONDS);
        // 打印测试
        productStatsWithCategoryStream.print("ProductStatsWithDim");

        // 7.将聚合数据写入clickhouse
        productStatsWithCategoryStream.addSink(
                ClickHouseUtil.getJdbcSinkBySchema("insert into product_stats values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"));

        // 启动任务
        env.execute();
    }
}