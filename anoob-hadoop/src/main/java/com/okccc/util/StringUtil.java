package com.okccc.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;

/**
 * @Author: okccc
 * @Date: 2021/10/26 15:26:24
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

    /**
     * 将main方法传入的字符串参数解析成键值对,类似hive函数str_to_map
     */
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

    /**
     * 截断字符串两侧逗号
     */
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

    /**
     * \r(return)是回车符,光标移到本行开头,后面字符会覆盖前面字符
     * \n(newLine)是换行符,光标移到下一行,Linux系统\n换行,Windows系统\r\n换行
     */
    public static String removeEscape(String str) {
        System.out.println("hello\rworld");
        System.out.println("hello\nworld");
        System.out.println("hello\r\nworld");
        // 匹配单个字符时不要用"\r|\n"应该用"[\r\n]",不然提示 Single character alternation in RegExp
        return str.replaceAll("[\r\n\t]", "");
    }

    /**
     * 字符串加密：MD5/SHA1等加密算法是不可逆的无法解密,AES算法可以用相同的秘钥key将加密后的字符串解密
     */
    public static String encrypt(String inputStr, String key) {
        try {
            // 创建AES加密算法
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            // 创建密钥
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            // 初始化加密算法
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            // 进行加密
            byte[] encryptedBytes = cipher.doFinal(inputStr.getBytes(StandardCharsets.UTF_8));
            // 使用Base64编码
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 字符串解密
     */
    public static String decrypt(String encryptedStr, String key) {
        try {
            // 创建AES解密算法
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            // 创建密钥
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            // 初始化解密算法
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            // 使用Base64解码
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedStr);
            // 进行解密
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            // 将解密后的字节流转换为字符串
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * url解码
     */
    public static String decode(String str) {
        if (str != null && !str.isEmpty()) {
            try {
                // java.lang.IllegalArgumentException: URLDecoder: Incomplete trailing escape (%) pattern
                // url解码,%在url中是特殊字符,要先将单独出现的%替换成编码后的%25,再对整个字符串解码
//                return URLDecoder.decode(str, StandardCharsets.UTF_8);
                return URLDecoder.decode(str.replaceAll("%(?![0-9a-fA-F]{2})", "%25"), StandardCharsets.UTF_8);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        return str;
    }

    /**
     * 解析canal抓取binlog返回的数据
     */
    public static String getCanalData(String columns, JSONObject data, JSONObject jsonObject) {
        StringBuilder sb = new StringBuilder();
        String[] arr = columns.split(";");
        String[] arr1 = arr[0].split(",");
        String[] arr2 = arr[1].split(",");
        for (String s : arr1) {
            // java中的null在hive表无法通过where ${column} is null查询,因为hive底层使用'\N'存储空值,需要手动转换
            // 并且修改hive表信息显式指定空值 alter table ${table} set serdeproperties('serialization.null.format'='\\N');
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

        String log = "{\"@timestamp\":\"31/Jul/2023:10:37:11 +0800\",\"hostname\":\"tx-prod-bigdata-01\",\"ip\":\"120.32.103.75\",\"Method\":\"POST\",\"referer\":\"-\",\"request\":\"POST /home HTTP/1.1\",\"request_body\":\"v=3&e=%7B%22session_id%22%3A1690770146461%2C%22language%22%3A%22Chinese%22%2C%22user_id%22%3A%22c57921f5bd5e4b218d73e42c83e9945e%22%2C%22country%22%3A%22China%20mainland%22%2C%22device_id%22%3A%2220471BE4-0434-4641-9304-4246F41BF96B%22%2C%22version_name%22%3A%2211.0.1%22%2C%22platform%22%3A%22iOS%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%A7%BB%E5%8A%A8%22%2C%22timestamp%22%3A1690771006362%7D&upload_time=1690771030913\",\"status\":\"200\",\"agent\":\"niuwa/11.0.1.2101131825 CFNetwork/1408.0.4 Darwin/22.5.0\"}";
        System.out.println(isJsonFormat(log));  // true
        System.out.println(isJsonFormat(decode(log)));  // false,对整个字符串解码可能会导致数据格式变成非json
        // 只对字符串内部的编码部分进行解码
        JSONObject jsonObject = JSON.parseObject(log);
        String newBody = decode(jsonObject.getString("request_body"));
        jsonObject.put("request_body", newBody);
        // 获取内部字段
        HashMap<String, String> hashMap = strToMap(newBody);
        String version = JSON.parseObject(hashMap.get("e")).getString("version_name");
        System.out.println(version);  // 11.0.1

        String str = "13888888888";
        String key = "mga93UeZ2s#&4Oc3";
        String encryptedStr = encrypt(str, key);
        System.out.println("加密后：" + encryptedStr);
        String decryptedStr = decrypt(encryptedStr, key);
        System.out.println("解密后：" + decryptedStr);
        System.out.println(str.equals(decryptedStr) ? "success" : "fail");
    }
}
