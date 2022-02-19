package com.okccc.realtime.utils;

import java.io.IOException;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2022/1/29 3:36 下午
 * Desc: 读取配置文件的工具类
 */
public class PropertiesUtil {

    public static Properties load(String fileName) {
        // 创建Properties对象
        Properties prop = new Properties();
        // 加载resource配置文件
        try {
            prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream(fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return prop;
    }

    public static void main(String[] args) {
        Properties prop = PropertiesUtil.load("config.properties");
        System.out.println(prop.getProperty("bootstrap.servers"));
    }
}
