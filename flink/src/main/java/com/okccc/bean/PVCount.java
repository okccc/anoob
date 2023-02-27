package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/9 21:22
 * @Desc:
 */
public class PVCount {

    public Long windowStart;

    public Long windowEnd;

    public Integer cnt;

    public PVCount() {
    }

    public PVCount(Long windowStart, Long windowEnd, Integer cnt) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
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

    public Integer getCnt() {
        return cnt;
    }

    public void setCnt(Integer cnt) {
        this.cnt = cnt;
    }

    @Override
    public String toString() {
        return "PVCount{" +
                "windowStart=" + new Timestamp(windowStart) +
                ", windowEnd=" + new Timestamp(windowEnd) +
                ", cnt=" + cnt +
                '}';
    }
}
