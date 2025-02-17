package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/2/9 21:22
 * @Desc:
 */
@Data
@AllArgsConstructor

public class PVCount {

    public Long windowStart;

    public Long windowEnd;

    public Integer cnt;

}
