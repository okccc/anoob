package com.okccc.flink;

import com.okccc.flink.bean.OrderData;
import com.okccc.flink.bean.ReceiptData;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

/**
 * @Author: okccc
 * @Date: 2021/9/23 15:23:37
 * @Desc: 订单支付和账单流水实时对账
 */
public class VerifyOrder {

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);

        // 订单流
        SingleOutputStreamOperator<OrderData> orderStream = env
                .fromSource(
                        FileSource.forRecordStreamFormat(new TextLineInputFormat(), new Path("anoob-realtime/input/OrderData.csv")).build(),
                        WatermarkStrategy.noWatermarks(), "Order Source")
                .map((MapFunction<String, OrderData>) value -> {
                    // 34729,pay,sd76f87d6,1558430844
                    String[] arr = value.split(",");
                    return new OrderData(arr[0], arr[1], arr[2], Long.parseLong(arr[3]) * 1000);
                })
                .filter(r -> "pay".equals(r.eventType))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OrderData>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                );

        // 账单流
        SingleOutputStreamOperator<ReceiptData> receiptStream = env
                .fromSource(FileSource.forRecordStreamFormat(new TextLineInputFormat(), new Path("anoob-realtime/input/ReceiptData.csv")).build(),
                        WatermarkStrategy.noWatermarks(), "Receipt Source")
                .map((MapFunction<String, ReceiptData>) value -> {
                    // ewr342as4,wechat,1558430845
                    String[] arr = value.split(",");
                    return new ReceiptData(arr[0], arr[1], Long.parseLong(arr[2]) * 1000);
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<ReceiptData>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                );

        // 创建侧输出流标签
        OutputTag<String> orderTag = new OutputTag<>("order") {};
        OutputTag<String> receiptTag = new OutputTag<>("receipt") {};

        // 双流合并
        SingleOutputStreamOperator<String> result = orderStream
                // 第一条流分流
                .keyBy(r -> r.receiptId)
                // 第二条流分流
                .connect(receiptStream.keyBy(r -> r.receiptId))
                // 双流合并的底层是CoProcessFunction
                .process(new CoProcessFunction<OrderData, ReceiptData, String>() {
                    // 声明状态保存订单事件和到账事件,两条流之间数据肯定是乱序的谁等谁都有可能
                    private ValueState<OrderData> order;
                    private ValueState<ReceiptData> receipt;

                    @Override
                    public void open(Configuration parameters) throws Exception {
                        super.open(parameters);
                        // 实例化状态变量
                        order = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("order", Types.POJO(OrderData.class)));
                        receipt = getRuntimeContext().getState(
                                new ValueStateDescriptor<>("receipt", Types.POJO(ReceiptData.class)));
                    }

                    @Override
                    public void processElement1(OrderData value, Context ctx, Collector<String> out) throws Exception {
                        // 订单事件进来了,判断之前是否有到账事件
                        if (receipt.value() != null) {
                            // 有就正常输出,清空状态
                            out.collect("账单ID " + value.receiptId + " 对账成功");
                            order.clear();
                            receipt.clear();
                        } else {
                            // 到账事件还没来,注册10秒后的定时器(具体时间视数据乱序程度而定)
                            ctx.timerService().registerEventTimeTimer(ctx.timerService().currentWatermark() + 10000L);
                            // 更新订单事件状态
                            order.update(value);
                        }
                    }

                    @Override
                    public void processElement2(ReceiptData value, Context ctx, Collector<String> out) throws Exception {
                        // 到账事件进来了,判断之前是否有订单事件
                        if (order.value() != null) {
                            // 有就正常输出,清空状态
                            out.collect("账单ID " + value.receiptId + " 对账成功");
                            order.clear();
                            receipt.clear();
                        } else {
                            // 订单事件还没来,注册5秒后的定时器(具体时间视数据乱序程度而定)
                            ctx.timerService().registerEventTimeTimer(ctx.timerService().currentWatermark() + 5000L);
                            // 更新到账事件状态
                            receipt.update(value);
                        }
                    }

                    @Override
                    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
                        super.onTimer(timestamp, ctx, out);
                        // 定时器触发了,判断下哪一个状态来了,说明另一个状态还没来,放到侧输出流
                        if (order.value() != null) {
                            ctx.output(orderTag, "订单ID " + order.value().orderId + " 对账失败,到账事件10秒内仍未到达");
                        }
                        if (receipt.value() != null) {
                            ctx.output(receiptTag, "账单ID " + receipt.value().receiptId + " 对账失败,订单事件5秒内仍未到达");
                        }
                        // 清空状态
                        order.clear();
                        receipt.clear();
                    }
                });

        result.print("match");
        result.getSideOutput(orderTag).print("unmatch-order");
        result.getSideOutput(receiptTag).print("unmatch-receipt");

        // 启动任务
        env.execute();
    }
}
