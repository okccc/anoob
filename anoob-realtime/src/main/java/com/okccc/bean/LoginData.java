package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/8 14:32
 * @Desc:
 */
public class LoginData {

    public String userId;
    
    public String eventType;
    
    public Long timestamp;

    public LoginData() {
    }

    public LoginData(String userId, String eventType, Long timestamp) {
        this.userId = userId;
        this.eventType = eventType;
        this.timestamp = timestamp;
    }

    public static LoginData of(String userId, String eventType, Long timestamp) {
        return new LoginData(userId, eventType, timestamp);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "LoginLog{" +
                "userId='" + userId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", timestamp=" + new Timestamp(timestamp) +
                '}';
    }
}
