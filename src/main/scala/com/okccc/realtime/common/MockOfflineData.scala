package com.okccc.realtime.common

import java.util.UUID

import com.okccc.util.DateUtil
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
 * Author: okccc
 * Date: 2021/5/28 下午1:52
 * Desc: 模拟生成离线数据写入hive
 */
object MockOfflineData {

  // 表名常量
  private val USER_INFO_TABLE: String = "user_info"
  private val PRODUCT_INFO_TABLE: String = "product_info"
  private val USER_VISIT_ACTION_TABLE: String = "user_visit_action"

  def main(args: Array[String]): Unit = {
    // 创建Spark配置信息
    val conf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("MockData")
    // 创建SparkSql客户端
    val spark: SparkSession = SparkSession.builder().config(conf).enableHiveSupport().getOrCreate()
    // 获取Spark上下文对象
    val sc: SparkContext = spark.sparkContext

    // 调用方法生成模拟数据
    val userInfoData: Array[User] = this.mockUserInfo()
    val productInfoData: Array[ProductInfo] = this.mockProductInfo()
    val userVisitActionData: Array[UserVisitAction] = this.mockUserVisitActionData()
    // 将模拟数据转换为RDD
    val userInfoRDD: RDD[User] = sc.makeRDD(userInfoData)
    val productInfoRDD: RDD[ProductInfo] = sc.makeRDD(productInfoData)
    val userVisitActionRDD: RDD[UserVisitAction] = sc.makeRDD(userVisitActionData)

    // 导入SparkSql的隐式转换支持
    import spark.implicits._
    // 将RDD转换为DataFrame并保存到hive表
    val userInfoDF: DataFrame = userInfoRDD.toDF()
    insertHive(spark, USER_INFO_TABLE, userInfoDF)
    val productInfoDF: DataFrame = productInfoRDD.toDF()
    insertHive(spark, PRODUCT_INFO_TABLE, productInfoDF)
    val userVisitActionDF: DataFrame = userVisitActionRDD.toDF()
    insertHive(spark, USER_VISIT_ACTION_TABLE, userVisitActionDF)

    // 关闭SparkSession
    spark.close
  }

  /**
   * 模拟用户数据
   */
  private def mockUserInfo(): Array[User] = {
    // 创建存放用户数据的可变数组
    val rows: ArrayBuffer[User] = ArrayBuffer[User]()
    // 创建随机数生成器对象
    val random: Random = new Random()
    // 用户性别
    val sexes: Array[String] = Array("male", "female")
    // 随机产生100个用户的个人信息
    for (i <- 1 to 100) {
      val userid: Int = i
      val username: String = "user" + i
      val name: String = "name" + i
      val age: Int = random.nextInt(60)
      val professional: String = "professional" + random.nextInt(100)
      val city: String = "city" + random.nextInt(100)
      val sex: String = sexes(random.nextInt(2))
      // 往数组添加数据
      rows += User(userid, username, name, age, professional, city, sex)
    }
    // 该数组是用来创建RDD,因为RDD是不可变的,所以要将ArrayBuffer转换成Array
    rows.toArray
  }

  /**
   * 模拟商品数据
   */
  private def mockProductInfo(): Array[ProductInfo] = {
    // 创建存放产品数据的可变数组
    val rows: ArrayBuffer[ProductInfo] = ArrayBuffer[ProductInfo]()
    // 创建随机数生成器对象
    val random: Random = new Random()
    // 产品状态
    val productStatus: Array[Int] = Array(0, 1)
    // 随机产生100个产品信息
    for (i <- 1 to 100) {
      val productId: Int = i
      val productName: String = "product" + i
      val extendInfo: String = "{\"product_status\": " + productStatus(random.nextInt(2)) + "}"
      // 往数组添加数据
      rows += ProductInfo(productId, productName, extendInfo)
    }
    // 转换成Array
    rows.toArray
  }

  /**
   * 模拟用户行为数据
   */
  private def mockUserVisitActionData(): Array[UserVisitAction] = {
    // 创建存放用户行为数据的可变数组
    val rows: ArrayBuffer[UserVisitAction] = ArrayBuffer[UserVisitAction]()
    // 创建随机数生成器对象
    val random: Random = new Random()
    // 四个用户行为：搜索/点击/下单/支付
    val actions: Array[String] = Array("search", "click", "order", "pay")
    // 搜索关键字
    val searchKeywords: Array[String] = Array("华为手机", "联想笔记本", "小龙虾", "卫生纸", "吸尘器", "Lamer", "机器学习", "苹果", "洗面奶", "保温杯")
    // 获取当天日期
    val date: String = DateUtil.getCurrentDate
    // 生成100个用户(有重复)
    for (i <- 1 to 100) {
      val userid: Int = random.nextInt(100)
      // 每个用户产生10个session
      for (j <- 1 to 10) {
        // 不可变的,全局的,独一无二的128bit长度的标识符,用于标识一个session,体现一次会话产生的sessionId是独一无二的
        val sessionid: String = UUID.randomUUID().toString.replace("-", "")
        // 在yyyy-MM-dd后面添加一个随机的小时时间(0-23)
        val baseActionTime: String = date + " " + random.nextInt(23)
        // 每个(userid + sessionid)生成0-100条用户访问数据
        for (k <- 0 to random.nextInt(100)) {
          val cityid: Long = random.nextInt(10).toLong
          val pageid: Int = random.nextInt(10)
          // 在yyyy-MM-dd HH后面添加一个随机的分钟时间和秒时间
          val actionTime: String = baseActionTime + ":" + random.nextInt(59).toString + ":" + random.nextInt(59).toString
          var searchKeyword: String = null
          var clickCategoryId: Long = -1L
          var clickProductId: Long = -1L
          var orderCategoryIds: String = null
          var orderProductIds: String = null
          var payCategoryIds: String = null
          var payProductIds: String = null
          // 随机生成用户在当前session中的行为,并对action做模式匹配决定相关字段值
          val action: String = actions(random.nextInt(4))
          action match {
            case "search" => searchKeyword = searchKeywords(random.nextInt(10))
            case "click" => clickCategoryId = random.nextInt(100).toLong
              clickProductId = random.nextInt(100).toLong
            case "order" => orderCategoryIds = random.nextInt(100).toString
              orderProductIds = random.nextInt(100).toString
            case "pay" => payCategoryIds = random.nextInt(100).toString
              payProductIds = random.nextInt(100).toString
          }
          // 往数组添加数据
          rows += UserVisitAction(date, userid, sessionid, cityid, pageid, actionTime, searchKeyword, clickCategoryId,
            clickProductId, orderCategoryIds, orderProductIds, payCategoryIds, payProductIds)
        }
      }
    }
    // 转换成Array
    rows.toArray
  }

  /**
   * 将DataFrame写入hive表
   */
  private def insertHive(spark: SparkSession, table: String, df: DataFrame): Unit = {
    spark.sql("DROP TABLE IF EXISTS " + table)
    // 将DataFrame保存为hive格式的表
    df.write.saveAsTable(table)
    // 将DataFrame插入已存在的hive表
    df.write.insertInto(table)
  }

}
