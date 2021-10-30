package com.okccc.realtime.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.sql.*;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/10/27 上午11:22
 * Desc: 读写clickhouse的工具类
 */
public class ClickHouseUtil {

    // 手动获取数据库连接
    public static Connection getConnection() throws Exception {
        // 1.读取配置文件
        Properties prop = new Properties();
        prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("config.properties1"));
        // 2.获取连接信息
        String driver = prop.getProperty("ck.driver");
        String url = prop.getProperty("ck.url");
        String user = prop.getProperty("ck.user");
        String password = prop.getProperty("ck.password");
        // 3.通过反射加载驱动
        Class.forName(driver);
        // 4.创建连接
        return DriverManager.getConnection(url, user, password);
    }

    // 关闭数据库连接
    public static void close(Connection conn, PreparedStatement ps, ResultSet rs) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // 批量查询
    public static JSONArray query(String sql) {
        // 存放结果集的json数组
        JSONArray jsonArray = new JSONArray();
        // 声明数据库连接信息
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 创建连接
            conn = getConnection();
//            System.out.println(conn);  // ru.yandex.clickhouse.ClickHouseConnectionImpl@46fa7c39
            // 预编译sql
            ps = conn.prepareStatement(sql);
//            System.out.println(ps);  // ru.yandex.clickhouse.ClickHousePreparedStatementImpl@7e9131d5
            // 执行查询,返回结果集
            rs = ps.executeQuery();
//            System.out.println(rs);  // ClickHouseResultSet...
            // 获取结果集的元数据信息
            ResultSetMetaData metaData = rs.getMetaData();
            // 遍历结果集
            while (rs.next()) {
                // 将查询数据封装成json对象
                JSONObject jsonObject = new JSONObject();
                for (int i = 0; i < metaData.getColumnCount(); i++) {
                    // 从元数据获取字段名称,从结果集获取字段值
                    jsonObject.put(metaData.getColumnName(i + 1), rs.getObject(i + 1));
                }
                // 添加到json数组
                jsonArray.add(jsonObject);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 释放资源
            close(conn, ps, rs);
        }
        // 返回结果集
        return jsonArray;
    }

    public static void main(String[] args) {
        System.out.println(query("select * from events"));
    }
}
