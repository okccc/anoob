package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/8 14:52
 * @Desc:
 */
public class ClickData {

    public String userId;

    public String adId;

    public String province;

    public String city;

    public Long timestamp;

    public ClickData() {
    }

    public ClickData(String userId, String adId, String province, String city, Long timestamp) {
        this.userId = userId;
        this.adId = adId;
        this.province = province;
        this.city = city;
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAdId() {
        return adId;
    }

    public void setAdId(String adId) {
        this.adId = adId;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ClickLog{" +
                "userId='" + userId + '\'' +
                ", adId='" + adId + '\'' +
                ", province='" + province + '\'' +
                ", city='" + city + '\'' +
                ", timestamp=" + new Timestamp(timestamp) +
                '}';
    }
}
