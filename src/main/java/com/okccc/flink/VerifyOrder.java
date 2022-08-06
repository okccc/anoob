package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.CoProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.sql.Timestamp;
import java.time.Duration;

/**
 * Author: okccc
 * Date: 2021/9/23 下午3:37
 * Desc: 订单支付和账单流水实时对账
 */
public class VerifyOrder {
    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);

        // 获取数据源
        SingleOutputStreamOperator<OrderEvent> orderStream = env
                .readTextFile("input/OrderLog.csv")
                .map((MapFunction<String, OrderEvent>) value -> {
                    // 34729,pay,sd76f87d6,1558430844
                    String[] arr = value.split(",");
                    return OrderEvent.of(arr[0], arr[1], arr[2], Long.parseLong(arr[3]) * 1000);
                })
                .filter(r -> r.eventType.equals("pay"))
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<OrderEvent>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                );
        SingleOutputStreamOperator<ReceiptEvent> receiptStream = env
                .readTextFile("input/ReceiptLog.csv")
                .map((MapFunction<String, ReceiptEvent>) value -> {
                    // ewr342as4,wechat,1558430845
                    String[] arr = value.split(",");
                    return ReceiptEvent.of(arr[0], arr[1], Long.parseLong(arr[2]) * 1000);
                })
                .assignTimestampsAndWatermarks(
                        WatermarkStrategy.<ReceiptEvent>forBoundedOutOfOrderness(Duration.ofSeconds(0))
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                );

        // 双流合并
        SingleOutputStreamOperator<String> result = orderStream
                // 第一条流分流
                .keyBy(r -> r.receiptId)
                // 第二条流分流
                .connect(receiptStream.keyBy(r -> r.receiptId))
                // 双流合并的底层是CoProcessFunction
                .process(new MyCoProcessFunction());

        result.print("match");
        result.getSideOutput(new OutputTag<String>("order"){}).print("unmatch-order");
        result.getSideOutput(new OutputTag<String>("receipt"){}).print("unmatch-receipt");

        // 启动任务
        env.execute();
    }

    // 自定义合流处理函数
    public static class MyCoProcessFunction extends CoProcessFunction<OrderEvent, ReceiptEvent, String> {
        // 声明状态,保存订单事件和到账事件,两条流相互之间数据肯定是乱序的谁等谁都有可能
        private ValueState<OrderEvent> orderState;
        private ValueState<ReceiptEvent> receiptState;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            // 实例化状态变量
            orderState = getRuntimeContext().getState(
                    new ValueStateDescriptor<>("order", Types.POJO(OrderEvent.class)));
            receiptState = getRuntimeContext().getState(
                    new ValueStateDescriptor<>("receipt", Types.POJO(ReceiptEvent.class)));
        }

        @Override
        public void processElement1(OrderEvent value, Context ctx, Collector<String> out) throws Exception {
            // 订单事件进来了,判断之前是否有到账事件
            if (receiptState.value() != null) {
                // 有就正常输出,清空状态
                out.collect("账单ID " + value.receiptId + " 对账成功");
                orderState.clear();
                receiptState.clear();
            } else {
                // 到账事件还没来,注册10秒后的定时器(具体时间视数据乱序程度而定)
                ctx.timerService().registerEventTimeTimer(ctx.timerService().currentWatermark() + 10 * 1000);
                // 更新订单事件状态
                orderState.update(value);
            }
        }

        @Override
        public void processElement2(ReceiptEvent value, Context ctx, Collector<String> out) throws Exception {
            // 到账事件进来了,判断之前是否有订单事件
            if (orderState.value() != null) {
                // 有就正常输出,清空状态
                out.collect("账单ID " + value.receiptId + " 对账成功");
                orderState.clear();
                receiptState.clear();
            } else {
                // 订单事件还没来,注册5秒后的定时器(具体时间视数据乱序程度而定)
                ctx.timerService().registerEventTimeTimer(ctx.timerService().currentWatermark() + 5 * 1000);
                // 更新到账事件状态
                receiptState.update(value);
            }
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            super.onTimer(timestamp, ctx, out);
            // 定时器触发了,判断下哪一个状态来了,说明另一个状态还没来,放到侧输出流
            if (orderState.value() != null) {
                ctx.output(new OutputTag<String>("order"){}, "订单ID " + orderState.value().orderId + " 对账失败,到账事件10秒内仍未到达！");
            }
            if (receiptState.value() != null) {
                ctx.output(new OutputTag<String>("receipt"){}, "账单ID " + receiptState.value().receiptId + " 对账失败,订单事件5秒内仍未到达！");
            }
            // 清空状态
            orderState.clear();
            receiptState.clear();
        }
    }

    // 输入数据POJO类
    public static class OrderEvent {
        public String orderId;
        public String eventType;
        public String receiptId;
        public Long timestamp;

        public OrderEvent() {
        }

        public OrderEvent(String orderId, String eventType, String receiptId, Long timestamp) {
            this.orderId = orderId;
            this.eventType = eventType;
            this.receiptId = receiptId;
            this.timestamp = timestamp;
        }

        public static OrderEvent of(String orderId, String eventType, String receiptId, Long timestamp) {
            return new OrderEvent(orderId, eventType, receiptId, timestamp);
        }

        @Override
        public String toString() {
            return "OrderEvent{" +
                    "orderId='" + orderId + '\'' +
                    ", eventType='" + eventType + '\'' +
                    ", receiptId='" + receiptId + '\'' +
                    ", timestamp=" + new Timestamp(timestamp) +
                    '}';
        }
    }

    public static class ReceiptEvent {
        public String receiptId;
        public String eventType;
        public Long timestamp;

        public ReceiptEvent() {
        }

        public ReceiptEvent(String receiptId, String eventType, Long timestamp) {
            this.receiptId = receiptId;
            this.eventType = eventType;
            this.timestamp = timestamp;
        }

        public static ReceiptEvent of(String receiptId, String eventType, Long timestamp) {
            return new ReceiptEvent(receiptId, eventType, timestamp);
        }

        @Override
        public String toString() {
            return "ReceiptEvent{" +
                    "receiptId='" + receiptId + '\'' +
                    ", eventType='" + eventType + '\'' +
                    ", timestamp=" + new Timestamp(timestamp) +
                    '}';
        }
    }
}
