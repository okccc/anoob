package utils

import java.util.Date

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

/**
 * @author okccc
 * @date 2020/12/15 4:16 下午
 * @desc 获取日期时间的工具类
 */
object MyDateUtil {

  // 日期时间格式常量
  val DATE_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
  val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  // timestamp -> "yyyy-MM-dd"
  def parseUnixToDate(timestamp: Long): String = {
    new DateTime(new Date(timestamp)).toString(DATE_FORMATTER)
  }

  // timestamp -> "yyyy-MM-dd HH:mm:ss"
  def parseUnixToDateTime(timestamp: Long): String = {
    new DateTime(new Date(timestamp)).toString(DATETIME_FORMATTER)
  }

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

  def main(args: Array[String]): Unit = {
    println(parseUnixToDate(1608011762573L))
    println(parseUnixToDateTime(1608011762573L).substring(0,13))
  }

}
