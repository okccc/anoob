package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/8 16:00
 * @Desc:
 */
public class ItemViewCount {

    public String itemId;

    public Long windowStart;

    public Long windowEnd;

    public Integer cnt;

    public ItemViewCount(String itemId, Long windowStart, Long windowEnd, Integer cnt) {
        this.itemId = itemId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.cnt = cnt;
    }

    public ItemViewCount() {
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
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
        return "ItemViewCount{" +
                "itemId='" + itemId + '\'' +
                ", windowStart=" + new Timestamp(windowStart) +
                ", windowEnd=" + new Timestamp(windowEnd) +
                ", cnt=" + cnt +
                '}';
    }
}
