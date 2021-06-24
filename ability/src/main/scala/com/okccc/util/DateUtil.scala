package com.okccc.util

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

/**
 * Author: okccc
 * Date: 2020/12/15 4:16 下午
 * Desc: 日期时间工具类,使用joda实现,java提供的Date存在线程安全问题
 */
object DateUtil {

  // 日期时间格式常量
  val DATE_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
  val HOUR_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("HH")
  val DATE_HOUR_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH")
  val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")

  // 获取当前日期
  def getCurrentDate: String = {
    DateTime.now().toString(DATE_FORMATTER)
  }
  // 获取当前时间
  def getCurrentTime: String = {
    DateTime.now().toString(DATETIME_FORMATTER)
  }

  // Long类型时间戳转换成字符串
  // timestamp -> "yyyy-MM-dd"
  def parseUnixToDate(timestamp: Long): String = {
    new DateTime(new Date(timestamp)).toString(DATE_FORMATTER)
  }
  // timestamp -> "HH"
  def parseUnixToHour(timestamp: Long): String = {
    new DateTime(new Date(timestamp)).toString(HOUR_FORMATTER)
  }
  // timestamp -> "yyyy-MM-dd HH"
  def parseUnixToDateHour(timestamp: Long): String = {
    new DateTime(new Date(timestamp)).toString(DATE_HOUR_FORMATTER)
  }
  // timestamp -> "yyyy-MM-dd HH:mm:ss"
  def parseUnixToDateTime(timestamp: Long): String = {
    new DateTime(new Date(timestamp)).toString(DATETIME_FORMATTER)
  }

  // 字符串和日期
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
  def isBefore(time1: String, time2: String): Boolean = {
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

  // 今天日期 + n天/周/月/年
  def getPlusDay(n: Int): String = {
    DateTime.now().plusDays(n).toString(DATE_FORMATTER)
  }
  // 今天日期 - n天/周/月/年
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

  // 周/月/年的第一天
  def getFirstDateOfWeek: String = {
    DateTime.now().dayOfWeek().withMinimumValue().toString(DATE_FORMATTER)
  }
  def getFirstDateOfMonth: String = {
    DateTime.now().dayOfMonth().withMinimumValue().toString(DATE_FORMATTER)
  }
  def getFirstDateOfYear: String = {
    DateTime.now().dayOfYear().withMinimumValue().toString(DATE_FORMATTER)
  }

  // 周/月/年的最后一天
  def getLastDateOfWeek: String = {
    DateTime.now().dayOfWeek().withMaximumValue().toString(DATE_FORMATTER)
  }
  def getLastDateOfMonth: String = {
    DateTime.now().dayOfMonth().withMaximumValue().toString(DATE_FORMATTER)
  }
  def getLastDateOfYear: String = {
    DateTime.now().dayOfYear().withMaximumValue().toString(DATE_FORMATTER)
  }

  // 获取今天是星期几
  def getWeekday: String = {
    new SimpleDateFormat("E").format(new Date(System.currentTimeMillis()))
  }

  def main(args: Array[String]): Unit = {
//    println(new Timestamp(1511658661000L))
//    println(getCurrentTime)
//    println(getWeekday)
    println(parseUnixToDateTime(1624291203929L))
  }
}