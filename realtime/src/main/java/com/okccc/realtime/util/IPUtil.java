package com.okccc.realtime.util;

import com.alibaba.sec.client.FastIPGeoClient;
import com.alibaba.sec.domain.FastGeoConf;
import net.ipip.ipdb.City;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Author: okccc
 * Date: 2021/10/28 上午10:20
 * Desc: 解析IP地址
 */
public class IPUtil {
    
    // 简单解析IP地址
    private static City city = null;

    public static String[] find(String ip) {
        try {
            if (city == null) {
                city = new City(ClassLoader.getSystemResource("ipipfree.ipdb").getPath());
            }
            return city.find(ip, "CN");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 使用阿里云IP库解析(收费)
    // https://help.aliyun.com/document_detail/170314.html?spm=a2c4g.11186623.6.583.6d6a1d5c159Enh
    private static final String DATA_FILE_PATH = ClassLoader.getSystemResource("geoip.dex").getPath();
    private static final String LICENSE_FILE_PATH = ClassLoader.getSystemResource("geoip.lic").getPath();

    public static String getCity(String ip) throws Exception {
        // 创建地理配置信息
        FastGeoConf geoConf = new FastGeoConf();
        geoConf.setDataFilePath(DATA_FILE_PATH);
        geoConf.setLicenseFilePath(LICENSE_FILE_PATH);
        // 指定需要的字段节约内存,默认是返回全部字段"country", "province", "province_code", "city", "city_code",
        // "county", "county_code", "isp", "isp_code", "routes", "longitude", "latitude"
        HashSet<String> hashSet = new HashSet<>(Arrays.asList("province", "city", "isp"));
        geoConf.setProperties(hashSet);
        geoConf.filterEmptyValue();  // 过滤空值字段
        // 通过单例模式创建对象
        FastIPGeoClient fastIPGeoClient = FastIPGeoClient.getSingleton(geoConf);
        // 查询ip
        String result = null;
        try {
            result = fastIPGeoClient.search(ip);
        } catch (Exception e){
            e.printStackTrace();
        }
        return result;
    }

    // 使用埃文科技IP库解析(收费)
    public static String getIp(String ip) throws IOException {
        String apiUrl = "https://api.ipplus360.com/ip/geo/v1/city/?key=g8RubXJ8JHbshjG8pj17YRRgypE21LbJi9aTNZjCfWWYN8E5xOvtSMfbmMN5ZRiV&ip=" + ip;
        HttpURLConnection conn = null;
        BufferedReader br = null;
        String line;
        try {
            URL url = new URL(apiUrl);
            // 根据URL生成HttpURLConnection
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            // 建立TCP连接
            conn.connect();
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                br.close();
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        System.out.println(Arrays.toString(find("49.83.75.64")));  // [中国, 江苏, 盐城]
        System.out.println(getCity("49.83.75.64"));  // {"province":"江苏省","city":"盐城市","isp":"中国电信"}
        System.out.println(getIp("49.83.75.64"));  // {"country":"中国","isp":"中国电信","prov":"江苏省","city":"盐城市"}
    }
}
