package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/2/8 14:52
 * @Desc:
 */
@Data
@AllArgsConstructor
public class ClickData {

    public String userId;

    public String adId;

    public String province;

    public String city;

    public Long timestamp;
}
