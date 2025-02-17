package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderInfo {

    private Long orderId;

    private Double totalAmount;

    private Long createTime;

    public static OrderInfo of(Long orderId, Double totalAmount, Long createTime) {
        return new OrderInfo(orderId, totalAmount, createTime);
    }
}
