package com.okccc.util;

import java.io.IOException;
import java.util.Properties;

/**
 * @Author: okccc
 * @Date: 2022/1/29 13:13:36
 * @Desc: 读取配置文件的工具类
 */
public class PropertiesUtil {

    public static Properties load(String fileName) {
        // 创建Properties对象
        Properties prop = new Properties();
        try {
            // ClassLoader陷阱：代码打包提交到linux后会读不到resources资源文件导致空指针异常
            // 代码报错不要慌,Caused by: XxxException下面找到自己写的那几行代码,定位问题根源
//            prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream(fileName));
            prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return prop;
    }

    public static void main(String[] args) {
        Properties prop = PropertiesUtil.load("log4j.properties");
        System.out.println(prop.getProperty("log4j.rootLogger"));
    }
}
