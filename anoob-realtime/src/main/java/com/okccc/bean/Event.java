package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/1/4 18:40
 * @Desc: POJO是简单的java对象,没有继承类/实现接口/绑定框架,更纯净,JavaBean则会封装一些简单逻辑
 */
public class Event {

    public String user;

    public String url;

    public Long timestamp;

    public Event() {}

    public Event(String user, String url, Long timestamp) {
        this.user = user;
        this.url = url;
        this.timestamp = timestamp;
    }

    // 模拟flink源码里大量使用的of语法糖,这样就不用每次都写new()
    public static Event of(String user, String url, Long timestamp) {
        return new Event(user, url, timestamp);
    }

    @Override
    public String toString() {
        return "Event{" +
                "user='" + user + '\'' +
                ", url='" + url + '\'' +
                ", timestamp=" + new Timestamp(timestamp) +
                '}';
    }
}
