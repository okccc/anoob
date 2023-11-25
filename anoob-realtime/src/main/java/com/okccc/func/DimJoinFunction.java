package com.okccc.func;

import com.alibaba.fastjson.JSONObject;

/**
 * @Author: okccc
 * @Date: 2023/8/9 18:54:45
 * @Desc: 维度关联接口
 */
public interface DimJoinFunction<T> {

    // 获取维度关联的key
    String getKey(T input);

    // 将维度信息补充到流对象
    void join(T input, JSONObject dimInfo);
}
