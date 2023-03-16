package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/8 16:03
 * @Desc:
 */
public class PageViewCount {

    public String url;

    public Long windowStart;

    public Long windowEnd;

    public Integer cnt;

    public PageViewCount() {
    }

    public PageViewCount(String url, Long windowStart, Long windowEnd, Integer cnt) {
        this.url = url;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.cnt = cnt;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public Integer getCnt() {
        return cnt;
    }

    public void setCnt(Integer cnt) {
        this.cnt = cnt;
    }

    @Override
    public String toString() {
        return "PageViewCount{" +
                "url='" + url + '\'' +
                ", windowStart=" + new Timestamp(windowStart) +
                ", windowEnd=" + new Timestamp(windowEnd) +
                ", cnt=" + cnt +
                '}';
    }
}
