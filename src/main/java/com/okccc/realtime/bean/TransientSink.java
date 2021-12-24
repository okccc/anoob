package com.okccc.realtime.bean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Author: okccc
 * Date: 2021/12/23 10:31 上午
 * Desc: java bean某个属性不需要保存到表里,就可以添加该注解标记,往数据库插入数据时判断一下属性是否包含该注解
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TransientSink {
}
