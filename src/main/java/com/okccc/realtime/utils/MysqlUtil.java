package com.okccc.realtime.utils;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/10/25 下午4:32
 * Desc: 读写mysql的工具类
 */
public class MysqlUtil {

    private static final Logger logger = LoggerFactory.getLogger(MysqlUtil.class);
    private static DataSource dataSource;
    private static Connection conn;

    // 手动获取数据库连接
    public static void initConnection() throws Exception {
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
        conn = DriverManager.getConnection(url, user, password);
    }

    // 使用druid连接池获取数据库连接
    public static void initDruidConnection() throws Exception {
        if (dataSource == null) {
            // 1.读取配置文件
            Properties prop = new Properties();
            prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("druid.properties"));
            // 2.创建数据源
            dataSource = DruidDataSourceFactory.createDataSource(prop);
        }
        // 3.获取连接对象
        conn =  dataSource.getConnection();
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

    // 批量查询,将结果集封装成T类型对象
    public static <T> List<T> queryList(String sql, Class<T> clazz) throws Exception {
        // 存放结果集的列表
        List<T> list = new ArrayList<>();
        // 声明连接、预编译和结果集
        if (conn == null) {
            initDruidConnection();
//            System.out.println(conn);  // com.mysql.cj.jdbc.ConnectionImpl@548a24a
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 预编译sql
            ps = conn.prepareStatement(sql);
//            System.out.println(ps);  // com.mysql.cj.jdbc.ClientPreparedStatement: select * from user_info
            // 执行查询,返回结果集
            rs = ps.executeQuery();
//            System.out.println(rs);  // com.alibaba.druid.pool.DruidPooledResultSet@5fb759d6
            // 获取结果集的元数据信息
            ResultSetMetaData md = rs.getMetaData();
            // 遍历结果集,循环次数未知用while
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
            // 在控制台打印错误堆栈信息(本地调试)
            e.printStackTrace();
            // 记录日志方便追述历史排查问题(线上代码)
            logger.error(e.getMessage());
        } finally {
            // 释放资源,先别关闭conn,因为关闭连接内存中地址值并不会立即释放,此时conn!=null,下次进来时就不会执行init,也就没有连接可用程序报错
            close(null, ps, rs);
        }
        // 返回结果集
        return list;
    }

    public static void main(String[] args) throws Exception {
        List<JSONObject> jsonObjects = queryList("select * from user_info", JSONObject.class);
        System.out.println(jsonObjects);
    }
}
