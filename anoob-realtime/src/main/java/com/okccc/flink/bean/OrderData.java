package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: okccc
 * @Date: 2023/2/8 11:46
 * @Desc:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderData {

    public String orderId;
    
    public String eventType;

    public String receiptId;
    
    public Long timestamp;

    public static OrderData of(String orderId, String eventType, String receiptId, Long timestamp) {
        return new OrderData(orderId, eventType, receiptId, timestamp);
    }
}
