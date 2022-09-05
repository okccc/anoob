package com.okccc.flink.source.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {
    private Integer id;
    private Long user_id;
    private Integer age;
    private Integer sex;

    public static UserInfo of(Integer id, Long user_id, Integer age, Integer sex) {
        return new UserInfo(id, user_id, age, sex);
    }
}
