package com.okccc.app.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.OrderWide;
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
 * @Date: 2023/8/14 18:50:26
 * @Desc: 订单宽表(DataStream - KafkaSource - Side Cache & Async IO - KafkaSink)
 *
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/asyncio/
 *
 * 事实数据(kafka)：订单表 & 订单明细表
 * 维度数据(hbase)：订单表(用户表 & 地区表)、订单明细表(商品表 -> SPU表/品牌表/类别表)
 * 实时数仓会将订单相关的事实数据和维度数据关联整合成一张订单宽表
 * 事实数据和事实数据关联：双流join(推荐使用flink-sql)
 * 事实数据和维度数据关联：维表关联,在流计算中根据userId/areaId/skuId等关联查询维度信息补充到事实表
 *
 * 与外部系统的交互是实时流计算的主要性能瓶颈,查询mysql/mongo时先看下查询列是否加索引很影响效率
 * When interacting with external systems (for example when enriching stream events with data stored in a database),
 * one needs to take care that communication delay with the external system does not dominate the streaming application’s total work.
 *
 * map算子是同步执行的,一次只能处理一条数据,IO阻塞占据了算子执行的大部分时间
 * Naively accessing data in the external database, for example in a MapFunction, typically means synchronous
 * interaction: A request is sent to the database and the MapFunction waits until the response has been received.
 * In many cases, this waiting makes up the vast majority of the function’s time.
 *
 * 增加map算子并行度可以提高吞吐量,但是会消耗更多资源,当然数据量巨大时,假设map速度10条/s,吞吐量1000条/s,再怎么优化也不够,必须加机器
 * Improving throughput by just scaling the MapFunction to a very high parallelism is in some cases possible as well,
 * but usually comes at a very high resource cost: Having many more parallel MapFunction instances means more tasks, threads,
 * Flink-internal network connections, network connections to the database, buffers, and general internal bookkeeping overhead
 *
 * 异步交互是指单并行度的算子可以并发处理多个请求,这样一来等待的时间就被多个请求均摊掉了,可以大幅提高流处理的吞吐量
 * Asynchronous interaction with the database means that a single parallel function instance can handle many requests
 * concurrently and receive the responses concurrently. That way, the waiting time can be overlaid with sending other
 * requests and receiving responses. At the very least, the waiting time is amortized over multiple requests.
 * This leads in most cased to much higher streaming throughput.
 *
 * 方案1：使用数据库本身提供的异步请求API,但是HBase的异步API肯定不包含DimUtil中自定义的旁路缓存功能,所以没法直接使用
 * As illustrated in the section above, implementing proper asynchronous I/O to a database (or key/value store)
 * requires a client to that database that supports asynchronous requests. Many popular databases offer such a client.
 *
 * 方案2：使用线程池创建多个客户端处理同步调用,比正规的异步API性能稍差,但是更符合我们当前维度关联跨系统hbase/redis查询的实际需求
 * In the absence of such a client, one can try and turn a synchronous client into a limited concurrent client by
 * creating multiple clients and handling the synchronous calls with a thread pool. However, this approach is usually
 * less efficient than a proper asynchronous client.
 *
 * Async I/O API
 * three parts are needed to implement a stream transformation with asynchronous I/O against the database:
 * An implementation of AsyncFunction that dispatches the requests
 * A callback that takes the result of the operation and hands it to the ResultFuture
 * Applying the async I/O operation on a DataStream as a transformation with or without retry
 *
 * 同步异步是代码执行层面
 * 同步：顺序执行,调用方必须等待当前调用返回结果,才能继续执行后面操作,会形成IO阻塞
 * 异步：彼此独立,调用方不用等待当前调用返回结果,可以继续执行后面操作,不会形成IO阻塞,异步可通过多线程实现
 * 我们平常写的基本上都是同步代码,必须上一行执行完才能继续下一行,异步代码往往属于调优部分
 */
public class DwdOrderWide {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 2.读取dwd层下单数据
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("dwd_order_detail", "dwd_order_wide_g");
        DataStreamSource<String> dataStream = env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaSource");

        // 3.将数据格式转换成JSON
        SingleOutputStreamOperator<JSONObject> jsonStream = FlinkUtil.convertStrToJson(dataStream);

        // 4.主键id数据去重,状态编程
        SingleOutputStreamOperator<JSONObject> fixedStream = FlinkUtil.getEarliestData(jsonStream, "id", 5);

        // 5.将数据格式转换成JavaBean
        SingleOutputStreamOperator<OrderWide> orderWideStream = fixedStream.map(new MapFunction<JSONObject, OrderWide>() {
            @Override
            public OrderWide map(JSONObject value) {
                // {"order_id":"30460","sku_id":"10","user_id":"3954","province_id":"5","create_time":"2023-08-01 10:41:05","split_total_amount":5999...}
                // 先将dwd_order_detail包含的字段添加进来,剩下的维度关联补充
                return OrderWide.builder()
                        .orderId(value.getString("order_id"))
                        .skuId(value.getString("sku_id"))
                        .skuName(value.getString("sku_name"))
                        .userId(value.getString("user_id"))
                        .provinceId(value.getString("province_id"))
                        .totalAmount(value.getDouble("split_total_amount"))
                        .createTime(value.getString("create_time"))
                        .build();
            }
        });

