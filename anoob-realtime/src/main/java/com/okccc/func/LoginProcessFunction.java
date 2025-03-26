package com.okccc.func;

import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2023/9/7 18:52:45
 * @Desc: 筛选登录用户,状态编程
 */
public class LoginProcessFunction extends KeyedProcessFunction<String, JSONObject, JSONObject> {

    // 声明状态变量,记录每次会话的第一条数据
    private ValueState<JSONObject> firstData;

    @Override
    public void open(Configuration parameters) {
        // 创建状态描述符
        ValueStateDescriptor<JSONObject> stateDescriptor = new ValueStateDescriptor<>("first", JSONObject.class);
        // 登录用户的状态用来筛选当天第一次访问,第二天就没用了,所以要设置失效时间ttl,避免状态常驻内存
        stateDescriptor.enableTimeToLive(
                StateTtlConfig.newBuilder(Duration.ofDays(1))
                        .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                        .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                        .build()
        );
        // 初始化状态变量
        firstData = getRuntimeContext().getState(stateDescriptor);
    }

    @Override
    public void processElement(JSONObject value, KeyedProcessFunction<String, JSONObject, JSONObject>.Context ctx, Collector<JSONObject> out) throws Exception {
        // 获取状态中的数据
        JSONObject jsonObject = firstData.value();
        // 获取当前数据的时间戳
        Long currentTs = value.getLong("ts");

        // 判断状态是否为空
        if (jsonObject == null) {
            firstData.update(value);
            // 注册5秒后的定时器,和数据乱序程度保持一致
            ctx.timerService().registerProcessingTimeTimer(currentTs + 5000L);
        } else {
            // 不为空就比较两条数据的时间大小
            Long stateTs = jsonObject.getLong("ts");
            // 将原本更早生成但因为乱序而迟到的数据更新到状态
            if (currentTs < stateTs) {
                firstData.update(value);
            }
        }
    }

    @Override
    public void onTimer(long timestamp, KeyedProcessFunction<String, JSONObject, JSONObject>.OnTimerContext ctx, Collector<JSONObject> out) throws Exception {
        // 定时器触发,输出数据
        out.collect(firstData.value());
    }
}
