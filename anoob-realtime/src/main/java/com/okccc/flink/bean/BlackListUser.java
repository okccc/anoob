package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/2/20 10:56
 * @Desc:
 */
@Data
@AllArgsConstructor
public class BlackListUser {

    public String userId;

    public String adId;

    public String msg;

}
