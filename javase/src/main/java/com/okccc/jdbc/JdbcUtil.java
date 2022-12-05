package com.okccc.jdbc;

import com.alibaba.druid.pool.DruidDataSourceFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Properties;

public class JdbcUtil {
    public static void main(String[] args) throws Exception {
        // 提供数据库连接和关闭操作的工具类
        getConnection();
        getDruidConnection();
    }

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
        // 4.获取连接
        Connection conn = DriverManager.getConnection(url, user, password);
        System.out.println(conn);  // com.mysql.jdbc.JDBC4Connection@396a51ab
        return conn;
    }

//    // 使用c3p0数据库连接池(速度慢,但很稳定)
//    private static ComboPooledDataSource cpds = new ComboPooledDataSource("mysql01");
//    public static Connection getC3P0Connection() throws SQLException {
//        // 获取连接
//        Connection conn = cpds.getConnection();
//        System.out.println(conn);  // com.mchange.v2.c3p0.impl.NewProxyConnection@71a794e5 [wrapping: com.mysql.jdbc.JDBC4Connection@6db7a02e]
//        return conn;
//    }
//
//    // 使用dbcp数据库连接池(速度比c3p0快,但是本身有点bug)
//    private static DataSource source;
//    static{
//        try {
//            // 1.加载配置文件
//            Properties prop = new Properties();
//            prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("dbcp.properties"));
//            // 2.创建数据源
//            source = BasicDataSourceFactory.createDataSource(prop);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//    public static Connection getDBCPConnection() throws SQLException {
//        // 3.获取连接
//        Connection conn = source.getConnection();
//        System.out.println(conn);  // jdbc:mysql:///test, UserName=root@localhost, MySQL-AB JDBC Driver
//        return conn;
//    }

    // 使用druid数据库连接池(集C3P0和DBCP优点于一身的数据库连接池,推荐使用)
    private static DataSource dataSource;
    // 连接池只需要一个就可以了,可以放静态代码块,随着类加载而加载,且只执行一次
    static {
        try {
            // 1.加载配置文件
            Properties prop = new Properties();
//            prop.load(JdbcUtils.class.getClassLoader().getResourceAsStream("druid.properties"));
            prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("druid.properties"));
            // 2.创建数据源(连接池),这里使用了工厂模式加载配置文件创建对象
            dataSource = DruidDataSourceFactory.createDataSource(prop);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static Connection getDruidConnection() throws SQLException {
        // 3.获取连接
        Connection conn = dataSource.getConnection();
        System.out.println(conn);  // com.mysql.jdbc.JDBC4Connection@23202fce
        return conn;
    }

    // 手动关闭数据库连接
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
}