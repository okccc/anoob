package com.okccc.bean;

import java.sql.Timestamp;

/**
 * @Author: okccc
 * @Date: 2023/2/8 11:43
 * @Desc:
 */
public class UserBehavior {

    public String userId;

    public String itemId;

    public String categoryId;

    public String behavior;

    public Long timestamp;

    public UserBehavior() {
    }

    public UserBehavior(String userId, String itemId, String categoryId, String behavior, Long timestamp) {
        this.userId = userId;
        this.itemId = itemId;
        this.categoryId = categoryId;
        this.behavior = behavior;
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "UserBehavior{" +
                "userId='" + userId + '\'' +
                ", itemId='" + itemId + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", behavior='" + behavior + '\'' +
                ", timestamp=" + new Timestamp(timestamp) +
                '}';
    }
}
