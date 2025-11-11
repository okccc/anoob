package com.okccc.util;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.fastjson2.JSONObject;
import com.okccc.app.bean.ConfigInfo;
import org.apache.commons.lang3.StringUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * @Author: okccc
 * @Date: 2023/3/16 17:16:09
 * @Desc: phoenix工具类
 * 1.创建连接池和线程池都属于重量级操作,很消耗资源,可以使用单例模式只创建一次对象,静态代码块相当于饿汉式
 * 2.获取数据库连接应该由工具类本身提供,而不是调用工具类的外部代码,不然每个调用者都要写一遍获取连接的逻辑
 */
public class PhoenixUtil {

    // 声明phoenix数据库连接池
    private static final DruidDataSource DRUID_DATA_SOURCE = JdbcUtil.getDataSource(ConfigInfo.PHOENIX_DRIVER, ConfigInfo.PHOENIX_SERVER, null, null);

    /**
     * 创建维度表
     */
    public static void createTable(String sinkTable, String sinkColumns, String sinkPk, String sinkExtend) {
        // 判断主键字段和建表扩展
        if (sinkPk == null || sinkPk.isEmpty()) {
            sinkPk = "id";
        }
        if (sinkExtend == null) {
            sinkExtend = "";
        }

        // 拼接建表语句: create table if not exists ${db}.{table}(id varchar primary key,bb varchar,cc varchar) xxx
        StringBuilder sb = new StringBuilder("create table if not exists ")
                .append(ConfigInfo.HBASE_SCHEMA)
                .append(".")
                .append(sinkTable)
                .append("(");
        // 获取所有字段
        String[] columns = sinkColumns.split(",");
        for (int i = 0; i < columns.length; i++) {
            String column = columns[i];
            // 判断是否为主键
            if (sinkPk.equals(column)) {
                sb.append(column).append(" varchar primary key");
            } else {
                sb.append(column).append(" varchar");
            }
            // 判断是否是最后一个字段
            if (i < columns.length - 1) {
                sb.append(",");
            }
        }
        sb.append(")").append(sinkExtend);
        System.out.println("phoenix建表语句：" + sb);

        DruidPooledConnection conn = null;
        PreparedStatement ps = null;
        try {
            // 从连接池获取连接
            conn = DRUID_DATA_SOURCE.getConnection();
            // 预编译sql
            ps = conn.prepareStatement(sb.toString());
            // 执行sql
            ps.execute();
        } catch (SQLException e) {
            e.printStackTrace();
            // 捕获异常时要考虑一个问题：到底要不要继续往下执行？
            // 此处建表失败的话后续维度数据就没法写,所以应该终止程序,手动throw将编译期异常转换为运行期异常,不然还会往下执行
            throw new RuntimeException("phoenix建表失败：" + sinkTable);
        } finally {
            if (ps != null) {
                try {
                    ps.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 往维度表写数据
     */
    public static void upsertValues(String sinkTable, JSONObject data) throws SQLException {
        // 获取列和值
        String columns = StringUtils.join(data.keySet(), ",");
        String values = StringUtils.join(data.values(), "','");  // value之间的逗号要加引号,不然传入的是一整个字符串

        // 拼接插入语句: upsert into dim.dim_user_info(id,name) values(1,"grubby")
        String sql = "upsert into " + ConfigInfo.HBASE_SCHEMA + "." + sinkTable + "(" + columns + ") values ('" + values + "')";
        System.out.println("phoenix插入语句：" + sql);  // upsert into base_trademark (id,tm_name) values("10","小米")

        // 从连接池获取连接
        DruidPooledConnection conn = DRUID_DATA_SOURCE.getConnection();

        // 预编译sql
        PreparedStatement ps = conn.prepareStatement(sql);

        // 执行sql
        ps.execute();

        // 执行程序不报错但是数据没写进去,在客户端命令行手动执行sql就没问题,那么两者区别在哪？已知在命令行执行sql会自动提交事务
        // 查看Connection接口的实现类PhoenixConnection源码发现属性isAutoCommit=false,需要手动提交事务
//        conn.commit();

        // 释放资源
        ps.close();

        // 查看池中连接使用情况
//        System.out.println("druidDataSource = " + DRUID_DATA_SOURCE);

        // 代码没报错但是没数据？没有释放连接导致没有空闲连接可用,而DruidDataSource等待超时时间默认是-1即一直等待,所以一定要及时释放连接
        // 或者设置超时时间为10秒让代码报错 Caused by: com.alibaba.druid.pool.GetConnectionTimeoutException: wait millis 10005, active 10, maxActive 10, creating 0
        conn.close();
    }

    /**
     * 根据主键id查询维度数据,结果只有一条
     */
    public static JSONObject getValueById(String tableName, String primaryKey) throws Exception {
        // 从连接池获取连接
        DruidPooledConnection conn = DRUID_DATA_SOURCE.getConnection();
//        System.out.println(conn);  // org.apache.phoenix.jdbc.PhoenixConnection@ddf20fd

        // 拼接查询语句
        String dimSql = "SELECT * FROM " + ConfigInfo.HBASE_SCHEMA + "." + tableName + " WHERE id = '" + primaryKey + "'";

        // 执行查询
        List<JSONObject> list = JdbcUtil.queryList(conn, dimSql, JSONObject.class, false);

        // 归还连接
        conn.close();

        // 返回结果
        if (!list.isEmpty()) {
            return list.get(0);
        } else {
            System.out.println("维度数据没找到：" + dimSql);
        }
        return null;
    }
}
