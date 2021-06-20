package com.okccc.realtime.common

import java.util

/**
 * Author: okccc
 * Date: 2021/1/3 3:46 下午
 * Desc: 项目中用到的数据样例类
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

/**
 * 订单表
 */
case class OrderInfo (
                       id: Long,  //订单编号
                       province_id: Long, //省份id
                       order_status: String,  //订单状态
                       user_id: Long, //用户id
                       final_total_amount: Double,  //总金额
                       benefit_reduce_amount: Double, //优惠金额
                       original_total_amount: Double, //原价金额
                       feight_fee: Double,  //运费
                       expire_time: String, //失效时间
                       create_time: String, //创建时间
                       operate_time: String,  //操作时间
                       var create_date: String, //创建日期
                       var create_hour: String, //创建小时
                       var if_first_order: String, //是否首单

                       var province_name: String,  //地区名
                       var province_area_code: String, //地区编码
                       var province_iso_code: String, //国际地区编码

                       var user_age_group: String, //用户年龄段
                       var user_gender: String //用户性别
                     )


/**
 * 映射用户状态表
 */
case class UserStatus (
                       userId: String,  //用户id
                       ifConsumed: String //是否消费过   0首单   1非首单
                     )

/**
 * 聚合统计表
 */
case class SessionAggStat(
                           taskid: String,  // 当前计算批次的ID
                           session_count: Long,  // 所有Session的总和
                           visit_length_1s_3s_ratio: Double,  // 1-3sSession访问时长占比
                           visit_length_4s_6s_ratio: Double,  // 4-6sSession访问时长占比
                           visit_length_7s_9s_ratio: Double,  // 7-9sSession访问时长占比
                           visit_length_10s_30s_ratio: Double,  // 10-30sSession访问时长占比
                           visit_length_30s_60s_ratio: Double,  // 30-60sSession访问时长占比
                           visit_length_1m_3m_ratio: Double,  // 1-3mSession访问时长占比
                           visit_length_3m_10m_ratio: Double,  // 3-10mSession访问时长占比
                           visit_length_10m_30m_ratio: Double,  // 10-30mSession访问时长占比
                           visit_length_30m_ratio: Double,  // 30mSession访问时长占比
                           step_length_1_3_ratio: Double,  // 1-3步长占比
                           step_length_4_6_ratio: Double,  // 4-6步长占比
                           step_length_7_9_ratio: Double,  // 7-9步长占比
                           step_length_10_30_ratio: Double,  // 10-30步长占比
                           step_length_30_60_ratio: Double,  // 30-60步长占比
                           step_length_60_ratio: Double  // 大于60步长占比
                         )

/**
 * Session随机抽取表
 */
case class SessionRandomExtract(
                                 taskid: String,  // 当前计算批次的ID
                                 sessionid: String,  // 抽取的Session的ID
                                 startTime: String,  // Session的开始时间
                                 searchKeywords: String,  // Session的查询字段
                                 clickCategoryIds: String  // Session点击的类别id集合
                               )

/**
 * Session随机抽取详细表
 **/
case class SessionDetail(
                          taskid: String,  // 当前计算批次的ID
                          userid: Long,  // 用户的ID
                          sessionid: String,  // Session的ID
                          pageid: Long,  // 某个页面的ID
                          actionTime: String,  // 点击行为的时间点
                          searchKeyword: String,  // 用户搜索的关键词
                          clickCategoryId: Long,  // 某一个商品品类的ID
                          clickProductId: Long,  // 某一个商品的ID
                          orderCategoryIds: String,  // 一次订单中所有品类的ID集合
                          orderProductIds: String,  // 一次订单中所有商品的ID集合
                          payCategoryIds: String,  // 一次支付中所有品类的ID集合
                          payProductIds: String  // 一次支付中所有商品的ID集合
                        )

/**
 * 品类Top10表
 */
case class Top10Category(
                          taskid: String,  // 当前计算批次的ID
                          categoryid: Long,  // 类别ID
                          clickCount: Long,  // 点击次数
                          orderCount: Long,  // 订单数量
                          payCount: Long  // 支付数量
                        )

/**
 * Top10 Session
 */
case class Top10Session(
                         taskid: String,  // 当前计算批次的ID
                         categoryid: Long,  // 类别ID
                         sessionid: String,  // 抽取的Session的ID
                         clickCount: Long  // 点击次数
                       )