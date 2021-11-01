package com.okccc.realtime.utils;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.beanutils.BeanUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/10/27 上午11:22
 * Desc: 读写clickhouse的工具类
 */
public class ClickHouseUtil {

    private static Connection conn;

    // 手动获取数据库连接
    public static void initConnection() throws Exception {
        // 1.读取配置文件
        Properties prop = new Properties();
        prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("config.properties"));
        // 2.获取连接信息
        String driver = prop.getProperty("ck.driver");
        String url = prop.getProperty("ck.url");
        String user = prop.getProperty("ck.user");
        String password = prop.getProperty("ck.password");
        // 3.通过反射加载驱动
        Class.forName(driver);
        // 4.创建连接
        conn = DriverManager.getConnection(url, user, password);
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
    public static <T> List<T> queryList(String sql, Class<T> clazz) throws Exception {
        // 存放结果集的列表
        List<T> list = new ArrayList<>();
        // 声明连接、预编译和结果集
        if (conn == null) {
            initConnection();
//            System.out.println(conn);  // ru.yandex.clickhouse.ClickHouseConnectionImpl@46fa7c39
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 预编译sql
            ps = conn.prepareStatement(sql);
//            System.out.println(ps);  // ru.yandex.clickhouse.ClickHousePreparedStatementImpl@7e9131d5
            // 执行查询,返回结果集
            rs = ps.executeQuery();
//            System.out.println(rs);  // ClickHouseResultSet...
            // 获取结果集的元数据信息
            ResultSetMetaData md = rs.getMetaData();
            // 遍历结果集
            while (rs.next()) {
                // 通过反射创建对应封装类型的对象
                T obj = clazz.newInstance();
                for (int i = 0; i < md.getColumnCount(); i++) {
                    // 从元数据获取字段名称,从结果集获取字段值
                    String columnName = md.getColumnName(i + 1);
                    Object columnValue = rs.getObject(i + 1);
                    // 使用java bean工具类给对象的属性赋值
                    BeanUtils.setProperty(obj, columnName, columnValue);
                }
                // 添加到列表
                list.add(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 释放资源
            close(null, ps, rs);
        }
        // 返回结果集
        return list;
    }

    public static void main(String[] args) throws Exception {
        List<JSONObject> jsonObjects = queryList("select * from events", JSONObject.class);
        System.out.println(jsonObjects);
    }
}
