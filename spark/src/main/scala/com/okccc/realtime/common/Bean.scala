package com.okccc.realtime.common

import java.util

/**
 * Desc: 日活信息样例类
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
 * 用户样例类
 */
case class UserInfo (
                     id:String,
                     user_level:String,
                     birthday:String,
                     gender:String,
                     var age_group:String,  // 年龄段
                     var gender_name:String  // 性别
                    )

/**
 * 映射用户状态表的样例类
 */
case class UserStatus(
                       userId:String,  // 用户id
                       ifConsumed:String  // 是否消费过,0首单,1非首单
                     )

/**
 * 商品样例类
 */
case class SpuInfo(
                    id: String,
                    spu_name: String
                  )

/**
 * 商品详情样例类
 */
case class SkuInfo(
                    id: String ,
                    spu_id: String ,
                    price: String ,
                    sku_name: String ,
                    tm_id: String ,
                    category3_id: String ,
                    create_time: String,

                    var category3_name: String,
                    var spu_name: String,
                    var tm_name: String
                  )

/**
 * 商品品牌样例类
 */
case class BaseTrademark (
                          tm_id: String,
                          tm_name: String
                         )

/**
 * 商品分类样例类
 */
case class BaseCategory3 (
                          id: String,
                          name: String,
                          category2_id: String
                         )

/**
 * 订单样例类
 */
case class OrderInfo (
                       id: Long,  // 订单编号
                       province_id: Long, // 省份id
                       order_status: String,  // 订单状态
                       user_id: Long, // 用户id
                       final_total_amount: Double,  // 总金额
                       benefit_reduce_amount: Double, // 优惠金额
                       original_total_amount: Double, // 原价金额
                       feight_fee: Double,  // 运费
                       expire_time: String, // 失效时间
                       create_time: String, // 创建时间
                       operate_time: String,  // 操作时间
                       // 构造器的参数默认是不可变参数,外部无法访问,加上var/val修饰后不可变参数变成成员变量,外部可以访问
                       var create_date: String, // 创建日期
                       var create_hour: String, // 创建小时
                       var if_first_order: String, // 是否首单

                       var province_name: String,  // 地区名
                       var province_area_code: String, // 地区编码
                       var province_iso_code: String, // 国际地区编码

                       var user_age_group: String, // 用户年龄段
                       var user_gender: String // 用户性别
                     )

/**
 * 订单明细样例类
 */
case class OrderDetail (
                        id: Long,
                        order_id: Long,
                        sku_id: Long,
                        order_price: Double,
                        sku_num: Long,
                        sku_name: String,
                        create_time: String,

                        var spu_id: Long,     // 作为维度数据,要关联进来
                        var tm_id: Long,
                        var category3_id: Long,
                        var spu_name: String,
                        var tm_name: String,
                        var category3_name: String
                       )

/**
 * 订单和订单明细样例类
 */
case class OrderWide (
                      var order_detail_id: Long = 0L,
                      var order_id: Long = 0L,
                      var order_status: String = null,
                      var create_time: String = null,
                      var user_id: Long = 0L,
                      var sku_id: Long = 0L,
                      var sku_price: Double = 0D,
                      var sku_num: Long = 0L,
                      var sku_name: String = null,
                      var benefit_reduce_amount: Double = 0D,
                      var feight_fee: Double = 0D,
                      var original_total_amount: Double = 0D,  // 原始总金额 = 明细 Σ 个数*单价
                      var final_total_amount: Double = 0D,  // 实际付款金额 =  原始购买金额-优惠减免金额+运费
                      // 分摊金额
                      var final_detail_amount: Double = 0D,
                      // 首单
                      var if_first_order: String = null,
                      // 主表维度: 省市,年龄段,性别
                      var province_name: String = null,
                      var province_area_code: String = null,
                      var user_age_group: String = null,
                      var user_gender: String = null,
                      var dt: String = null,
                      // 从表维度: spu,品牌,品类
                      var spu_id: Long = 0L,
                      var tm_id: Long = 0L,
                      var category3_id: Long = 0L,
                      var spu_name: String = null,
                      var tm_name: String = null,
                      var category3_name: String = null
                    ) {

  def this(orderInfo: OrderInfo, orderDetail: OrderDetail) {
    this
    mergeOrderInfo(orderInfo)
    mergeOrderDetail(orderDetail)
  }

  def mergeOrderInfo(orderInfo: OrderInfo): Unit = {
    if (orderInfo != null) {
      this.order_id = orderInfo.id
      this.order_status = orderInfo.order_status
      this.create_time = orderInfo.create_time
      this.dt = orderInfo.create_date
      this.benefit_reduce_amount = orderInfo.benefit_reduce_amount
      this.original_total_amount = orderInfo.original_total_amount
      this.feight_fee = orderInfo.feight_fee
      this.final_total_amount = orderInfo.final_total_amount
      this.province_name = orderInfo.province_name
      this.province_area_code = orderInfo.province_area_code
      this.user_age_group = orderInfo.user_age_group
      this.user_gender = orderInfo.user_gender
      this.if_first_order = orderInfo.if_first_order
      this.user_id = orderInfo.user_id
    }
  }

  def mergeOrderDetail(orderDetail: OrderDetail): Unit = {
    if (orderDetail != null) {
      this.order_detail_id = orderDetail.id
      this.sku_id = orderDetail.sku_id
      this.sku_name = orderDetail.sku_name
      this.sku_price = orderDetail.order_price
      this.sku_num = orderDetail.sku_num

      this.spu_id = orderDetail.spu_id
      this.tm_id = orderDetail.tm_id
      this.category3_id = orderDetail.category3_id
      this.spu_name = orderDetail.spu_name
      this.tm_name = orderDetail.tm_name
      this.category3_name = orderDetail.category3_name
    }
  }
}

/**
 * 省份样例类
 */
case class ProvinceInfo (
                          id: String,
                          name: String,
                          area_code: String,
                          iso_code: String
                        )

/**
 * 模拟用户表
 */
case class User (
                 user_id: Long,
                 username: String,
                 name: String,
                 age: Int,
                 professional: String,
                 city: String,
                 sex: String
               )

/**
 * 模拟用户行为表
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
 * 模拟商品表
 */
case class ProductInfo(
                        product_id: Long,
                        product_name: String,
                        extend_info: String
                      )

/**
 * 使用样例类封装es的index中的source对象
 */
case class MovieInfo(
                      id: Int,
                      name: String,
                      doubanScore: Double,
                      // es是java编写,定义集合类型时要用java.util.*不然读不到数据,后续为了方便转换数据结构可以隐式转换为scala集合
                      // "actorList" : [{"name" : "兽王", "id" : 1}]
                      actorList: util.List[util.Map[String, Any]]
                    )