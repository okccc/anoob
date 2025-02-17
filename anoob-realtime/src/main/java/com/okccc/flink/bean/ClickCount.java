package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/2/20 10:54
 * @Desc:
 */
@Data
@AllArgsConstructor
public class ClickCount {

    public Long windowStart;

    public Long windowEnd;

    public String province;

    public String city;

    public Integer cnt;
}
