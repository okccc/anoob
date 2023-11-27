package com.okccc.app.bean;

import lombok.Builder;
import lombok.Data;

/**
 * @Author: okccc
 * @Date: 2023/9/6 18:25:05
 * @Desc: dwd层用户登录实体类
 */
@Data
@Builder
public class UserLogin {

    // 用户ID
    String userId;

    // 省份ID
    String provinceId;

    // 设备ID
    String midId;

    // 品牌
    String brand;

    // 设备型号
    String model;

    // 渠道
    String channel;

    // 版本号
    String version;

    // 操作系统
    String os;

    // 登录时间
    String loginTime;

    // 时间戳
    Long ts;
}
