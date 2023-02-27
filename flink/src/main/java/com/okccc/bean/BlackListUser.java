package com.okccc.bean;

/**
 * @Author: okccc
 * @Date: 2023/2/20 10:56
 * @Desc:
 */
public class BlackListUser {

    public String userId;

    public String adId;

    public String msg;

    public BlackListUser() {
    }

    public BlackListUser(String userId, String adId, String msg) {
        this.userId = userId;
        this.adId = adId;
        this.msg = msg;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAdId() {
        return adId;
    }

    public void setAdId(String adId) {
        this.adId = adId;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "BlackListUser{" +
                "userId='" + userId + '\'' +
                ", adId='" + adId + '\'' +
                ", msg='" + msg + '\'' +
                '}';
    }
}
