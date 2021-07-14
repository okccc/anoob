package com.okccc.util

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util

import com.alibaba.fastjson.{JSON, JSONArray, JSONException, JSONObject}
import org.apache.spark.sql.Row
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ArrayBuffer

/**
 * Author: okccc
 * Date: 2021/6/23 上午10:38
 * Desc: 日志解析工具类
 */
object LogParseUtil {
  private val logger: Logger = LoggerFactory.getLogger(LogParseUtil.getClass)

  /**
   * 校验日志是否json格式
   */
  def isJsonFormat(str: String): Boolean = {
    try {
      JSON.parse(str)
      true
    } catch {
      case e: JSONException => logger.error("error", e)
        false
    }
  }

  /**
   * url解码
   */
  def decode(str: String): String = {
    if (str != null && !"".equals(str)) {
      try {
        // java.lang.IllegalArgumentException: URLDecoder: Incomplete trailing escape (%) pattern
        // url解码,%在url中是特殊字符,要先将单独出现的%替换成编码后的%25,再对整个字符串解码
//        return URLDecoder.decode(str, "utf-8");
        return URLDecoder.decode(str.replaceAll("%(?![0-9a-fA-F]{2})", "%25"), "utf-8")
      } catch {
        case e: UnsupportedEncodingException => logger.error("error", e)
      }
    }
    str
  }

  /**
   * 将json字符串解析成ArrayBuffer[JSONObject]
   */
  def parseStrToArrayBuffer(str: String): ArrayBuffer[JSONObject] = {
    // 存放json对象的数组
    val arrayBuffer: ArrayBuffer[JSONObject] = new ArrayBuffer[JSONObject]
    // 解析字符串
    if (isJsonFormat(str)) {
      // 最外部的大json串
      val jsonObj: JSONObject = JSON.parseObject(str)
      // 存放公共字段的Map集合
      val common: util.HashMap[String, Object] = new util.HashMap[String, Object]()
      common.put("server_time", jsonObj.getString("@timestamp"))
      common.put("ip", jsonObj.getString("ip"))
      common.put("method", jsonObj.getString("Method"))
      val request_body: String = jsonObj.getString("request_body")
      // v=3&new_log_flag=true&client=234406d28efbc2041bc1f58a501cad17&e=...
      if (request_body.length > 2) {
        val map: util.HashMap[String, String] = StringUtil.strToMap(request_body)
        common.put("v", map.get("v"))
        common.put("client", map.get("client"))
        common.put("upload_time", map.get("upload_time"))
        common.put("checksum", map.get("checksum"))
        // 获取e字段 e=[{},{},{}]
        val e: String = map.get("e")
        // 解码
        var jsonArr: JSONArray = null
        try {
          jsonArr = JSON.parseArray(decode(e))
          for (i <- 0 until jsonArr.size()) {
            // 包含多个小json串对象
            val jsonObj: JSONObject = JSON.parseObject(jsonArr.get(i).toString)
            // 添加common字段拼接成完整json对象
            jsonObj.fluentPutAll(common)
            // 将该json对象添加到数组
            arrayBuffer.append(jsonObj)
          }
        } catch {
          case exception: Exception => exception.printStackTrace()
          // 将解析失败的脏数据放到单独表(TODO)
            println(e)
        }
      }
    }
    arrayBuffer
  }

  /**
   * 将JSONObject解析成Row对象
   */
  def parseJSONObjectToRow(jsonObj: JSONObject): Row = {
    // 获取所有字段
    val server_time: String = jsonObj.getString("server_time")
    val ip: String = jsonObj.getString("ip")
    val method: String = jsonObj.getString("method")
    val v: String = jsonObj.getString("Method")
    val client: String = jsonObj.getString("client")
    val upload_time: String = jsonObj.getString("upload_time")
    val checksum: String = jsonObj.getString("checksum")
    val session_id: String = jsonObj.getString("session_id")
    val user_properties: String = jsonObj.getString("user_properties")
    val language: String = jsonObj.getString("language")
    val event_type: String = jsonObj.getString("event_type")
    val sequence_number: String = jsonObj.getString("sequence_number")
    val user_id: String = jsonObj.getString("user_id")
    val country: String = jsonObj.getString("country")
    val api_properties: String = jsonObj.getString("api_properties")
    val device_id: String = jsonObj.getString("device_id")
    val event_properties: String = jsonObj.getString("event_properties")
    val uuid: String = jsonObj.getString("uuid")
    val device_manufacturer: String = jsonObj.getString("device_manufacturer")
    val version_name: String = jsonObj.getString("version_name")
    val library: String = jsonObj.getString("library")
    val os_name: String = jsonObj.getString("os_name")
    val platform: String = jsonObj.getString("platform")
    val event_id: String = jsonObj.getString("event_id")
    val carrier: String = jsonObj.getString("carrier")
    val timestamp: String = jsonObj.getString("timestamp")
    val groups: String = jsonObj.getString("groups")
    val os_version: String = jsonObj.getString("os_version")
    val device_model: String = jsonObj.getString("device_model")
    // 根据ip解析城市
    val res: String = IPUtil.getCity(ip)
    val province: String = JSON.parseObject(res).getString("province")
    val city: String = JSON.parseObject(res).getString("city")
    // 分区字段
    val dt: String = DateUtil.getCurrentDate.replace("-", "")
    // 封装成Row对象
    Row(client, v, upload_time, checksum, method, ip, session_id, user_properties, language, event_type,
      sequence_number, user_id, country, api_properties, device_id, event_properties, uuid, device_manufacturer,
      version_name, library, os_name, platform, event_id, carrier, timestamp, groups, os_version, device_model,
      province, city, server_time, dt)
  }


