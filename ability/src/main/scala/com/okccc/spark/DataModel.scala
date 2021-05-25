package com.okccc.spark

import java.util

/**
 * Author: okccc
 * Date: 2021/1/3 3:46 下午
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

/**
 * Author: okccc
 * Date: 2021/1/3 11:10 下午
 * Desc: 使用样例类封装es的index中的source对象
 */
case class MovieInfo(
                      id: Int,
                      name: String,
                      doubanScore: Double,
                      // es是java编写,定义集合类型时要用java.util.*不然读不到数据,后续为了方便转换数据结构可以隐式转换为scala集合
                      // "actorList" : [{"name" : "兽王", "id" : 1}]
                      actorList: util.List[util.Map[String, Any]]
                    )

/**
 * 用户表
 */
case class UserInfo(
                     user_id: Long,
                     username: String,
                     name: String,
                     age: Int,
                     professional: String,
                     city: String,
                     sex: String
                   )

/**
 * 商品表
 */
case class ProductInfo(
                        product_id: Long,
                        product_name: String,
                        extend_info: String
                      )

/**
 * 用户行为表
 */
case class UserVisitAction(
                            date: String,  // 用户点击行为的日期
                            user_id: Long,  // 用户的ID
                            session_id: String,  // Session的ID
                            city_id: Long,  // 城市ID
                            page_id: Long,  // 某个页面的ID
                            action_time: String,  // 点击行为的时间点
                            search_keyword: String,  // 用户搜索的关键词
                            click_category_id: Long,  // 某一个商品品类的ID
                            click_product_id: Long,  // 某一个商品的ID
                            order_category_ids: String,  // 一次订单中所有品类的ID集合
                            order_product_ids: String,  // 一次订单中所有商品的ID集合
                            pay_category_ids: String,  // 一次支付中所有品类的ID集合
                            pay_product_ids: String  // 一次支付中所有商品的ID集合
                          )
