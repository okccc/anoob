package com.okccc.realtime.app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.okccc.realtime.app.func.DimAsyncFunction;
import com.okccc.realtime.bean.OrderDetail;
import com.okccc.realtime.bean.OrderInfo;
import com.okccc.realtime.bean.OrderWide;
import com.okccc.realtime.util.DateUtil;
import com.okccc.realtime.util.MyFlinkUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * @Author: okccc
 * @Date: 2021/10/28 下午5:43
 * @Desc: 订单宽表
 */
public class OrderWideApp {
    public static void main(String[] args) throws Exception {
        /*
         * 事实数据(kafka)：订单表 & 订单明细表
         * 维度数据(hbase)：订单表(用户表 & 地区表)、订单明细表(商品表 -> 品牌表/类别表/SPU表)
         * 实时数仓会将订单相关的事实数据和维度数据关联整合成一张订单宽表
         * 事实数据和事实数据关联：双流join
         * 事实数据和维度数据关联：维表关联,就是在流计算中查询hbase数据,用维度表补全事实表中的字段,比如userId关联用户维度,skuId关联商品维度
         * areaId关联地区维度,外部数据源的查询往往是流计算性能瓶颈所在,可以通过旁路缓存和异步查询进行优化
         *
         * 旁路缓存：先查询redis缓存,命中直接返回,没有命中再去查询mysql/hbase数据库同时将结果写入缓存
         * 注意事项：1.缓存要设置过期时间,防止冷数据常驻缓存浪费资源 2.数据库数据更新时要及时清除失效缓存
         * redis面试题：数据更新,先更新数据库还是先更新缓存？
         * a.先更新数据库,再删除缓存(常用)：缓存刚好失效,A查询缓存未命中去数据库查询旧值,B更新数据库并让缓存失效,A将旧数据写入缓存,导致脏数据
         * 读操作必须先于写操作进入数据库,又晚于写操作更新缓存才会导致该情况发生,然而实际上数据库的写操作比读操作慢很多还得锁表,所以发生概率极低
         * b.先删除缓存,再更新数据库：A删除缓存,B查询缓存未命中去数据库查询旧值并写入缓存,A更新数据库,导致脏数据
         * c.先更新数据库,再更新缓存：A更新数据为V1,B更新数据为V2,B更新缓存为V2,A更新缓存为V1,A先操作但是网络延迟B先更新了缓存,导致脏数据
         * d.先更新缓存,再更新数据库：如果缓存更新成功但数据库异常导致回滚,而缓存是无法回滚的,导致数据不一致(不考虑)
         *
         * 异步IO：flink默认同步,比如MapFunction算子内部实时计算影响不大,但是向外部数据源hbase请求数据会IO阻塞,等待响应后再发送下一个请求
         * 等待请求会耗费大量时间,为了提高效率可以增加算子并行度,但同时也会消耗更多计算资源,可以引入Async I/O,单个并行度也可以连续发送多个请求
         * 谁先响应就处理谁,减少等待耗时,如果数据库本身没有支持异步请求的客户端,可以通过线程池自己实现
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
        DataStreamSource<String> orderInfoStream = env.addSource(MyFlinkUtil.getKafkaSource(orderInfoTopic, groupId));
        DataStreamSource<String> orderDetailStream = env.addSource(MyFlinkUtil.getKafkaSource(orderDetailTopic, groupId));

        // 3.结构转换
        KeyedStream<OrderInfo, Long> orderInfoKeyedStream = orderInfoStream
                // 将订单流封装成订单实体类
                .map(new RichMapFunction<String, OrderInfo>() {
//                    private SimpleDateFormat sdf;
//                    @Override
//                    public void open(Configuration parameters) throws Exception {
//                        super.open(parameters);
//                        // 每次数据进来都要创建sdf对象,通用逻辑可以抽取成工具类
//                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//                    }
                    @Override
                    public OrderInfo map(String value) throws Exception {
                        /* {
                         *     "id":"34861",
                         *     "order_status":"1001",
                         *     "user_id":"1923",
                         *     "province_id":"23",
                         *     "total_amount":"25094.0",
                         *     "activity_reduce_amount":"0.0",
                         *     "coupon_reduce_amount":"0.0",
                         *     "original_total_amount":"25079.0",
                         *     "feight_fee":"15.0",
                         *     "create_time":"2021-11-29 16:51:53",
                         *     "expire_time":"2021-11-29 17:06:53",
                         *     // OrderInfo实体类不包含下面这些字段,JSON解析时会过滤掉,缺省的字段会给null值
                         *     "delivery_address":"第3大街第13号楼8单元124门",
                         *     "order_comment":"描述552216",
                         *     "consignee_tel":"13334498220",
                         *     "trade_body":"Apple iPhone 12 (A2404) 64GB 蓝色 支持移动联通电信5G 双卡双待手机等4件商品",
                         *     "consignee":"法外狂徒",
                         *     "out_trade_no":"824396433584891",
                         *     "img_url":"http://img.gmall.com/831615.jpg"
                         * }
                         */
                        // 输入的value字段无序,生成的OrderInfo字段有序
                        OrderInfo orderInfo = JSON.parseObject(value, OrderInfo.class);
                        // 生成Long类型时间戳字段后面设定水位线用
                        orderInfo.setCreate_ts(DateUtil.parseDateTimeToUnix(orderInfo.getCreate_time()));
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

        KeyedStream<OrderDetail, Long> orderDetailKeyedStream = orderDetailStream
                // 将订单明细流封装成订单明细实体类
                .map(new RichMapFunction<String, OrderDetail>() {
                    @Override
                    public OrderDetail map(String value) throws Exception {
                        /* {
                         *     "id":"97177",
                         *     "order_id":"34860",
                         *     "sku_id":"13",
                         *     "sku_name":"华为 HUAWEI P40 麒麟990 5G SoC芯片 30倍数字变焦 6GB+128GB亮黑色全网通5G手机",
                         *     "order_price":"4188.0",
                         *     "sku_num":"2",
                         *     "split_total_amount":"8376.0",
                         *     "create_time":"2021-11-29 16:51:53",
                         *     // OrderDetail实体类不包含下面这些字段,JSON解析时会过滤掉,缺省的字段会给null值
                         *     "source_id":"82",
                         *     "source_type":"2402"
                         * }
                         */
                        // 输入的value字段无序,生成的OrderDetail字段有序
                        OrderDetail orderDetail = JSON.parseObject(value, OrderDetail.class);
                        // 生成Long类型时间戳字段后面设定水位线用
                        orderDetail.setCreate_ts(DateUtil.parseDateTimeToUnix(orderDetail.getCreate_time()));
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
        // OrderInfo(id=34781, order_status=1001, user_id=622, province_id=14, total_amount=15891.0, activity_reduce_amount=0.0,
        // coupon_reduce_amount=0.0, original_total_amount=15872.0, feight_fee=19.0, create_time=2021-11-29 16:32:44,
        // operate_time=null, expire_time=2021-11-29 16:47:44, create_date=null, create_hour=null, create_ts=1638174764000)
        orderInfoKeyedStream.print("order_info");
        // OrderDetail(id=97073, order_id=34780, sku_id=16, sku_name=iphone, order_price=4488.0, sku_num=2, split_total_amount=8976.0,
        // split_activity_amount=null, split_coupon_amount=null, create_time=2021-11-29 16:32:44, create_ts=1638174764000)
        orderDetailKeyedStream.print("order_detail");

        // 4.双流join合成订单宽表流
        SingleOutputStreamOperator<OrderWide> orderWideStream = orderInfoKeyedStream
                // 基于时间间隔的连接,查看源码发现intervalJoin底层就是调用的connect
                .intervalJoin(orderDetailKeyedStream)
                // 设置时间上下边界
                .between(Time.seconds(-5), Time.seconds(5))
                // 自定义处理关联函数
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

        // 5.维表关联
//        // 常规方式,同步执行,效率低下
//        orderWideStream.map(new MapFunction<OrderWide, OrderWide>() {
//            @Override
//            public OrderWide map(OrderWide orderWide) throws Exception {
//                // 1).从流对象获取维度关联的key
//                String userId = orderWide.getUser_id().toString();
//                // 2).根据key去维度表查询维度数据
//                JSONObject dimInfo = DimUtil.getDimInfoWithCache("dim_user_info", userId);
//                // 3).将维度数据赋值给流对象的属性
//                orderWide.setUser_gender(dimInfo.getString("gender"));
//                return orderWide;
//            }
//        });

        // 采用Async异步查询,orderedWait保证输入输出顺序一致,unorderedWait不保证
        // 用户维度
        SingleOutputStreamOperator<OrderWide> orderWideWithUserStream = AsyncDataStream.unorderedWait(
                orderWideStream,
                new DimAsyncFunction<OrderWide>("dim_user_info") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getUser_id().toString();
                    }
                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        // dimInfo = {"GENDER":"F","ID":"217","CREATE_TIME":"1607112386000","NAME":"张三"...}
                        // 性别
                        orderWide.setUser_gender(dimInfo.getString("GENDER"));
                        // 年龄:1991-12-04
                        String birthday = dimInfo.getString("BIRTHDAY");
                        long between = System.currentTimeMillis() - DateUtil.parseDate(birthday).getTime();
                        long age = between / 365L / 24L / 3600L / 1000L;
                        orderWide.setUser_age((int)age);
                    }
                },
                60, TimeUnit.SECONDS
        );
        // 打印测试,发现user_gender和user_age不再是null值
        orderWideWithUserStream.print("order_wide_user");

        // 地区维度
        SingleOutputStreamOperator<OrderWide> orderWideWithProvinceStream = AsyncDataStream.unorderedWait(
                orderWideWithUserStream,
                new DimAsyncFunction<OrderWide>("dim_base_province") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getProvince_id().toString();
                    }
                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setProvince_name(dimInfo.getString("NAME"));
                        orderWide.setProvince_area_code(dimInfo.getString("AREA_CODE"));
                        orderWide.setProvince_iso_code(dimInfo.getString("ISO_CODE"));
                        orderWide.setProvince_3166_2_code(dimInfo.getString("ISO_3166_2"));
                    }
                },
                60, TimeUnit.SECONDS
        );

        // sku维度
        SingleOutputStreamOperator<OrderWide> orderWideWithSkuStream = AsyncDataStream.unorderedWait(
                orderWideWithProvinceStream,
                new DimAsyncFunction<OrderWide>("dim_sku_info") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getSku_id().toString();
                    }
                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setSpu_id(dimInfo.getLong("SPU_ID"));
                        orderWide.setTm_id(dimInfo.getLong("TM_ID"));
                        orderWide.setCategory3_id(dimInfo.getLong("CATEGORY3_ID"));
                    }
                },
                60, TimeUnit.SECONDS);

        // spu维度
        SingleOutputStreamOperator<OrderWide> orderWideWithSpuStream = AsyncDataStream.unorderedWait(
                orderWideWithSkuStream,
                new DimAsyncFunction<OrderWide>("dim_spu_info") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getSpu_id().toString();
                    }
                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setSpu_name(dimInfo.getString("SPU_NAME"));
                    }
                },
                60, TimeUnit.SECONDS
        );

        // 品牌维度
        SingleOutputStreamOperator<OrderWide> orderWideWithTmStream = AsyncDataStream.unorderedWait(
                orderWideWithSpuStream,
                new DimAsyncFunction<OrderWide>("dim_base_trademark") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getTm_id().toString();
                    }
                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setTm_name(dimInfo.getString("TM_NAME"));
                    }
                },
                60, TimeUnit.SECONDS
        );

        // 类别维度
        SingleOutputStreamOperator<OrderWide> orderWideWithCategoryStream = AsyncDataStream.unorderedWait(
                orderWideWithTmStream,
                new DimAsyncFunction<OrderWide>("dim_base_category3") {
                    @Override
                    public String getKey(OrderWide orderWide) {
                        return orderWide.getCategory3_id().toString();
                    }
                    @Override
                    public void join(OrderWide orderWide, JSONObject dimInfo) {
                        orderWide.setCategory3_name(dimInfo.getString("NAME"));
                    }
                },
                60, TimeUnit.SECONDS
        );
        // 打印测试
        orderWideWithCategoryStream.print("result");

        // 6.将订单宽表数据写入dwm层对应的topic
        orderWideWithCategoryStream
                .map(new MapFunction<OrderWide, String>() {
                    @Override
                    public String map(OrderWide orderWide) {
                        // 将java对象转换为json字符串,默认会过滤null值字段,可以添加过滤器处理
                        return JSON.toJSONString(orderWide, SerializerFeature.WriteMapNullValue);
                    }
                })
                .addSink(MyFlinkUtil.getKafkaSink("dwm_order_wide"));

        // 启动任务
        env.execute();
    }
}
