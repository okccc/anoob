package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/8 15:02
 * @Desc:
 */
public class ApacheLog {

    public String ip;

    public String userId;

    public String method;

    public String url;

    public Long timestamp;

    public ApacheLog() {
    }

    public ApacheLog(String ip, String userId, String method, String url, Long timestamp) {
        this.ip = ip;
        this.userId = userId;
        this.method = method;
        this.url = url;
        this.timestamp = timestamp;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ApacheLog{" +
                "ip='" + ip + '\'' +
                ", userId='" + userId + '\'' +
                ", method='" + method + '\'' +
                ", url='" + url + '\'' +
                ", timestamp=" + new Timestamp(timestamp) +
                '}';
    }
}
