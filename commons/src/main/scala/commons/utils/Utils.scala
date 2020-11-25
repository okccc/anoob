package commons.utils

import java.util.Date

import net.sf.json.JSONObject
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import scala.collection.mutable

/**
  * 日期时间工具类,使用Joda实现,Java提供的Date会存在线程安全问题
  */
object DateUtils {

  // 日期时间格式常量
  val DATE_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
  val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  // "yyyy-MM-dd" -> Date
  def parseDate(date: String): Date = {
    DATE_FORMATTER.parseDateTime(date).toDate
  }

  // "yyyy-MM-dd HH:mm:ss" -> Date
  def parseTime(time: String): Date = {
    DATETIME_FORMATTER.parseDateTime(time).toDate
  }

  // Date -> "yyyy-MM-dd"
  def formatDate(date: Date): String = {
    new DateTime(date).toString(DATE_FORMATTER)
  }

  // Date -> "yyyy-MM-dd HH:mm:ss"
  def formatTime(date: Date): String = {
    new DateTime(date).toString(DATETIME_FORMATTER)
  }

  // 判断一个时间是否在另一个时间之前 "yyyy-MM-dd HH:mm:ss"
  def before(time1: String, time2: String): Boolean = {
    if(DATETIME_FORMATTER.parseDateTime(time1).isBefore(DATETIME_FORMATTER.parseDateTime(time2))) {
      return true
    }
    false
  }

  // 计算日期差值(天) "yyyy-MM-dd"
  def dateDiff(date1: String, date2: String): Int = {
    (DATE_FORMATTER.parseDateTime(date1).getMillis - DATE_FORMATTER.parseDateTime(date2).getMillis) / (1000*60*60*24) toInt
  }

  // 计算时间差值(时/分/秒) "yyyy-MM-dd HH:mm:ss"
  def timeDiff(time1: String, time2: String): Int = {
    (DATETIME_FORMATTER.parseDateTime(time1).getMillis - DATETIME_FORMATTER.parseDateTime(time2).getMillis) / 1000 toInt
  }

  // 获取年月日加小时 "yyyy-MM-dd HH:mm:ss" -> "yyyy-MM-dd_HH"
  def getDateHour(time: String): String = {
    val date: String = time.split(" ")(0)
    val hour: String = time.split(" ")(1).split(":")(0)
    date + "_" + hour
  }

  // 获取今天日期 "yyyy-MM-dd"
  def getTodayDate: String = {
    DateTime.now().toString(DATE_FORMATTER)
  }

  // 今天日期 + n天/周/月/年
  def getPlusDay(n: Int): String = {
    DateTime.now().plusDays(n).toString(DATE_FORMATTER)
  }

  // 今天 - n天/周/月/年
  def getMinusDay(n: Int): String = {
    DateTime.now().minusDays(n).toString(DATE_FORMATTER)
  }

  def getMinusWeek(n: Int): String = {
    DateTime.now().minusWeeks(n).toString(DATE_FORMATTER)
  }

  def getMinusMonth(n: Int): String = {
    DateTime.now().minusMonths(n).toString(DATE_FORMATTER)
  }

  def getMinusYear(n: Int): String = {
    DateTime.now().minusYears(n).toString(DATE_FORMATTER)
  }

  // 周/月/年第一天
  def getFirstDateOfWeek: String = {
    DateTime.now().dayOfWeek().withMinimumValue().toString(DATE_FORMATTER)
  }

  def getFirstDateOfMonth: String = {
    DateTime.now().dayOfMonth().withMinimumValue().toString(DATE_FORMATTER)
  }

  def getFirstDateOfYear: String = {
    DateTime.now().dayOfYear().withMinimumValue().toString(DATE_FORMATTER)
  }

  // 周/月/年最后一天
  def getLastDateOfWeek: String = {
    DateTime.now().dayOfWeek().withMaximumValue().toString(DATE_FORMATTER)
  }

  def getLastDateOfMonth: String = {
    DateTime.now().dayOfMonth().withMaximumValue().toString(DATE_FORMATTER)
  }

  def getLastDateOfYear: String = {
    DateTime.now().dayOfYear().withMaximumValue().toString(DATE_FORMATTER)
  }

}

/**
  * 数字工具类
  */
object NumberUtils {
  // 对小数四舍五入格式化
  def formatDouble(num: Double, scale: Int): Double = {
    val bd: BigDecimal = BigDecimal(num)
    bd.setScale(scale, BigDecimal.RoundingMode.HALF_UP).doubleValue()
  }

}

/**
  * 参数工具类
  */
object ParamUtils {
  // 从json对象中提取参数
  def getParam(jsonObject: JSONObject, field: String): String = {
    jsonObject.getString(field)
  }
}

