package com.okccc.realtime.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/10/22 下午3:17
 * Desc: 使用phoenix读写hbase的工具类
 */
public class HbaseUtil {

    public static final Logger logger = LoggerFactory.getLogger(HbaseUtil.class);

    // 获取数据库连接
    public static Connection getConnection() throws Exception {
        // 1.读取配置文件
        Properties prop = new Properties();
        prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("config.properties"));
        // 2.获取连接信息
        String driver = prop.getProperty("hbase.driver");
        String url = prop.getProperty("hbase.url");
        // 3.通过反射加载驱动
        Class.forName(driver);
        // 4.创建连接
        return DriverManager.getConnection(url);
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
//            System.out.println(conn);  // org.apache.phoenix.jdbc.PhoenixConnection@1bd81830
            // 预编译sql
            ps = conn.prepareStatement(sql);
//            System.out.println(ps);  // select * from realtime.dim_base_trademark
            // 执行查询,返回结果集
            rs = ps.executeQuery();
//            System.out.println(rs);  // org.apache.phoenix.jdbc.PhoenixResultSet@392a04e7
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
            logger.error(e.getMessage());
        } finally {
            // 释放资源
            close(conn, ps, rs);
        }
        // 返回结果集
        return jsonArray;
    }

    // 更新操作
    public static void upsert(String sql) {
        // 声明数据库连接信息
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            // 创建连接
            conn = getConnection();
            // 预编译sql
            ps = conn.prepareStatement(sql);
            // 执行更新,返回影响行数
            ps.executeUpdate();
            // 查看Connection接口的实现类PhoenixConnection源码发现属性isAutoCommit=false,需要手动提交事务
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        } finally {
            // 释放资源
            close(conn, ps, null);
        }
    }

    public static void main(String[] args) {
        upsert("upsert into realtime.dim_base_trademark values('13', '小米')");
        System.out.println(query("select * from realtime.dim_base_trademark"));
    }
}