  def main(args: Array[String]): Unit = {
    val str01: String = "{ \"@timestamp\": \"22/Jun/2021:19:35:54 +0800\", \"hostname\": \"prod-bigdata-amp01\", \"remote_addr\": \"10.42.251.122\", \"ip\": \"122.236.115.191\", \"Method\": \"POST\", \"referer\": \"-\", \"request\": \"POST /amplitude/ HTTP/1.1\", \"request_body\": \"v=2&client=76382ab7cc9f61be703afadc802bf276&e=%5B%7B%22event_type%22%3A%22Cocos%20EnterSection%20LoadNodes%22%2C%22timestamp%22%3A1624361735816%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%221bcaff77-d30c-45bc-a25b-675235efad51%22%2C%22sequence_number%22%3A48956%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22des%22%3A%22T09%20enter%20game%20scene%22%2C%22scene%22%3A%22Game%22%2C%22timestamp%22%3A1624361735816%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93285%7D%2C%7B%22event_type%22%3A%22Cocos_Res_Render_Cost%22%2C%22timestamp%22%3A1624361735866%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%2264491ff9-42d1-4937-9c15-c77138c08047%22%2C%22sequence_number%22%3A48957%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22time%22%3A401%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93286%7D%2C%7B%22event_type%22%3A%22Sub%20Lesson%20View%20Success%22%2C%22timestamp%22%3A1624361736163%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%223b5a83a7-a319-40b7-af99-d9923a969e4d%22%2C%22sequence_number%22%3A48958%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22Type%22%3A%22newinteraction%22%2C%22LessonType%22%3A%22Compulsory%22%2C%22WeekNum%22%3A%22L2XXW20%22%2C%22ID%22%3A%22L2XX076sub01%22%2C%22Unit%22%3A%22L2XXU11%22%2C%22LoadDuring%22%3A%229%22%2C%22Name%22%3A%22%E4%BA%92%E5%8A%A8%E8%AF%BE%E5%A0%82%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93287%7D%2C%7B%22event_type%22%3A%22Cocos%20Game%20Start%22%2C%22timestamp%22%3A1624361736165%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%22f868012a-6277-4410-9ace-d3a985ed088a%22%2C%22sequence_number%22%3A48959%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22AdjustTime%22%3A0%2C%22Cocos%20Download%20In%20Loading%22%3Afalse%2C%22SubjectVersion%22%3A%221.0%22%2C%22Native%20Download%20in%20loading%22%3Atrue%2C%22LastDuration%22%3A1413%2C%22ABVersion%22%3A%22B%22%2C%22SessionId%22%3A%22d04ec3e8-a55d-4c5a-af12-ea775501ff98%22%2C%22SubLessonId%22%3A%22L2XX0761%22%2C%22Subject%22%3A%22XX%22%2C%22LastNodeName%22%3A%22Cocos%20Engine%20Start%22%2C%22TotalDuration%22%3A9886%2C%22GameId%22%3A%22L2XX0761%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93288%7D%2C%7B%22event_type%22%3A%22onPlayVideo%22%2C%22timestamp%22%3A1624361736358%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%22d6de76e6-7cc9-439e-b717-cd4c62fab22a%22%2C%22sequence_number%22%3A48960%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22msg%22%3A%22L2XX0761_V01%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93289%7D%2C%7B%22event_type%22%3A%22videoPlayerEvent%22%2C%22timestamp%22%3A1624361736359%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%223d240a2d-813d-42a7-b9e7-cdf075c65f71%22%2C%22sequence_number%22%3A48961%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22url%22%3A%22L2XX0761_V01%22%2C%22msg%22%3A%22playVideoWith%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93290%7D%2C%7B%22event_type%22%3A%22videoPlayerEvent%22%2C%22timestamp%22%3A1624361736368%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%22fdd2547f-d473-410e-8151-29c17d76091e%22%2C%22sequence_number%22%3A48962%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22url%22%3A%7B%22_super%22%3Anull%2C%22_name%22%3A%22%22%2C%22_objFlags%22%3A0%2C%22_native%22%3A%22%5C%2F%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.jiliguala.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%2C%22loadMode%22%3A0%2C%22loaded%22%3Atrue%2C%22url%22%3A%22%22%2C%22_callbackTable%22%3A%7B%7D%2C%22_audio%22%3A%22%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.jiliguala.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%7D%2C%22msg%22%3A%22playVideo%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93291%7D%2C%7B%22event_type%22%3A%22videoPlayerEvent%22%2C%22timestamp%22%3A1624361736477%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%222b22bad8-b60d-408d-b2f7-d58edbb33b70%22%2C%22sequence_number%22%3A48963%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22url%22%3A%7B%22_super%22%3Anull%2C%22_name%22%3A%22%22%2C%22_objFlags%22%3A0%2C%22_native%22%3A%22%5C%2F%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.jiliguala.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%2C%22loadMode%22%3A0%2C%22loaded%22%3Atrue%2C%22url%22%3A%22%22%2C%22_callbackTable%22%3A%7B%7D%2C%22_audio%22%3A%22%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.jiliguala.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%7D%2C%22msg%22%3A%22android%20ready-to-play%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93292%7D%5D&upload_time=1624361754400&checksum=03a53b51d7270bed39637a7eacdd3ff8\", \"status\": \"200\", \"bytes\": \"17\", \"agent\": \"okhttp/4.2.2\", \"x_forwarded\": \"122.236.115.191\"}"
    val str02: String = "{ \"@timestamp\": \"22/Jun/2021:19:35:54 +0800\""
    println(isJsonFormat(str01))
    println(isJsonFormat(str02))
  }

}
