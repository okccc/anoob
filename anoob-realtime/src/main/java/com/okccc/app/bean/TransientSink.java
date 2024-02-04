package com.okccc.app.bean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author: okccc
 * @Date: 2023/3/6 15:27:51
 * @Desc: 给类中属性添加注解
 *
 * 实体类中某些字段是为了辅助指标计算而设计,并不需要写入数据库,那么代码如何知道是哪些字段呢？
 * java.beans.Transient是作用于方法的注解,表示编码器会忽略该方法,以此类推可以自定义一个作用于属性的注解
 * java反射可以通过Field.getAnnotation(Class annotationClass)获取注解,写数据库时判断一下目标属性是否包含该注解
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TransientSink {

    boolean value() default true;
}