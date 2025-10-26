package com.okccc.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.Filter;
import com.alibaba.fastjson2.filter.PropertyFilter;
import com.alibaba.fastjson2.filter.ValueFilter;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * @Author: okccc
 * @Date: 2021/11/23 15:26:09
 * @Desc: 处理JSON格式数据的工具类
 */
public class JSONUtil {
    public static void main(String[] args) {
        /*
         * 主要类
         * public class JSONObject extends JSON implements Map<String, Object>, Serializable
         * public class JSONArray extends JSON implements List<Object>, Serializable
         *
         * 主要方法
         * JSON.parseObject(jsonStr)：将JSON字符串转换成JSONObject
         * JSON.parseObject(jsonStr, OrderInfo.class)：将JSON字符串转换成java实体类
         * JSON.toJSONString(OrderInfo)：将java实体类转换成JSON字符串
         * JSON.parseArray(jsonStr)：将JSON字符串转换成json数组
         *
         * 常见错误
         * ValueError: Expecting, delimiter: line 4 column 1(char 23) 字符串不是标准json格式,看看是否有双引号和逗号缺失
         */

        common();

        String str = "[{\"Name\":\"_id\",\"Value\":\"264e6d619ce14393b34fb74c27b53b\"},{\"Name\":\"games\",\"Value\":[{\"Name\":\"game01\",\"Value\":[{\"Name\":\"title\",\"Value\":\"WAR3\"},{\"Name\":\"race\",\"Value\":[\"orc\",\"hum\",\"ne\"]}]},{\"Name\":\"game02\",\"Value\":[{\"Name\":\"title\",\"Value\":\"LOL\"},{\"Name\":\"isGood\",\"Value\":true}]}]},{\"Name\":\"info\",\"Value\":[[{\"Name\":\"league\",\"Value\":\"WCG世界总决赛\"},{\"Name\":\"player\",\"Value\":\"grubby\"}],[{\"Name\":\"club\",\"Value\":\"JDG\"},{\"Name\":\"league\",\"Value\":\"S12世界总决赛\"},{\"Name\":\"bonus\",\"Value\":[[{\"Name\":\"rank\",\"Value\":1},{\"Name\":\"money\",\"Value\":10000}],[{\"Name\":\"rank\",\"Value\":2},{\"Name\":\"money\",\"Value\":5000}]]}]]},{\"Name\":\"cts\",\"Value\":\"2022-07-31T11:01:56.675Z\"}]";
        JSONObject jsonObject = convertJSONArrayToJSONObject(JSON.parseArray(str));
        jsonLoop(jsonObject);
        System.out.println(jsonObject);
    }

