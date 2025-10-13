package com.okccc.flume.interceptor;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @Author: okccc
 * @Date: 2022/2/28 15:15:15
 * @Desc: 拦截器两个功能：1.校验数据是否JSON格式  2.修改header中的时间戳解决零点漂移问题
 */
public class TimestampInterceptor implements Interceptor {
    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {
        // 1.获取header和body
        Map<String, String> headers = event.getHeaders();
        String body = new String(event.getBody(), StandardCharsets.UTF_8);

        try {
            // 2.将body数据格式转换成JSON,方便后续解析
            JSONObject jsonObject = JSON.parseObject(body);

            // 3.将header中的timestamp字段替换成日志本身的时间戳,解决零点漂移问题
            String ts = jsonObject.getString("ts");
            headers.put("timestamp", ts);
            return event;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public List<Event> intercept(List<Event> events) {
        // 给每个event对象添加拦截器
        Iterator<Event> iterator = events.iterator();
        while (iterator.hasNext()) {
            Event event = iterator.next();
            if (intercept(event) == null) {
                iterator.remove();
            }
        }
        return events;
    }

    @Override
    public void close() {

    }

    // 按照agent配置文件中的interceptor类型,创建静态内部类生成拦截器对象
    public static class Builder implements Interceptor.Builder {
        @Override
        public Interceptor build() {
            return new TimestampInterceptor();
        }

        @Override
        public void configure(Context context) {

        }
    }
}
