package com.okccc.realtime.app.dwm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.app.func.DimAsyncFunction;
import com.okccc.realtime.bean.OrderDetail;
import com.okccc.realtime.bean.OrderInfo;
import com.okccc.realtime.bean.OrderWide;
import com.okccc.realtime.utils.MyKafkaUtil;
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.ProcessJoinFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

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
         * 事实数据和维度数据关联：维表关联,就是在流计算中查询hbase数据,用维度表补全事实表中的字段,比如userId关联用户维度,skuId关联商品维度
         * areaId关联地区维度,外部数据源的查询往往是流计算性能瓶颈所在,可以通过加入旁路缓存模式和异步查询进行优化
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
         * 异步IO：flink算子默认同步,比如MapFunction向外部数据源hbase请求数据,IO阻塞,等待响应,然后再发送下一个请求,等待请求会耗费大量时间
         * 为了提高效率可以增加MapFunction并行度,但是会消耗更多计算资源,可以引入Async I/O,单个并行度也可以连续发送多个请求,谁先响应就处理谁
         * 减少等待耗时,如果数据库本身没有支持异步请求的客户端,可以通过线程池自己实现
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

        // 4.维表关联
//        // 常规方式,同步执行,效率低下
//        orderWideStream.map(new MapFunction<OrderWide, OrderWide>() {
//            @Override
//            public OrderWide map(OrderWide orderWide) throws Exception {
//                // 从流对象获取维度关联的key
//                String userId = orderWide.getUser_id().toString();
//                // 根据key去维度表查询维度数据
//                JSONObject dimInfo = DimUtil.getDimInfoWithCache("dim_user_info", userId);
//                // 将维度数据赋值给流对象的属性
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
                    public void join(OrderWide orderWide, JSONObject dimInfo) throws ParseException {
                        // 性别
                        orderWide.setUser_gender(dimInfo.getString("gender"));
                        // 年龄
                        String birthday = dimInfo.getString("birthday");
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        long time = sdf.parse(birthday).getTime();
                        int age = (int) (System.currentTimeMillis() - time) / (365 * 24 * 3600 * 1000);
                        orderWide.setUser_age(age);
                    }
                },
                60,
                TimeUnit.SECONDS
        );

        // 打印测试
        orderWideWithUserStream.print();

        // 启动任务
        env.execute();
    }
}