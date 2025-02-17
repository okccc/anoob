package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/2/8 14:32
 * @Desc:
 */
@Data
@AllArgsConstructor
public class LoginData {

    public String userId;
    
    public String eventType;
    
    public Long timestamp;

    public static LoginData of(String userId, String eventType, Long timestamp) {
        return new LoginData(userId, eventType, timestamp);
    }
}
