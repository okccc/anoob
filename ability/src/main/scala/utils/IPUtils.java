package utils;

import net.ipip.ipdb.City;
import java.util.Arrays;

/**
 * Author: okccc
 * Date: 2021/2/2 2:12 下午
 * Desc: 根据IP地址查询城市
 */
public class IPUtils {
    private static City city;

    public static String[] find(String ip, String language) {
        try {
            if (city == null) {
                city = new City(IPUtils.class.getResource("/").getPath() + "ipipfree.ipdb");
            }
            return city.find(ip, language);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        String[] cns = find("49.83.75.64", "CN");
        System.out.println(Arrays.toString(cns));  // [中国, 江苏, 盐城]
    }
}