    /**
     * 常用方法
     */
    public static void common() {
        // JSONObject实现了Map接口
        JSONObject obj = new JSONObject();
        obj.put("id", "1001");
        obj.put("name", "grubby");
        System.out.println(obj.keySet());  // [name, id]
        System.out.println(obj.values());  // [grubby, 1001]
        System.out.println(StringUtils.join(obj.values(), ","));  // grubby,1001
        System.out.println(obj.entrySet());  // [name=grubby, id=1001]
        System.out.println(obj.getOrDefault("age", 20));  // 20

        // JSONObject对象过滤键值对
        String value = "{\"data\":[{\"age\":\"19\",\"id\":\"01\",\"name\":\"aaa\"}],\"database\":\"maxwell\",\"table\":\"user\"}";
        JSONObject jsonObject = JSON.parseObject(value);
        System.out.println(jsonObject);
        List<String> columns = Arrays.asList("id,name".split(","));
        Set<String> keySet = jsonObject.getJSONArray("data").getJSONObject(0).keySet();
        keySet.removeIf(s -> !columns.contains(s));
        System.out.println(jsonObject);

        // 往JSONObject添加JSON格式的字符串会转译生成反斜杠
        JSONObject json01 = new JSONObject();
        JSONObject json02 = new JSONObject();
        JSONObject json03 = new JSONObject();
        json01.put("k1", Arrays.asList("a,b,c".split(",")));
        json02.put("k2", json01);
        json03.put("k3", json01.toJSONString());
        System.out.println(json01);  // {"k1":["a","b","c"]}
        System.out.println(json02);  // {"k2":{"k1":["a","b","c"]}}
        System.out.println(json03);  // {"k3":"{\"k1\":[\"a\",\"b\",\"c\"]}"}
        // 注意：JSONObject的key是字符串,但value可能是各种数据类型,所以获取value是get()而不是getString()
        JSONObject json04 = new JSONObject();
        JSONObject json05 = new JSONObject();
        json04.put("k4", json01.getString("k1"));
        json05.put("k5", json01.get("k1"));
        System.out.println(json04);  // {"k4":"[a, b, c]"}
        System.out.println(json05);  // {"k5":["a","b","c"]}

        // 将JSON字符串转换成java实体类时会将缺省字段设为null并过滤多余字段
        String str01 = "{\"name\":\"grubby\",\"age\":19,\"gender\":\"male\"}";
        String str02 = "{\"name\":\"grubby\",\"age\":19,\"gender\":\"male\",\"phone\":\"111\",\"email\":\"orc@qq.com\"}";
        User user01 = JSON.parseObject(str01, User.class);
        User user02 = JSON.parseObject(str02, User.class);
        System.out.println(user01);  // User(name=grubby, age=19, gender=male, phone=null)
        System.out.println(user02);  // User(name=grubby, age=19, gender=male, phone=111)

        // 将java实体类转换成JSON字符串时会过滤null值字段
        User user = new User();
        user.setName("grubby");
        String s1 = JSON.toJSONString(user);
        System.out.println(s1);  // {"age":0,"name":"grubby"}

        // 设置JSONWriter允许写null值
        String s2 = JSON.toJSONString(user, JSONWriter.Feature.WriteMapNullValue);
        System.out.println(s2);  // {"age":0,"gender":null,"name":"grubby","phone":null}

        // null值放JSON里面不好看,设置ValueFilter将其转换成空字符串
        String s3 = JSON.toJSONString(user,
                new ValueFilter[]{
                        new ValueFilter() {
                            @Override
                            public Object apply(Object object, String name, Object value) {
                                return Objects.requireNonNullElse(value, "");
                            }
                        }},
                JSONWriter.Feature.WriteMapNullValue);
        System.out.println(s3);  // {"age":0,"gender":"","name":"grubby","phone":""}

        // 如果有些null值要过滤而有些null值要保留,可以组合使用PropertyFilter和ValueFilter
        String s4 = JSON.toJSONString(user,
                new Filter[]{
                    new PropertyFilter() {
                        @Override
                        public boolean apply(Object object, String name, Object value) {
                            return "gender".equals(name) || value != null;
                        }
                    },
                    new ValueFilter() {
                        @Override
                        public Object apply(Object object, String name, Object value) {
                            return Objects.requireNonNullElse(value, "");
                        }
                    }
                },
                JSONWriter.Feature.WriteMapNullValue);
        System.out.println(s4);  // {"age":0,"gender":"","name":"grubby"}
    }

    /**
     * JSONArray包含多个JSONObject
     */
    public static JSONObject convertJSONArrayToJSONObject(JSONArray jsonArray) {
        // [{"Name":"a1","Value":"b1"},{"Name":"a2","Value":["b2","c2"]}] -> {"a1":"b1","a2":["b2","c2"]}
        JSONObject jsonObject = new JSONObject();
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            jsonObject.put(obj.getString("Name"), obj.get("Value"));
        }
        return jsonObject;
    }

    /**
     * JSONArray包含多个JSONArray
     */
    public static JSONArray convertJSONArrayToJSONArray(JSONArray jsonArray) {
        // [[{"Name":"a1","Value":"b1"},{"Name":"a2","Value":"b2"}],[...],[...]] -> [{"a1":"b1","a2":"b2"},{...},{...}]
        JSONArray res = new JSONArray();
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONArray array = jsonArray.getJSONArray(i);
            // 每个JSONArray又包含多个JSONObject
            JSONObject jsonObject = convertJSONArrayToJSONObject(array);
            res.add(jsonObject);
        }
        return res;
    }

    /**
     * JSONObject嵌套多层JSONArray
     */
    public static void jsonLoop(JSONObject input) {
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                input.put(key, "\\N");
            } else if (value.toString().startsWith("[{")) {
                JSONObject input01 = convertJSONArrayToJSONObject(JSON.parseArray(value.toString()));
                // 关键点：如果JSONObject某个key的value是JSONArray就继续递归
                jsonLoop(input01);
                input.put(key, input01);
            } else if (value.toString().startsWith("[[{")) {
                JSONArray jsonArray = convertJSONArrayToJSONArray(JSON.parseArray(value.toString()));
                JSONArray array = new JSONArray();
                for (int i = 0; i < jsonArray.size(); i++) {
                    JSONObject input02 = jsonArray.getJSONObject(i);
                    // 关键点：如果JSONObject某个key的value是JSONArray就继续递归
                    jsonLoop(input02);
                    array.add(input02);
                }
                input.put(key, array);
            } else {
                input.put(key, value);
            }
        }
    }

    @Data
    public static class User {
        String name;
        int age;
        String gender;
        String phone;
    }
}
