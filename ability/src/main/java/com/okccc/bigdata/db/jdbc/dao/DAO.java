package com.okccc.bigdata.db.jdbc.dao;

import com.okccc.bigdata.db.jdbc.JdbcUtils;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

// DAO(data access object)：数据访问对象
public class DAO {

    /**
     * 通用带事务的更新操作(insert/delete/update)
     */
    public static void updateWithTx(Connection conn, String sql, Object... args) {
        PreparedStatement ps = null;
        try {
            // 1.预编译sql
            ps = conn.prepareStatement(sql);
            // 2.填充占位符
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1 , args[i]);
            }
            // 3.执行更新操作,返回影响记录数
            int count = ps.executeUpdate();
            System.out.println("影响了 " + count + " 条记录");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 4.关闭ps,conn在外面单独关闭
            JdbcUtils.close(null, ps, null);
        }
    }

    /**
     * 通用查询单条记录方法
     */
    public static <T> T queryOne(Class<T> clazz, String sql, Object... args) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 1.获取连接
//            conn = JDBCUtils.getConnection();
            conn = JdbcUtils.getC3P0Connection();
            // 2.预编译sql
            ps = conn.prepareStatement(sql);
            // 3.填充占位符
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            // 4.执行查询操作,返回结果集
            rs = ps.executeQuery();
            System.out.println(rs);
            // 5.获取结果集的元数据,因为通用查询方法不知道具体查询哪些字段,所以要先获取结果集的元数据进行分析
            ResultSetMetaData rsmd = rs.getMetaData();
            // 从元数据获取列数
            int columnCount = rsmd.getColumnCount();
            if (rs.next()) {
                T t = clazz.newInstance();
                for (int i = 0; i < columnCount; i++) {
                    // 从元数据获取列的别名,对应设计的java类的属性名
                    String columnLabel = rsmd.getColumnLabel(i + 1);
                    System.out.println(columnLabel);
                    // 从结果集获取列的值
                    Object columnValue = rs.getObject(i + 1);
                    // 通过反射获取对象并给字段赋值
                    Field field = clazz.getDeclaredField(columnLabel);
                    field.setAccessible(true);
                    field.set(t, columnValue);
                }
                return t;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 6.关闭连接
            JdbcUtils.close(conn, ps, rs);
        }
        return null;
    }

    /**
     * 通用查询多条记录方法
     */
    public static <T> List<T> queryList(Class<T> clazz, String sql, Object... args) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<T> list = new ArrayList<>();
        try {
            // 1.获取连接
//            conn = JDBCUtils.getConnection();
            conn = JdbcUtils.getDBCPConnection();
            // 2.预编译sql
            ps = conn.prepareStatement(sql);
            // 3.填充占位符
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            // 4.执行查询操作,返回结果集
            rs = ps.executeQuery();
            // 5.获取结果集的元数据,因为通用查询方法不知道具体查询哪些字段,所以要先获取结果集的元数据进行分析
            ResultSetMetaData rsmd = rs.getMetaData();
            // 从元数据获取列数
            int columnCount = rsmd.getColumnCount();
            while (rs.next()) {
                T t = clazz.newInstance();
                for (int i = 0; i < columnCount; i++) {
                    // 从元数据获取列的别名,对应设计的java类的属性名
                    String columnLabel = rsmd.getColumnLabel(i + 1);
                    System.out.println(columnLabel);
                    // 从结果集获取列的值
                    Object columnValue = rs.getObject(i + 1);
                    // 通过反射获取对象并给字段赋值
                    Field field = clazz.getDeclaredField(columnLabel);
                    field.setAccessible(true);
                    field.set(t, columnValue);
                }
                list.add(t);
            }
            return list;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 6.关闭连接
            JdbcUtils.close(conn, ps, rs);
        }
        return null;
    }
}
