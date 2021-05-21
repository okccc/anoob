package com.okccc.bigdata.jdbc.util;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.apache.commons.dbcp.BasicDataSourceFactory;

import javax.sql.DataSource;
import java.io.FileInputStream;
import java.io.FileReader;
import java.sql.*;
import java.util.Properties;

@SuppressWarnings("all")
public class JDBCUtils {
    public static void main(String[] args) throws Exception {
        // 提供数据库连接和关闭操作的工具类
        getConnection();
        getC3P0Connection();
        getDBCPConnection();
    }

    // 手动获取连接
    public static Connection getConnection() throws Exception {
        // 1.读取配置文件
        Properties prop = new Properties();
        FileReader fr = new FileReader("ability/src/main/resources/config.properties");
        prop.load(fr);
        // 2.获取连接信息
        String driver = prop.getProperty("driver");
        String url = prop.getProperty("url");
        String user = prop.getProperty("user");
        String password = prop.getProperty("password");
        // 3.通过反射加载驱动
        Class.forName(driver);
        // 4.获取连接
        Connection conn = DriverManager.getConnection(url, user, password);
        System.out.println(conn);  // com.mysql.jdbc.JDBC4Connection@396a51ab
        return conn;
    }

    // 使用c3p0数据库连接池
    private static ComboPooledDataSource cpds = new ComboPooledDataSource("mysql01");
    public static Connection getC3P0Connection() throws SQLException {
        // 获取连接
        Connection conn = cpds.getConnection();
        System.out.println(conn);  // com.mchange.v2.c3p0.impl.NewProxyConnection@71a794e5 [wrapping: com.mysql.jdbc.JDBC4Connection@6db7a02e]
        return conn;
    }

    // 使用dbcp数据库连接池
    private static DataSource source;
    static{
        try {
            // 1.加载配置文件
            Properties prop = new Properties();
            FileInputStream is = new FileInputStream("ability/src/main/resources/dbcp.properties");
//            InputStream is = JDBCUtils.class.getClassLoader().getResourceAsStream("config.properties");
            prop.load(is);
            // 2.创建数据源
            source = BasicDataSourceFactory.createDataSource(prop);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static Connection getDBCPConnection() throws SQLException {
        // 3.获取连接
        Connection conn = source.getConnection();
        System.out.println(conn);  // jdbc:mysql:///test, UserName=root@localhost, MySQL-AB JDBC Driver
        return conn;
    }

    // 关闭数据库连接
    public static void close(Connection conn, PreparedStatement ps, ResultSet rs) {
        // 手动关闭
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
        // 使用DBUtils工具类关闭
//        try {
//            DbUtils.close(conn);
//        } catch (SQLException throwables) {
//            throwables.printStackTrace();
//        }
//        try {
//            DbUtils.close(ps);
//        } catch (SQLException throwables) {
//            throwables.printStackTrace();
//        }
//        try {
//            DbUtils.close(rs);
//        } catch (SQLException throwables) {
//            throwables.printStackTrace();
//        }
    }
}
