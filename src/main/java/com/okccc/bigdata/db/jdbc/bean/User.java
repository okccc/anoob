package com.okccc.bigdata.db.jdbc.bean;

import lombok.*;

import java.sql.Date;

@Data  // lombok可以简化JavaBean开发,会自动实现属性的get&set方法,可通过Structure查看
@NoArgsConstructor  // 无参构造器
@AllArgsConstructor  // 全参构造器,如果不需要全部参数可以手动指定参数实现
@EqualsAndHashCode  // equals和hashCode方法
@ToString  // toString方法
public class User {
    private int id;
    private String name;
    private String email;
    private Date birth;
}
