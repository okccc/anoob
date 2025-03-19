package com.okccc.func;

import com.alibaba.fastjson.JSONObject;
import com.okccc.util.DateUtil;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;

import java.time.Duration;

/**
 * @Author: okccc
 * @Date: 2023/3/28 20:34:39
 * @Desc: 筛选独立访客,状态编程
 */
public class FilterUserFunction extends RichFilterFunction<JSONObject> {

    // 声明状态变量,记录设备上次访问日期
    private ValueState<String> lastVisitDate;

    @Override
    public void open(Configuration parameters) throws Exception {
        // 创建状态描述符
        ValueStateDescriptor<String> stateDescriptor = new ValueStateDescriptor<>("last-visit-date", Types.STRING);
        // 独立访客的状态用来筛选当天是否访问过,第二天就没用了,所以要设置失效时间ttl,避免状态常驻内存
        stateDescriptor.enableTimeToLive(
                StateTtlConfig
                        // 设置状态存活时间为1天
                        .newBuilder(Duration.ofDays(1))
                        // 状态更新策略：比如状态是今天10点创建11点更新12点读取,那么失效时间是明天Disabled(10点)/OnCreateAndWrite(11点)/OnReadAndWrite(12点)
                        .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                        // 状态可见性：内存中的状态过期后,如果没有被jvm垃圾回收,是否还会返回给调用者
                        .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                        .build()
        );
        // 初始化状态变量
        lastVisitDate = getRuntimeContext().getState(stateDescriptor);
    }

    @Override
    public boolean filter(JSONObject value) throws Exception {
        // 先判断是否从别的页面跳转过来
        String lastPageId = value.getJSONObject("page").getString("last_page_id");
        if (lastPageId != null && !lastPageId.isEmpty()) {
            // 有上一页,说明肯定不是第一次访问,直接过滤
            return false;
        }

        // 不是跳转页面,就继续判断当前访问日期和上次访问日期是否相同
        String lastDate = lastVisitDate.value();
        String currentDate = DateUtil.parseUnixToDate(value.getLong("ts"));
        if (lastDate == null || !lastDate.equals(currentDate)) {
            // 还没访问过,更新状态变量,保留该条数据
            lastVisitDate.update(currentDate);
            return true;
        } else {
            // 已经访问过,过滤该条数据
            return false;
        }
    }
}
