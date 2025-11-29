package com.okccc.func;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.flink.api.common.serialization.SerializationSchema;

import java.nio.charset.StandardCharsets;

/**
 * @Author: okccc
 * @Date: 2024/11/8 21:44:07
 * @Desc: 自定义key的序列化器,kafka的key主要用来计算分区,保证相同uid的数据进入同一个分区,kafka是单分区有序的
 */
public class MyKeySerializationSchema implements SerializationSchema<String> {

    private final String column;

    public MyKeySerializationSchema(String column) {
        this.column = column;
    }

    @Override
    public byte[] serialize(String element) {
        JSONObject jsonObject = JSON.parseObject(element);
        String key = jsonObject.getString(column);
        return key.getBytes(StandardCharsets.UTF_8);
    }
}
