package com.okccc.realtime.utils;

import java.net.URLDecoder;
import java.util.HashMap;

/**
 * Author: okccc
 * Date: 2021/10/26 下午6:24
 * Desc: 字符串工具类
 */
public class StringUtil {

    // 将main方法传入的字符串参数解析成键值对,类似hive函数str_to_map
    public static HashMap<String, String> strToMap(String args) {
        // orc=grubby&ne=moon&hum=sky&ud=ted
        HashMap<String, String> hashMap = new HashMap<>();
        for (String s : args.split("&")) {
            String[] arr = s.split("=");
            hashMap.put(arr[0], arr[1]);
        }
        return hashMap;
    }

    // 截断字符串两侧逗号
    public static String trimComma(String str) {
        String result = "";
        String tmp = "";
        if (str.startsWith(",")) {
            tmp = str.substring(1);
        }
        if (tmp.endsWith(",")) {
            result = tmp.substring(0, tmp.length() - 1);
        }
        return result;
    }

    // url解码
    public static String decode(String str) {
        if (str != null && !"".equals(str)) {
            try {
                // java.lang.IllegalArgumentException: URLDecoder: Incomplete trailing escape (%) pattern
                // url解码,%在url中是特殊字符,要先将单独出现的%替换成编码后的%25,再对整个字符串解码
//                return URLDecoder.decode(str, "utf-8");
                return URLDecoder.decode(str.replaceAll("%(?![0-9a-fA-F]{2})", "%25"), "utf-8");
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        return str;
    }

    public static void main(String[] args) {
        HashMap<String, String> res = strToMap("jobName=userLabel&envType=online&topic=thrall&groupId=g01&parallelism=6");
        System.out.println(res);  // {jobName=userLabel, envType=online, groupId=g01, parallelism=6, topic=thrall}
        System.out.println(res.get("topic"));  // thrall
        System.out.println(trimComma(",flink data warehouse,"));  // flink data warehouse
        System.out.println(decode("%22name%22%3D%22grubby%22%26%22age%22%3D%2218%22"));  // "name"="grubby"&"age"="18"
    }
}