        // 6.维表关联
        // 常规方式,使用map算子同步执行,性能较差
//        SingleOutputStreamOperator<OrderWide> orderWideStreamWithSku = orderWideStream.map(new MapFunction<OrderWide, OrderWide>() {
//            @Override
//            public OrderWide map(OrderWide orderWide) {
//                // 1.获取维度关联的key
//                String userId = orderWide.getUserId();
//                // 2.查询维度数据
//                JSONObject dimInfo = DimUtil.getDimInfoWithCache("DIM_USER_INFO", userId);
//                // 3.补充到JavaBean
//                orderWide.setGender(dimInfo.getString("GENDER"));
//                return orderWide;
//            }
//        });

        // 将异步IO应用于DataStream,作为一次转换操作,功能和map算子是一样的
        // orderedWait保证输入输出顺序一致,a先请求b后请求,即使b先响应也要等a先输出,略微影响效率
        // unorderedWait可能会数据乱序但是不用等,谁先响应就处理谁效率更高,不影响业务的情况下尽量用无序
        // 用户维度
        SingleOutputStreamOperator<OrderWide> orderWideWithUser = AsyncDataStream.unorderedWait(
                orderWideStream,
                // 此部分是通用功能,可以抽取出来
//                new AsyncFunction<OrderWide, OrderWide>() {
//                    @Override
//                    public void asyncInvoke(OrderWide orderWide, ResultFuture<OrderWide> resultFuture) throws Exception {
//                        // 开启线程，发送异步请求
//                        ThreadPoolExecutor poolExecutor = ThreadPoolUtil.getInstance();
//                        poolExecutor.submit(new Runnable() {
//                            @Override
//                            public void run() {
//                                // 1.获取维度关联的key
//                                String userId = orderWide.getUserId();
//                                // 2.查询维度数据
//                                JSONObject userInfo;
//                                try {
//                                    userInfo = DimUtil.getDimInfoWithCache("DIM_USER_INFO", userId);
//                                    // 3.补充到JavaBean
//                                    orderWide.setGender(userInfo.getString("GENDER"));
//                                } catch (Exception e) {
//                                    e.printStackTrace();
//                                }
//                                // 获取与外部系统的交互结果,并发送给ResultFuture的回调函数
//                                resultFuture.complete(Collections.singleton(orderWide));
//                            }
//                        });
//                    }
//                },
                new DimAsyncFunction<OrderWide>("DIM_USER_INFO") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getUserId();
                    }

                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setUserName(dimInfo.getString("NAME"));
                        orderWide.setGender(dimInfo.getString("GENDER"));
                        String birthday = dimInfo.getString("BIRTHDAY");
                        long diff = System.currentTimeMillis() - DateUtil.parseDate(birthday).getTime();
                        long age = diff / (365 * 24 * 3600 * 1000L);
                        orderWide.setAge((int) age);
                    }
                },
                30, TimeUnit.SECONDS  // 维度查询hbase会依赖zk,所以异步操作的超时时间要大于zk的默认超时时间(20秒)
        );

        // 地区维度
        SingleOutputStreamOperator<OrderWide> orderWideWithArea = AsyncDataStream.unorderedWait(
                orderWideWithUser,
                new DimAsyncFunction<OrderWide>("DIM_BASE_PROVINCE") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getProvinceId();
                    }

                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setProvinceName(dimInfo.getString("NAME"));
                        orderWide.setAreaCode(dimInfo.getString("AREA_CODE"));
                        orderWide.setIsoCode(dimInfo.getString("ISO_CODE"));
                    }
                },
                30, TimeUnit.SECONDS
        );

        // sku维度
        SingleOutputStreamOperator<OrderWide> orderWideWithSku = AsyncDataStream.unorderedWait(
                orderWideWithArea,
                new DimAsyncFunction<OrderWide>("DIM_SKU_INFO") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getSkuId();
                    }

                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setSpuId(dimInfo.getString("SPU_ID"));
                        orderWide.setTmId(dimInfo.getString("TM_ID"));
                        orderWide.setCategory3Id(dimInfo.getString("CATEGORY3_ID"));
                    }
                },
                30, TimeUnit.SECONDS
        );

        // spu维度
        SingleOutputStreamOperator<OrderWide> orderWideWithSpu = AsyncDataStream.unorderedWait(
                orderWideWithSku,
                new DimAsyncFunction<OrderWide>("DIM_SPU_INFO") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getSpuId();
                    }

                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setSpuName(dimInfo.getString("SPU_NAME"));
                    }
                },
                30, TimeUnit.SECONDS
        );

        // 品牌维度
        SingleOutputStreamOperator<OrderWide> orderWideWithTm = AsyncDataStream.unorderedWait(
                orderWideWithSpu,
                new DimAsyncFunction<OrderWide>("DIM_BASE_TRADEMARK") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getTmId();
                    }

                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setTmName(dimInfo.getString("TM_NAME"));
                    }
                },
                30, TimeUnit.SECONDS
        );

        // 类别维度
        SingleOutputStreamOperator<OrderWide> orderWideWithCategory3 = AsyncDataStream.unorderedWait(
                orderWideWithTm,
                new DimAsyncFunction<OrderWide>("DIM_BASE_CATEGORY3") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getCategory3Id();
                    }

                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setCategory3Name(dimInfo.getString("NAME"));
                    }
                },
                30, TimeUnit.SECONDS
        );

        orderWideWithCategory3.print("orderWide");

        // 7.将订单宽表写入kafka
        orderWideWithCategory3
                .map(JSON::toJSONString)
                .sinkTo(FlinkUtil.getKafkaSink("dwd_order_wide", "ow_"));

        // 8.启动任务
        env.execute("DwdOrderWide");
    }
}
