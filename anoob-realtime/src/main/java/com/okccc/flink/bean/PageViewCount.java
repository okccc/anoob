package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/2/8 16:03
 * @Desc:
 */
@Data
@AllArgsConstructor
public class PageViewCount {

    public String url;

    public Long windowStart;

    public Long windowEnd;

    public Integer cnt;

}
