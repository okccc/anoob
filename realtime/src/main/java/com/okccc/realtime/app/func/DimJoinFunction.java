package com.okccc.realtime.app.func;

import com.alibaba.fastjson.JSONObject;

import java.text.ParseException;

/**
 * @Author: okccc
 * @Date: 2021/11/5 下午5:47
 * @Desc: 维度关联接口
 */
public interface DimJoinFunction<T> {

    // 获取维度关联的key
    String getKey(T input);

    // 关联维度表给流对象的属性赋值
    void join(T input, JSONObject dimInfo) throws ParseException;
}
