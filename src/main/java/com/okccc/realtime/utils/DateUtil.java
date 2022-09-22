package com.okccc.realtime.utils;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Author: okccc
 * Date: 2021/10/26 下午2:04
 * Desc: 日期时间工具类
 */
@SuppressWarnings("unused")
public class DateUtil {

    // 方法中的局部变量是单线程访问,而类中的成员变量可能会被多线程同时访问,如果涉及修改操作就会存在线程安全问题
    // SimpleDateFormat源码943行format()和1532行parse()都使用了线程不安全的Calendar对象,导致SimpleDateFormat线程不安全
    // 解决方法：1.将sdf定义为局部变量(开销大) 2.加synchronized/lock锁(性能差不适合高并发场景) 3.使用DateTimeFormatter代替(推荐)
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    // 将long类型转换成字符串
    public static String longToStr(Long ts) {
        return sdf.format(new Date(ts));
    }
    // 将字符串转换成long类型
    public static long strToLong(String str) throws ParseException {
        return sdf.parse(str).getTime();
    }

    // 日期时间格式常量
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormat.forPattern("HH");
    private static final DateTimeFormatter DATEHOUR_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");

    // 获取当前日期
    public static String getCurrentDate() {
        return DateTime.now().toString(DATE_FORMATTER);
    }
    // 获取当前时间
    public static String getCurrentTime() {
        return DateTime.now().toString(DATETIME_FORMATTER);
    }

    // Long -> "yyyy-MM-dd"
    public static String parseUnixToDate(Long ts) {
        return new DateTime(new Date(ts)).toString(DATE_FORMATTER);
    }
    // Long -> "HH"
    public static String parseUnixToHour(Long ts) {
        return new DateTime(new Date(ts)).toString(HOUR_FORMATTER);
    }
    // Long -> "yyyy-MM-dd HH"
    public static String parseUnixToDateHour(Long ts) {
        return new DateTime(new Date(ts)).toString(DATEHOUR_FORMATTER);
    }
    // Long -> "yyyy-MM-dd HH:mm:ss"
    public static String parseUnixToDateTime(Long ts) {
        return new DateTime(new Date(ts)).toString(DATETIME_FORMATTER);
    }
    // "yyyy-MM-dd HH:mm:ss" -> Long
    public static Long parseDateTimeToUnix(String str) {
        return DATETIME_FORMATTER.parseDateTime(str).toDate().getTime();
    }
    // "yyyy-MM-dd" -> Date
    public static Date parseDate(String str) {
        return DATE_FORMATTER.parseDateTime(str).toDate();
    }
    // "yyyy-MM-dd HH:mm:ss" -> Date
    public static Date parseDateTime(String str) {
        return DATETIME_FORMATTER.parseDateTime(str).toDate();
    }
    // Date -> "yyyy-MM-dd"
    public static String formatDate(Date date) {
        return new DateTime(date).toString(DATE_FORMATTER);
    }
    // Date -> "yyyy-MM-dd HH:mm:ss"
    public static String formatDateTime(Date date) {
        return new DateTime(date).toString(DATETIME_FORMATTER);
    }

    // 判断一个时间是否在另一个时间之前 "yyyy-MM-dd HH:mm:ss"
    public static Boolean isBefore(String str1, String str2) {
        return DATETIME_FORMATTER.parseDateTime(str1).isBefore(DATETIME_FORMATTER.parseDateTime(str2));
    }

    // 计算日期差值(天) "yyyy-MM-dd"
    public static long dateDiff(String str1, String str2) {
        return (DATE_FORMATTER.parseDateTime(str1).getMillis() - DATE_FORMATTER.parseDateTime(str2).getMillis()) / (1000*60*60*24);
    }

    // 计算时间差值(时/分/秒) "yyyy-MM-dd HH:mm:ss"
    public static long timeDiff(String str1, String str2) {
        return (DATETIME_FORMATTER.parseDateTime(str1).getMillis() - DATETIME_FORMATTER.parseDateTime(str2).getMillis()) / 1000;
    }

    // 获取年月日加小时 "yyyy-MM-dd HH:mm:ss" -> "yyyy-MM-dd_HH"
    public static String getDateHour(String str) {
        String date = str.split(" ")[0];
        String hour = str.split(" ")[1].split(":")[0];
        return date + "_" + hour;
    }

    // 今天日期 + n天/周/月/年
    public static String getPlusDay(Integer n) {
        return DateTime.now().plusDays(n).toString(DATE_FORMATTER);
    }
    public static String getPlusWeek(Integer n) {
        return DateTime.now().plusWeeks(n).toString(DATE_FORMATTER);
    }
    public static String getPlusMonth(Integer n) {
        return DateTime.now().plusMonths(n).toString(DATE_FORMATTER);
    }
    public static String getPlusYear(Integer n) {
        return DateTime.now().plusYears(n).toString(DATE_FORMATTER);
    }

    // 今天日期 - n天/周/月/年
    public static String getMinusDay(Integer n) {
        return DateTime.now().minusDays(n).toString(DATE_FORMATTER);
    }
    public static String getMinusWeek(Integer n) {
        return DateTime.now().minusWeeks(n).toString(DATE_FORMATTER);
    }
    public static String getMinusMonth(Integer n) {
        return DateTime.now().minusMonths(n).toString(DATE_FORMATTER);
    }
    public static String getMinusYear(Integer n) {
        return DateTime.now().minusYears(n).toString(DATE_FORMATTER);
    }

    // 周/月/年的第一天
    public static String getFirstDateOfWeek() {
        return DateTime.now().dayOfWeek().withMinimumValue().toString(DATE_FORMATTER);
    }
    public static String getFirstDateOfMonth() {
        return DateTime.now().dayOfMonth().withMinimumValue().toString(DATE_FORMATTER);
    }
    public static String getFirstDateOfYear() {
        return DateTime.now().dayOfYear().withMinimumValue().toString(DATE_FORMATTER);
    }

    // 周/月/年的最后一天
    public static String getLastDateOfWeek() {
        return DateTime.now().dayOfWeek().withMaximumValue().toString(DATE_FORMATTER);
    }
    public static String getLastDateOfMonth() {
        return DateTime.now().dayOfMonth().withMaximumValue().toString(DATE_FORMATTER);
    }
    public static String getLastDateOfYear() {
        return DateTime.now().dayOfYear().withMaximumValue().toString(DATE_FORMATTER);
    }

    // 获取今天是星期几
    public static String getWeekday() {
        return new SimpleDateFormat("E").format(new Date(System.currentTimeMillis()));
    }

    public static void main(String[] args) {
//        System.out.println(parseDateTimeToUnix("2022-03-07 10:02:20"));
        System.out.println(parseUnixToDateTime(1656490451629L));
    }
}
