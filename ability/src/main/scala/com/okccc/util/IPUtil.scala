package com.okccc.util

import java.util

import com.alibaba.fastjson.JSON
import com.alibaba.sec.client.FastIPGeoClient
import com.alibaba.sec.domain.FastGeoConf
import net.ipip.ipdb.City
import org.slf4j.{Logger, LoggerFactory}

/**
 * Author: okccc
 * Date: 2021/7/7 下午3:36
 * Desc: 解析IP地址
 */
object IPUtil {

  private val logger: Logger = LoggerFactory.getLogger(IPUtil.getClass)

  // 简单解析IP地址
  private var city: City = _

  def find(ip: String, language: String = "CN"): Array[String] = {
    try {
      if (city == null) {
        city = new City(ClassLoader.getSystemResource("ipipfree.ipdb").getPath)
      }
      return city.find(ip, language)
    } catch {
      case e: Exception =>
        //            e.printStackTrace();
        logger.error("parse ip failed: " + e)
    }
    null
  }

  // 使用阿里云IP库解析(收费)
  // https://help.aliyun.com/document_detail/170314.html?spm=a2c4g.11186623.6.583.6d6a1d5c159Enh
  private val DATA_FILE_PATH: String = ClassLoader.getSystemResource("geoip.dex").getPath
  private val LICENSE_FILE_PATH: String = ClassLoader.getSystemResource("geoip.lic").getPath

  def getCity(ip: String): String =  {
      // 创建地理配置信息
      val geoConf: FastGeoConf = new FastGeoConf()
      geoConf.setDataFilePath(DATA_FILE_PATH)
      geoConf.setLicenseFilePath(LICENSE_FILE_PATH)
      // 指定只返回需要的字段节约内存,默认是返回全部字段"country", "province", "province_code", "city", "city_code",
      // "county", "county_code","isp", "isp_code", "routes","longitude", "latitude"
      val hashSet: util.HashSet[String] = new util.HashSet[String](util.Arrays.asList("province", "city", "isp"))
      geoConf.setProperties(hashSet)
      geoConf.filterEmptyValue()  // 过滤空值字段
      // 通过单例模式创建对象
      val fastIPGeoClient: FastIPGeoClient = FastIPGeoClient.getSingleton(geoConf)
      // 查询ip
      var result: String = null
      try {
          result = fastIPGeoClient.search(ip)
      } catch {
        case e: Exception =>
//          e.printStackTrace()
          logger.error("error", e)
      }
      result
  }


  def main(args: Array[String]): Unit = {
    val arr: Array[String] = find("49.83.75.64")
    println(arr.mkString(","))  // 中国,江苏,盐城
    println(arr(1))  // 江苏

    val str: String = getCity("49.83.75.64")
    println(str)  // {"province":"江苏省","city":"盐城市","isp":"中国电信"}
    val province: String = JSON.parseObject(str).getString("province")
    val city: String = JSON.parseObject(str).getString("city")
    val isp: String = JSON.parseObject(str).getString("isp")
    println(province + "," + city + "," + isp)  // 江苏省,盐城市,中国电信
  }
}
