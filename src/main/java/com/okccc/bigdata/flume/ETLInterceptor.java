package com.okccc.bigdata.flume;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

/**
 * Author: okccc
 * Date: 2021/1/5 3:03 下午
 * Desc: 清洗nginx日志格式并做url解码
 */
public class ETLInterceptor implements Interceptor {
    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {
        if (event == null) {
            return null;
        }

        // 将event转换成字符串
        String line = new String(event.getBody(), StandardCharsets.UTF_8);
        // 日志格式校验
        if (!LogUtil.isJsonFormat(line)) {
            return null;
        }

//        // url解码
//        // {"@timestamp":"10/Nov/2021:01:05:08 +0800","ip":"221.206.47.218","Method":"POST","request_body":"..."}
//        JSONObject jsonObject = JSON.parseObject(line);
//        try {
//            // 对字符串的编码部分进行解码
//            String newBody = LogUtil.decode(jsonObject.getString("request_body"));
//            // 将解码后的字符串再塞回去,往json对象添加json字符串会转义生成反斜杠,所以采集数据时不建议url解码,等到处理数据时再处理
//            jsonObject.put("request_body", newBody);
//            // 返回新的event
//            event.setBody(jsonObject.toJSONString().getBytes(StandardCharsets.UTF_8));
//            return event;
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }

        // 条件过滤
        JSONObject jsonObject = JSON.parseObject(line);
        try {
            String body = jsonObject.getString("request_body");
            HashMap<String, String> hashMap = LogUtil.strToMap(body);
            String e = LogUtil.decode(hashMap.get("e"));
            String version = JSON.parseArray(e).getJSONObject(0).getString("version_name");
            if (version == null || version.length() == 0) {
                event.setBody(line.getBytes(StandardCharsets.UTF_8));
                return event;
            } else {
                String[] arr = version.split("\\.");
                if (Integer.parseInt(arr[0]) < 11 || (Integer.parseInt(arr[0]) >= 11 && Integer.parseInt(arr[1]) < 6)) {
                    event.setBody(line.getBytes(StandardCharsets.UTF_8));
                    return event;
                }
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<Event> intercept(List<Event> list) {
        // 给每个event对象添加拦截器处理
        list.removeIf(event -> intercept(event) == null);
        return list;
    }

    @Override
    public void close() {

    }

    // 按照agent配置文件中的interceptor类型,创建静态内部类生成拦截器对象
    public static class Builder implements Interceptor.Builder {
        @Override
        public Interceptor build() {
            return new ETLInterceptor();
        }

        @Override
        public void configure(Context context) {
            // 获取flume配置文件的参数,一般用不到
        }
    }

}
