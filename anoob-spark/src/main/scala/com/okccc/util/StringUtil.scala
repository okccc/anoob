package com.okccc.util

import java.util

/**
 * @Author: okccc
 * @Date: 2022/12/3 10:51 下午
 * @Desc:
 */
object StringUtil {

  // 将main方法传入的字符串参数解析成键值对,类似hive函数str_to_map
  def strToMap(args: String): util.HashMap[String, String] = { // orc=grubby&ne=moon&hum=sky&ud=ted
    val hashMap = new util.HashMap[String, String]
    // split切割字符串 "," ":" "&" "@" "#" "/"不需要转义, "." "|" "$" "*"需要转义,多个分隔符可以用"|"隔开,但是该转义的还得转义
    for (s <- args.split("&")) {
      val arr: Array[String] = s.split("=")
      hashMap.put(arr(0), arr(1))
    }
    hashMap
  }

}
