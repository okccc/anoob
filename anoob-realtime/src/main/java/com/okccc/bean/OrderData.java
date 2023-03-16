package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/8 11:46
 * @Desc:
 */
public class OrderData {

    public String orderId;
    
    public String eventType;

    public String receiptId;
    
    public Long timestamp;

    public OrderData() {
    }

    public OrderData(String orderId, String eventType, String receiptId, Long timestamp) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.receiptId = receiptId;
        this.timestamp = timestamp;
    }

    public static OrderData of(String orderId, String eventType, String receiptId, Long timestamp) {
        return new OrderData(orderId, eventType, receiptId, timestamp);
    }

    @Override
    public String toString() {
        return "OrderLog{" +
                "orderId='" + orderId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", receiptId='" + receiptId + '\'' +
                ", timestamp=" + new Timestamp(timestamp) +
                '}';
    }
}
