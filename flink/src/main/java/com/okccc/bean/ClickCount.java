package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/20 10:54
 * @Desc:
 */
public class ClickCount {

    public Long windowStart;

    public Long windowEnd;

    public String province;

    public String city;

    public Integer cnt;

    public ClickCount() {
    }

    public ClickCount(Long windowStart, Long windowEnd, String province, String city, Integer cnt) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.province = province;
        this.city = city;
        this.cnt = cnt;
    }

    public Long getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Long windowStart) {
        this.windowStart = windowStart;
    }

    public Long getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Long windowEnd) {
        this.windowEnd = windowEnd;
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

    public Integer getCnt() {
        return cnt;
    }

    public void setCnt(Integer cnt) {
        this.cnt = cnt;
    }

    @Override
    public String toString() {
        return "ClickCount{" +
                "windowStart=" + new Timestamp(windowStart) +
                ", windowEnd=" + new Timestamp(windowEnd) +
                ", province='" + province + '\'' +
                ", city='" + city + '\'' +
                ", cnt=" + cnt +
                '}';
    }
}
