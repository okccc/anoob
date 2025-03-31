package com.okccc.kafka;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * @Author: okccc
 * @Date: 2022/2/28 15:15:15
 * @Desc: 给header添加事件时间解决0点漂移问题
 */
public class TimeInterceptor implements Interceptor {
    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {
        // 获取header
        Map<String, String> headers = event.getHeaders();
        // 获取body
        String body = new String(event.getBody(), StandardCharsets.UTF_8);
        JSONObject jsonObject = JSON.parseObject(body);
        String ts = jsonObject.getString("ts");
        // 将日志里的实际时间添加到header,其key必须是timestamp,然后flume会识别这个key的值作为时间写入hdfs
        headers.put("timestamp", ts);
        return event;
    }

    @Override
    public List<Event> intercept(List<Event> events) {
        // 给每个event对象添加拦截器
        for (Event event : events) {
            intercept(event);
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
            return new TimeInterceptor();
        }

        @Override
        public void configure(Context context) {

        }
    }
}
