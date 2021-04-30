package com.okccc.bean

/**
 * Author: okccc
 * @date 2021/1/3 3:46 下午
 * Desc: 封装日活数据的样例类
 */
case class DauInfo(
                    mid: String,  // 设备id
                    uid: String,  // 用户id
                    ar: String,  // 地区
                    ch: String,  // 渠道
                    vc: String,  // 版本
                    var dt: String,  // 日期
                    var hr: String,  // 小时
                    var mi: String,  // 分钟
                    ts: Long  // 时间戳
                  )
