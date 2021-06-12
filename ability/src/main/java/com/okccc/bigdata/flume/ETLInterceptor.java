package com.okccc.bigdata.flume;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        // 判断event是否为空
        if (event == null) {
            return null;
        }

        // 将event转换成字符串
        String log = new String(event.getBody(), StandardCharsets.UTF_8);

        // 判断字符串是否json格式
        if (LogUtil.isJsonFormat(log)) {
            // url解码
            String log_decode = LogUtil.decode(log);
            // 返回新的event
            event.setBody(log_decode.getBytes(StandardCharsets.UTF_8));
            return event;
        } else {
            return null;
        }
    }

    @Override
    public List<Event> intercept(List<Event> list) {
        // 拦截器处理完后的event列表
        List<Event> events = new ArrayList<>();
        for (Event event : list) {
            // 给每条event都用拦截器处理
            Event event_new = intercept(event);
            if (event_new != null) {
                events.add(event_new);
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
            return new ETLInterceptor();
        }

        @Override
        public void configure(Context context) {
            // 获取flume配置文件的参数,一般用不到
        }
    }

}
