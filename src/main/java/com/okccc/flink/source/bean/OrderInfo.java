package com.okccc.flink.source.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderInfo {
    private Integer id;
    private Long user_id;
    private Double total_amount;
    private Long create_time;

    public static OrderInfo of(Integer id, Long user_id, Double total_amount, Long create_time) {
        return new OrderInfo(id, user_id, total_amount, create_time);
    }
}
