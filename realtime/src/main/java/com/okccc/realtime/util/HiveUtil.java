package com.okccc.realtime.util;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2022/1/28 3:34 下午
 * Desc: 读写hive的工具类
 */
public class HiveUtil {

    private static final Logger logger = LoggerFactory.getLogger(HiveUtil.class);
    private static Connection conn;

    /**
     * 获取数据库连接
     */
    public static void initConnection() throws Exception {
        // 1.读取配置文件
        Properties prop = new Properties();
        prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("config.properties"));
        // 2.获取连接信息
        String driver = prop.getProperty("hive.driver");
        String url = prop.getProperty("hive.url");
        String user = prop.getProperty("hive.user");
        String password = prop.getProperty("hive.password");
        // 3.通过反射加载驱动
        Class.forName(driver);
        // 4.创建连接
        conn =  DriverManager.getConnection(url, user, password);
    }

    /**
     * 关闭数据库连接
     */
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

    /**
     * 常用操作(没啥意义,beeline命令行更方便)
     */
    public static void crud() throws Exception {
        // 声明连接、编译
        if (conn == null) {
            initConnection();
        }
        Statement statement = conn.createStatement();
        // 创建数据库
        String create_db = "create database db01";
        // 删除数据库
        String drop_db = "drop database if exists db01";
        // 创建表
        String create_tb = "create table if not exists ods.aaa(id int, name string)";
        // 删除表
        String drop_tb = "drop table if exists ods.aaa";
        // 加载数据
        String load = "load data local inpath 'a.txt' overwrite into table ods.aaa";
        // 执行语句
//        statement.execute(create_db);
        // 查看所有数据库
        String show_db = "show databases";
        // 查看所有表
        String show_tb = "show tables";
        // 查看表结构
        String desc_tb = "desc ods.aaa";
        // 执行查询
        ResultSet rs = statement.executeQuery(show_db);
        while (rs.next()) {
            System.out.println(rs.getString(1));
        }
    }

    /**
     * msck修复数据
     */
    public static void msck(String tableName) throws Exception {
        if (conn == null) {
            initConnection();
        }
        Statement statement = conn.createStatement();
        statement.execute("set hive.msck.path.validation=ignore");
        statement.execute("msck repair table " + tableName);
    }

    /**
     * 批量查询,将结果集封装成T类型对象,hive不能像mysql/hbase/clickhouse那样直接通过jdbc写数据,需要使用java/flink操作hdfs的api
     */
    public static <T> List<T> queryList(String sql, Class<T> clazz) throws Exception {
        // 存放结果集的列表
        List<T> list = new ArrayList<>();
        // 声明连接、预编译和结果集
        if (conn == null) {
            initConnection();
//            System.out.println(conn);  // org.apache.hive.jdbc.HiveConnection@3c9754d8
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 预编译sql
            ps = conn.prepareStatement(sql);
//            System.out.println(ps);  // org.apache.hive.jdbc.HivePreparedStatement@fbd1f6
            // 执行查询,返回结果集
            rs = ps.executeQuery();
//            System.out.println(rs);  // org.apache.hive.jdbc.HiveQueryResultSet@51f116b8
            // 获取结果集的元数据信息
            ResultSetMetaData md = rs.getMetaData();
            // 遍历结果集,循环次数未知用while
            while (rs.next()) {
                // 通过反射创建对应封装类型的对象
                T obj = clazz.newInstance();
                for (int i = 0; i < md.getColumnCount(); i++) {
                    // 从元数据获取字段名称,从结果集获取字段值,hive列名会带库名前缀需手动去除
                    String columnName = md.getColumnName(i + 1).split("\\.")[1];
                    Object columnValue = rs.getObject(i + 1);
                    // 使用java bean工具类给对象的属性赋值
                    BeanUtils.setProperty(obj, columnName, columnValue);
                }
                // 添加到列表
                list.add(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        } finally {
            close(null, ps, rs);
        }
        // 返回结果集
        return list;
    }

    public static void main(String[] args) throws Exception {
        List<JSONObject> jsonObjects = queryList("select * from ods.ods_knowledge_record0", JSONObject.class);
        System.out.println(jsonObjects);
    }
}
