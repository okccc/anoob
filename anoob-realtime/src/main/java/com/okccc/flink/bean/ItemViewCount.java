package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: okccc
 * @Date: 2023/2/8 16:00
 * @Desc:
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemViewCount {

    public String itemId;

    public Long windowStart;

    public Long windowEnd;

    public Integer cnt;
}