/**
  * 字符串工具类
  */
object StringUtils {

  // 判断字符串是否为空
  def isEmpty(str: String): Boolean = {
    str == null || "".equals(str)
  }

  // 截断字符串两侧逗号
  def trimComma(str: String): String = {
    var result = ""
    if(str.startsWith(",")) {
      result = str.substring(1)
    }
    if(str.endsWith(",")) {
      result = str.substring(0, str.length() - 1)
    }
    result
  }

  // 补全两位数字
  def fulfill(str: String): String = {
    if(str.length() == 2) {
      str
    } else {
      "0" + str
    }
  }

  // 从拼接的字符串中提取字段值
  def getFieldFromConcatString(str: String, delimiter: String, field: String): String = {
    try {
      val fields: Array[String] = str.split(delimiter)
      for(i <- fields) {
        if(i.split("=").length == 2) {
          val key: String = i.split("=")(0)
          val value: String = i.split("=")(1)
          if(key.equals(field)) {
            return value
          }
        }
      }
    } catch{
      case e:Exception => e.printStackTrace()
    }
    null
  }

  // 从拼接的字符串中给字段设置值
  def setFieldInConcatString(str: String, delimiter: String, field: String, newFieldValue: String): String = {
    val hm: mutable.HashMap[String, String] = new mutable.HashMap[String, String]()
    val fields: Array[String] = str.split(delimiter)
    for(i <- fields){
      val arr: Array[String] = i.split("=")
      if(arr(0).compareTo(field) == 0)
        hm += (field -> newFieldValue)
      else
        hm += (arr(0) -> arr(1))
    }
    hm.map((item: (String, String)) => item._1 + "=" + item._2).mkString(delimiter)
  }

}

/**
  * 校验工具类
  */
object ValidUtils {

  /**
    * 校验数据中的指定字段,是否在指定范围内
    * @param data 数据
    * @param dataField 数据字段
    * @param parameter 参数
    * @param startParamField 起始参数字段
    * @param endParamField 结束参数字段
    * @return 校验结果
    */
  def between(data: String, dataField: String, parameter: String, startParamField: String, endParamField: String): Boolean = {

    val startParamFieldStr: String = StringUtils.getFieldFromConcatString(parameter, "\\|", startParamField)
    val endParamFieldStr: String = StringUtils.getFieldFromConcatString(parameter, "\\|", endParamField)
    if(startParamFieldStr == null || endParamFieldStr == null) {
      return true
    }

    val startParamFieldValue: Int = startParamFieldStr.toInt
    val endParamFieldValue: Int = endParamFieldStr.toInt

    val dataFieldStr: String = StringUtils.getFieldFromConcatString(data, "\\|", dataField)
    if(dataFieldStr != null) {
      val dataFieldValue: Int = dataFieldStr.toInt
      if(dataFieldValue >= startParamFieldValue && dataFieldValue <= endParamFieldValue) {
        return true
      } else {
        return false
      }
    }
    false
  }

  /**
    * 校验数据中的指定字段,是否有值与参数字段的值相同
    * @param data 数据
    * @param dataField 数据字段
    * @param parameter 参数
    * @param paramField 参数字段
    * @return 校验结果
    */
  def in(data: String, dataField: String, parameter: String, paramField: String):Boolean = {
    val paramFieldValue: String = StringUtils.getFieldFromConcatString(parameter, "\\|", paramField)
    if(paramFieldValue == null) {
      return true
    }
    val paramFieldValueSplited: Array[String] = paramFieldValue.split(",")

    val dataFieldValue: String = StringUtils.getFieldFromConcatString(data, "\\|", dataField)
    if(dataFieldValue != null && dataFieldValue != "-1") {
      val dataFieldValueSplited: Array[String] = dataFieldValue.split(",")

      for(singleDataFieldValue <- dataFieldValueSplited) {
        for(singleParamFieldValue <- paramFieldValueSplited) {
          if(singleDataFieldValue.compareTo(singleParamFieldValue) ==0) {
            return true
          }
        }
      }
    }
    false
  }

  /**
    * 校验数据中的指定字段,是否在指定范围内
    * @param data 数据
    * @param dataField 数据字段
    * @param parameter 参数
    * @param paramField 参数字段
    * @return 校验结果
    */
  def equal(data: String, dataField: String, parameter: String, paramField: String):Boolean = {
    val paramFieldValue: String = StringUtils.getFieldFromConcatString(parameter, "\\|", paramField)
    if(paramFieldValue == null) {
      return true
    }

    val dataFieldValue: String = StringUtils.getFieldFromConcatString(data, "\\|", dataField)
    if(dataFieldValue != null) {
      if(dataFieldValue.compareTo(paramFieldValue) == 0) {
        return true
      }
    }
    false
  }

}