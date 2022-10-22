package com.okccc.realtime.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.PropertyFilter;
import com.alibaba.fastjson.serializer.SerializeFilter;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.ValueFilter;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
        test02();
//        test03();
//        test04();

        String str = "[{\"Name\":\"nick\",\"Value\":\"Ted\"},{\"Name\":\"info\",\"Value\":[{\"Name\":\"other\",\"Value\":[{\"Name\":\"course\",\"Value\":[{\"Name\":\"unit\",\"Value\":\"377\"},{\"Name\":\"index\",\"Value\":0}]},{\"Name\":\"unlocked\",\"Value\":true}]},{\"Name\":\"cnt\",\"Value\":\"3\"}]},{\"Name\":\"gender\",\"Value\":\"boy\"}]";
        JSONArray jsonArray = JSON.parseArray(str);
        JSONObject input = convertJsonArrayToJsonObject(jsonArray);
        // {"nick":"Ted","gender":"boy","info":"[{\"Value\":[{\"Value\":[{\"Value\":\"377\",\"Name\":\"unit\"},{\"Value\":0,\"Name\":\"index\"}],\"Name\":\"course\"},{\"Value\":true,\"Name\":\"unlocked\"}],\"Name\":\"other\"},{\"Value\":\"3\",\"Name\":\"cnt\"}]"}
        System.out.println(input);
        JSONObject output = new JSONObject();
        jsonLoop(input, output);
        // {"nick":"Ted","gender":"boy","info":{"other":{"course":{"unit":"377","index":"0"},"unlocked":"true"},"cnt":"3"}}
        System.out.println(output);
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
        // 往JSONObject添加json格式的字符串会转译生成反斜杠
        JSONObject json01 = new JSONObject();
        JSONObject json02 = new JSONObject();
        JSONObject json03 = new JSONObject();
        json01.put("k1", Arrays.asList("a,b,c".split(",")));
        json02.put("k2", json01);
        json03.put("k3", json01.toJSONString());
        System.out.println(json01);  // {"k1":["a","b","c"]}
        System.out.println(json02);  // {"k2":{"k1":["a","b","c"]}}
        System.out.println(json03);  // {"k3":"{\"k1\":[\"a\",\"b\",\"c\"]}"}
        // JSONObject的key是字符串,但value可能是各种数据类型,所以获取value是get()而不是getString()
        JSONObject json04 = new JSONObject();
        JSONObject json05 = new JSONObject();
        json04.put("k4", json01.getString("k1"));
        json05.put("k5", json01.get("k1"));
        System.out.println(json04);  // {"k4":"[a, b, c]"}
        System.out.println(json05);  // {"k5":["a","b","c"]}
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

    public static void jsonLoop(JSONObject input, JSONObject output) {
        // 解析json对象嵌套多层json数组
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                output.put(key, "\\N");
            } else if (value.toString().startsWith("[{")) {
                // 关键点：value是json数组就继续递归
                JSONArray jsonArray = JSON.parseArray(value.toString());
                JSONObject input01 = convertJsonArrayToJsonObject(jsonArray);
                JSONObject output01 = new JSONObject();
                jsonLoop(input01, output01);
                // 添加到外层json对象
                output.put(key, output01);
            } else {
                output.put(key, value.toString());
            }
        }
    }

    public static JSONObject convertJsonArrayToJsonObject(JSONArray jsonArray) {
        // 将json数组拆散拼接成json对象
        JSONObject jsonObject = new JSONObject();
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            jsonObject.put(obj.getString("Name"), obj.getString("Value"));
        }
        return jsonObject;
    }

    @Data
    public static class User {
        String name;
        int age;
        String gender;
        String phone;
    }

}
