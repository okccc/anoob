package com.okccc.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

import java.net.URLDecoder;
import java.util.HashMap;

/**
 * @Author: okccc
 * @Date: 2021/10/26 下午6:24
 * @Desc: 字符串工具类
 */
public class StringUtil {

    /**
     * 校验日志是否json格式
     */
    public static boolean isJsonFormat(String str) {
        try {
            JSON.parse(str);
            return true;
        } catch (JSONException e) {
//            e.printStackTrace();
            return false;
        }
    }

    // 将main方法传入的字符串参数解析成键值对,类似hive函数str_to_map
    public static HashMap<String, String> strToMap(String args) {
        // orc=grubby&ne=moon&hum=sky&ud=ted
        HashMap<String, String> hashMap = new HashMap<>();
        // split切割字符串 "," ":" "&" "@" "#" "/"不需要转义, "." "|" "$" "*"需要转义,多个分隔符可以用"|"隔开,但是该转义的还得转义
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

    // \r(return)是回车符,光标移到本行开头,后面字符会覆盖前面字符
    // \n(newLine)是换行符,光标移到下一行,Linux系统\n换行,Windows系统\r\n换行
    public static String removeEscape(String str) {
        System.out.println("hello\rworld");
        System.out.println("hello\nworld");
        System.out.println("hello\r\nworld");
        // 匹配单个字符时不要用"\r|\n"应该用"[\r\n]",不然提示 Single character alternation in RegExp
        return str.replaceAll("[\r\n\t]", "");
    }

    // url解码
    public static String decode(String str) {
        if (str != null && str.length() > 0) {
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

    // 解析canal抓取binlog返回的数据
    public static String getCanalData(String columns, JSONObject data, JSONObject jsonObject) {
        StringBuilder sb = new StringBuilder();
        String[] arr = columns.split(";");
        String[] arr1 = arr[0].split(",");
        String[] arr2 = arr[1].split(",");
        for (String s : arr1) {
            // java中的null在hive表无法通过where ${column} is null查询,因为hive底层使用'\N'存储空值,需要手动转换
            // 并且修改hive表信息显式指定空值 alter table ${table} set serdeproperties('serialization.null.format'='\N');
            String value = data.getString(s);
            sb.append(value == null ? "\\N" : value.replaceAll("\n", "")).append("\001");
        }
        for (int i = 0; i < arr2.length; i++) {
            if (i == arr2.length - 1) {
                sb.append(jsonObject.getString(arr2[i]));
            } else {
                sb.append(jsonObject.getString(arr2[i])).append("\001");
            }
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        HashMap<String, String> res = strToMap("jobName=userLabel&envType=online&topic=thrall&groupId=g01&parallelism=6");
        System.out.println(res);  // {jobName=userLabel, envType=online, groupId=g01, parallelism=6, topic=thrall}
        System.out.println(res.get("topic"));  // thrall
        System.out.println(trimComma(",flink data warehouse,"));  // flink data warehouse
        System.out.println(removeEscape("申请：20230515(001号\r菲菲)\n" +
                "审批：20230519(002号\t露露)"));
        System.out.println(decode("%22name%22%3D%22grubby%22%26%22age%22%3D%2218%22"));  // "name"="grubby"&"age"="18"
    }
}
