package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/2/8 15:02
 * @Desc:
 */
@Data
@AllArgsConstructor
public class ApacheLog {

    public String ip;

    public String userId;

    public String method;

    public String url;

    public Long timestamp;
}
