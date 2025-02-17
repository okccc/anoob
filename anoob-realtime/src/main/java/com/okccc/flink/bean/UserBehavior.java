package com.okccc.flink.bean;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/2/8 11:43
 * @Desc:
 */
@Data
@AllArgsConstructor
public class UserBehavior {

    public String userId;

    public String itemId;

    public String categoryId;

    public String behavior;

    public Long ts;
}
