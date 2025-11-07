package com.okccc.func;

import com.alibaba.fastjson2.JSONObject;
import com.okccc.util.DateUtil;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;

/**
 * @Author: okccc
 * @Date: 2023/3/27 11:22:33
 * @Desc: 新老用户校验,状态编程
 */
public class CheckUserFunction extends RichMapFunction<JSONObject, JSONObject> {

    // 声明状态变量,记录用户上次访问日期
    private ValueState<String> lastVisitDate;

    @Override
    public void open(Configuration parameters) throws Exception {
        // 初始化状态变量
        lastVisitDate = getRuntimeContext().getState(new ValueStateDescriptor<>("last-visit-date", Types.STRING));
    }

    @Override
    public JSONObject map(JSONObject value) throws Exception {
        // 获取当前进来数据的访客状态,0是老用户 1是新用户
        String isNew = value.getJSONObject("common").getString("is_new");

        // 获取上次访问日期和当前访问日期
        String lastDate = lastVisitDate.value();
        String currentDate = DateUtil.parseUnixToDate(value.getLong("ts"));

        // 新访客才需要修复,老访客不需要
        if ("1".equals(isNew)) {
            // 判断是否第一次访问
            if (lastDate == null) {
                // 更新状态值
                lastVisitDate.update(currentDate);
            } else if (!lastDate.equals(currentDate)) {
                // 不是同一天就是老用户,同一天的话比如今天刚注册那不管访问多少次都是新客户
                value.getJSONObject("common").put("is_new", "0");
            }
        } else {
            // 老访客如果卸载app那么lastVisitDate为空,重装之后再登录就会变成新用户,所以统一将老用户的lastVisitDate设为昨天
            if (lastDate == null) {
                lastVisitDate.update(DateUtil.parseUnixToDate(value.getLong("ts") - 24 * 3600 * 1000L));
            }
        }

        return value;
    }
}
