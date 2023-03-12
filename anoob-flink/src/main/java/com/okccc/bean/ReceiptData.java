package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/8 14:32
 * @Desc:
 */
public class ReceiptData {

    public String receiptId;

    public String eventType;

    public Long timestamp;

    public ReceiptData() {
    }

    public ReceiptData(String receiptId, String eventType, Long timestamp) {
        this.receiptId = receiptId;
        this.eventType = eventType;
        this.timestamp = timestamp;
    }

    public String getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(String receiptId) {
        this.receiptId = receiptId;
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
        return "ReceiptLog{" +
                "receiptId='" + receiptId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", timestamp=" + new Timestamp(timestamp) +
                '}';
    }
}
