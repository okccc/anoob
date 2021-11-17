package com.okccc.realtime.utils;

import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.common.MyConfig;
import org.apache.commons.beanutils.BeanUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: okccc
 * Date: 2021/10/22 下午3:17
 * Desc: 使用phoenix读写hbase的工具类
 */
public class PhoenixUtil {

    // 声明数据库连接
    private static Connection conn;

    /**
     * 获取数据库连接
     */
    public static void initConnection() {
        try {
            // 通过反射加载驱动
            Class.forName(MyConfig.PHOENIX_DRIVER);
            // 创建连接
            conn = DriverManager.getConnection(MyConfig.PHOENIX_SERVER);
            // 设置namespace
            conn.setSchema(MyConfig.HBASE_SCHEMA);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
     * 批量查询
     */
    public static <T> List<T> queryList(String sql, Class<T> clazz) {
        // 存放结果集的列表
        List<T> list = new ArrayList<>();
        // 声明连接、预编译和结果集
        if (conn == null) {
            initConnection();
//            System.out.println(conn);  // org.apache.phoenix.jdbc.PhoenixConnection@1bd81830
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 预编译sql
            ps = conn.prepareStatement(sql);
//            System.out.println(ps);  // select * from realtime.dim_base_trademark
            // 执行查询,返回结果集
            rs = ps.executeQuery();
//            System.out.println(rs);  // org.apache.phoenix.jdbc.PhoenixResultSet@392a04e7
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

    /**
     * 更新操作
     */
    public static void upsert(String sql) {
        if (conn == null) {
            initConnection();
        }
        PreparedStatement ps = null;
        try {
            // 预编译sql
            ps = conn.prepareStatement(sql);
            // 执行更新,返回影响行数
            ps.executeUpdate();
            // 执行程序不报错但是数据没写进去,在客户端命令行手动执行sql就没问题,那么两者区别在哪？已知在命令行执行sql会自动提交事务
            // 查看Connection接口的实现类PhoenixConnection源码发现属性isAutoCommit=false,需要手动提交事务
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 释放资源
            close(null, ps, null);
        }
    }

    public static void main(String[] args) {
        upsert("upsert into dim_base_trademark values('13', '小米')");
        List<JSONObject> jsonObjects = queryList("select * from dim_base_trademark", JSONObject.class);
        System.out.println(jsonObjects);
    }
}
