package com.okccc.realtime.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.PropertyFilter;
import com.alibaba.fastjson.serializer.SerializeFilter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.ValueFilter;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Author: okccc
 * Date: 2021/11/23 下午6:09
 * Desc: 处理JSON格式数据的工具类
 */
public class JSONUtil {
    public static void main(String[] args) {
        /*
         * JSON.parseObject(jsonStr)：将json字符串转换成json对象
         * JSON.parseObject(jsonStr, OrderInfo.class)：将json字符串转换成java实体类
         * JSON.toJSONString(OrderInfo)：将java实体类转换成json字符串
         */

        test03();
    }

    public static void test03() {
        // 将java实体类转换成json字符串时默认会过滤null值字段
        User user = new User();
        user.setName("grubby");
        String str01 = JSON.toJSONString(user);
        System.out.println(str01);  // {"age":0,"name":"grubby"}

        // 设置SerializerFeature允许写null值
        String str02 = JSON.toJSONString(user, SerializerFeature.WriteMapNullValue);
        System.out.println(str02);  // {"age":0,"gender":null,"name":"grubby","phone":null}

        // null值放json里面不好看,可以将其转换成空字符串,可以使用SerializeFilter接口的子接口ValueFilter
        String str03 = JSON.toJSONString(user, new ValueFilter() {
            @Override
            public Object process(Object object, String name, Object value) {
                if (value == null) {
                    return "";
                }
                return value;
            }
        });
        System.out.println(str03);  // {"age":0,"gender":"","name":"grubby","phone":""}

        // 如果有些null值要过滤有些null值要保留,可以组合使用ValueFilter和PropertyFilter
        String str04 = JSON.toJSONString(user, new SerializeFilter[]{
                new PropertyFilter() {
                    @Override
                    public boolean apply(Object object, String name, Object value) {
                        return "gender".equals(name) || value != null;
                    }
                },
                new ValueFilter() {
                    @Override
                    public Object process(Object object, String name, Object value) {
                        if (value == null) {
                            return "";
                        }
                        return value;
                    }
                }
        }, SerializerFeature.WriteMapNullValue);
        System.out.println(str04);  // {"age":0,"gender":"","name":"grubby"}
    }

    @Data
    public static class User {
        String name;
        int age;
        String gender;
        String phone;
    }

}
