package com.okccc.realtime.utils;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/10/25 下午4:32
 * Desc: 读写mysql的工具类
 */
public class MysqlUtil {

    private static final Logger logger = LoggerFactory.getLogger(MysqlUtil.class);
    private static DataSource dataSource;

    // 手动获取数据库连接
    public static Connection getConnection() throws Exception {
        // 1.读取配置文件
        Properties prop = new Properties();
        prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("config.properties"));
        // 2.获取连接信息
        String driver = prop.getProperty("jdbc.driver");
        String url = prop.getProperty("jdbc.url");
        String user = prop.getProperty("jdbc.user");
        String password = prop.getProperty("jdbc.password");
        // 3.通过反射加载驱动
        Class.forName(driver);
        // 4.创建连接
        return DriverManager.getConnection(url, user, password);
    }

    // 使用druid连接池获取数据库连接
    public static Connection getDruidConnection() throws Exception {
        if (dataSource == null) {
            // 1.读取配置文件
            Properties prop = new Properties();
            prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("druid.properties"));
            // 2.创建数据源
            dataSource = DruidDataSourceFactory.createDataSource(prop);
        }
        // 3.获取连接对象
        return dataSource.getConnection();
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
            conn = getDruidConnection();
//            System.out.println(conn);  // com.mysql.cj.jdbc.ConnectionImpl@548a24a
            // 预编译sql
            ps = conn.prepareStatement(sql);
//            System.out.println(ps);  // com.mysql.cj.jdbc.ClientPreparedStatement: select * from user_info
            // 执行查询,返回结果集
            rs = ps.executeQuery();
//            System.out.println(rs);  // com.alibaba.druid.pool.DruidPooledResultSet@5fb759d6
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
            // 在控制台打印错误堆栈信息(本地调试)
            e.printStackTrace();
            // 记录日志方便追述历史排查问题(线上代码)
            logger.error(e.getMessage());
        } finally {
            // 释放资源
            close(conn, ps, rs);
        }
        // 返回结果集
        return jsonArray;
    }

    public static void main(String[] args) {
        System.out.println(query("select * from user_info111"));
    }
}
