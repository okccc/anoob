package com.okccc.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.sec.client.FastIPGeoClient;
import com.alibaba.sec.domain.FastGeoConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Author: okccc
 * Date: 2021/2/2 2:12 下午
 * Desc: 根据IP地址查询城市
 */
public class IPUtil {

    // 使用指定类初始化日志对象,控制台输出日志时会显示日志信息所属的类
    private static final Logger logger = LoggerFactory.getLogger(IPUtil.class);

    // 简单解析IP地址
//    private static City city;
//    public static String[] find(String ip, String language) {
//        try {
//            if (city == null) {
//                city = new City(IPUtil.class.getResource("/").getPath() + "ipipfree.ipdb");
//            }
//            return city.find(ip, language);
//        } catch (Exception e) {
////            e.printStackTrace();
//            logger.error("parse ip failed: " + e);
//        }
//        return null;
//    }

    // 使用阿里云IP库解析
    private static final String DATA_FILE_PATH = "ability/src/main/resources/geoip.dex";
    private static final String LICENSE_FILE_PATH = "ability/src/main/resources/geoip.lic";
    public static String getCity(String ip) throws Exception {
        // 创建地理配置信息
        FastGeoConf geoConf = new FastGeoConf();
        geoConf.setDataFilePath(DATA_FILE_PATH);
        geoConf.setLicenseFilePath(LICENSE_FILE_PATH);
        // 指定只返回需要的字段节约内存,默认是返回全部字段"country", "province", "province_code", "city", "city_code",
        // "county", "county_code","isp", "isp_code", "routes","longitude", "latitude"
        HashSet<String> hashSet = new HashSet<>(Arrays.asList("province", "city", "isp"));
        geoConf.setProperties(hashSet);
        geoConf.filterEmptyValue();  // 过滤空值字段
        // 通过单例模式创建对象
        FastIPGeoClient fastIPGeoClient = FastIPGeoClient.getSingleton(geoConf);
        // 查询ip
        String result;
        try {
            result = fastIPGeoClient.search(ip);
        } catch (Exception e) {
            logger.error("error", e);
            result = null;
        }
        return result;
    }


    public static void main(String[] args) throws Exception {
//        String[] cns = find("49.83.75.64", "CN");
//        System.out.println(Arrays.toString(cns));  // [中国, 江苏, 盐城]

        String res = getCity("49.83.75.64");
        String province = JSON.parseObject(res).getString("province");
        String city = JSON.parseObject(res).getString("city");
        String isp = JSON.parseObject(res).getString("isp");
        System.out.println(province + "," + city + "," + isp);  // 江苏省,盐城市,中国电信
    }
}
