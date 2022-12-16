package com.okccc.pojo;

import lombok.*;

import java.sql.Date;

/**
 * @Author: okccc
 * @Date: 2022/12/12 15:00
 * @Desc: 订单类
 */
@Data  // lombok可以简化JavaBean开发,会自动实现属性的get&set方法,可通过Structure查看
@NoArgsConstructor  // 无参构造器
@AllArgsConstructor  // 全参构造器,如果不需要全部参数可以手动指定参数实现
@EqualsAndHashCode  // equals和hashCode方法
@ToString  // toString方法
public class Order {

    private int orderId;

    private String orderName;

    private Date orderDate;
}
