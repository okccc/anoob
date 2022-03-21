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
         * 主要方法
         * JSON.parseObject(jsonStr)：将json字符串转换成json对象
         * JSON.parseObject(jsonStr, OrderInfo.class)：将json字符串转换成java实体类
         * JSON.toJSONString(OrderInfo)：将java实体类转换成json字符串
         *
         * 常见错误
         * ValueError: Expecting, delimiter: line 4 column 1(char 23) 字符串不是标准json格式,看看是否有双引号和逗号缺失
         */

//        test01();
//        test02();
        test03();
//        test04();
    }

    public static void test01() {
        JSONObject object = new JSONObject();
        object.put("id", "1001");
        object.put("name", "grubby");
        System.out.println(object.keySet());  // [name, id]
        System.out.println(object.values());  // [grubby, 1001]
        System.out.println(StringUtils.join(object.values(), ","));  // grubby,1001
        System.out.println(object.entrySet());  // [name=grubby, id=1001]

        // json对象过滤键值对
        String value = "{\"data\":[{\"age\":\"19\",\"id\":\"001\",\"name\":\"aaa\"}],\"database\":\"maxwell\",\"table\":\"comment_info\",\"type\":\"INSERT\"}";
        JSONObject jsonObject = JSON.parseObject(value);
        // {"database":"maxwell","data":[{"age":"19","name":"aaa","id":"001"}],"type":"INSERT","table":"comment_info"}
        System.out.println(jsonObject);
        JSONObject data = jsonObject.getJSONArray("data").getJSONObject(0);
        List<String> columns = Arrays.asList("id,name".split(","));
        Set<String> keySet = data.keySet();
        keySet.removeIf(s -> !columns.contains(s));
        // {"database":"maxwell","data":[{"name":"aaa","id":"001"}],"type":"INSERT","table":"comment_info"}
        System.out.println(jsonObject);
    }

    public static void test02() {
        // 往json对象添加包含json的字符串会转译生成反斜杠
        JSONObject json01 = new JSONObject();
        JSONObject json02 = new JSONObject();
        JSONObject json03 = new JSONObject();
        json01.put("k1", "v1");
        json02.put("k1", json01);
        json03.put("k1", json01.toJSONString());
        System.out.println(json01);  // {"k1":"v1"}
        System.out.println(json02);  // {"k1":{"k1":"v1"}}
        System.out.println(json03);  // {"k1":"{\"k1\":\"v1\"}"}
    }

    public static void test03() {
        // 将json字符串转换成java实体类时会将缺省字段设为null并过滤多余字段
        String str01 = "{\"name\":\"grubby\",\"age\":19,\"gender\":\"male\"}";
        String str02 = "{\"name\":\"grubby\",\"age\":19,\"gender\":\"male\",\"phone\":\"111\",\"email\":\"orc@qq.com\"}";
        User user01 = JSON.parseObject(str01, User.class);
        User user02 = JSON.parseObject(str02, User.class);
        System.out.println(user01);  // User(name=grubby, age=19, gender=male, phone=null)
        System.out.println(user02);  // User(name=grubby, age=19, gender=male, phone=111)
    }

    public static void test04() {
        // 将java实体类转换成json字符串时会过滤null值字段
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

        // 如果有些null值要过滤而有些null值要保留,可以组合使用ValueFilter和PropertyFilter
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
