package com.okccc.warehouse.flume;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Author: okccc
 * Date: 2020/12/6 19:47
 * Desc: 给不同类型的日志添加header区分
 */
public class TypeInterceptor implements Interceptor {
    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {
        // Event: {headers:{} body: 61 61 61  aaa} headers默认是空,根据body中的日志类型填充headers
        if (event == null) {
            return null;
        }
        // 获取body
        byte[] bytes = event.getBody();
        // 将字节数组转换成字符串
        String body = new String(bytes, StandardCharsets.UTF_8);
        // 获取header
        Map<String, String> headers = event.getHeaders();
        // 填充header
        if (body.contains("start")) {
            headers.put("type", "start");  // 启动日志
        } else {
            headers.put("type", "event");  // 事件日志
        }
        // 返回填充headers后的Event
        return event;
    }

    @Override
    public List<Event> intercept(List<Event> list) {
//        // 1.ArrayList直接删除数据会有线程安全问题: java.util.ConcurrentModificationException
//        for (Event event : list) {
//            if (intercept(event) == null) {
//                list.remove(event);
//            }
//        }

//        // 2.存放拦截器处理过后的event,这样会多创建对象,性能不如直接在迭代器里删除好
//        List<Event> events = new ArrayList<>();
//        for (Event event : list) {
//            Event newEvent = intercept(event);
//            if (newEvent != null) {
//                events.add(newEvent);
//            }
//        }
//        return events;

        // 3.迭代器
        Iterator<Event> iterator = list.iterator();
        while (iterator.hasNext()) {
            if (intercept(iterator.next()) == null) {
                iterator.remove();
            }
        }
        return list;
    }

    @Override
    public void close() {

    }

    // 按照agent配置文件中的interceptor类型,创建静态内部类生成拦截器对象
    public static class Builder implements Interceptor.Builder {
        @Override
        public Interceptor build() {
            return new TypeInterceptor();
        }

        @Override
        public void configure(Context context) {

        }
    }
}
