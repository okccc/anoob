package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/2/14 15:00
 * @Desc:
 */
@Data
@AllArgsConstructor
public class UVCount {

    public Long windowStart;

    public Long windowEnd;

    public Integer cnt;

}
