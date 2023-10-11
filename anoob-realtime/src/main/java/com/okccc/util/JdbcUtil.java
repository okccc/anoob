package com.okccc.util;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.CaseFormat;
import com.okccc.app.bean.ConfigInfo;
import org.apache.commons.beanutils.BeanUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: okccc
 * @Date: 2023/4/8 09:39:03
 * @Desc: jdbc通用查询工具类 mysql、hive、phoenix、clickhouse
 * https://github.com/alibaba/druid/wiki/DruidDataSource%E9%85%8D%E7%BD%AE%E5%B1%9E%E6%80%A7%E5%88%97%E8%A1%A8
 */
public class JdbcUtil {

    /**
     * 创建数据库连接池
     */
    public static DruidDataSource getDataSource(String driverClass, String jdbcUrl, String username, String password) {
        // 创建连接池
        DruidDataSource druidDataSource = new DruidDataSource();

        // 连接信息
        druidDataSource.setDriverClassName(driverClass);
        druidDataSource.setUrl(jdbcUrl);
        druidDataSource.setUsername(username);
        druidDataSource.setPassword(password);

        // 初始化时连接数,默认0
        druidDataSource.setInitialSize(5);

        // 最大活跃连接数,默认8
        druidDataSource.setMaxActive(20);

        // 最小空闲连接数,默认0
        druidDataSource.setMinIdle(1);

        // 没有空闲连接时等待的超时时间,默认-1表示一直等待
        druidDataSource.setMaxWait(-1);

        // 校验连接是否有效的sql
        druidDataSource.setValidationQuery("select 1");

        // 借出连接时是否校验(会降低性能)
        druidDataSource.setTestOnBorrow(false);

        // 归还连接时是否校验(会降低性能)
        druidDataSource.setTestOnReturn(false);

        // 连接空闲时是否校验(不影响性能,保证安全性)
        druidDataSource.setTestWhileIdle(true);

        // 连接空闲多久就回收(默认60s),是testWhileIdle的判断依据,要小于mysql的wait_timeout
        druidDataSource.setTimeBetweenEvictionRunsMillis(60 * 1000L);

        // 返回连接池
        return druidDataSource;
    }

    /**
     * 批量查询,将结果集封装成T类型对象
     */
    public static <T> List<T> queryList(Connection conn, String sql, Class<T> clazz, Boolean underlineToCamel) throws Exception {
        // 存放结果集的列表
        ArrayList<T> list = new ArrayList<>();

        // 预编译sql
        PreparedStatement ps = conn.prepareStatement(sql);

        // 执行查询,返回结果集
        ResultSet rs = ps.executeQuery();

        // 获取结果集的元数据信息
        ResultSetMetaData md = rs.getMetaData();

        // 行遍历,将行数据转换成T类型对象
        while (rs.next()) {
            // 通过反射创建对应封装类型的对象
            T obj = clazz.newInstance();

            // 列遍历,给T类型对象赋值
            for (int i = 0; i < md.getColumnCount(); i++) {
                // 从元数据获取字段名称,从结果集获取字段值
                String columnName = md.getColumnName(i + 1);
//                String columnName = md.getColumnName(i + 1).split("\\.")[1];  // hive列名会带库名前缀需手动去除
                Object value = rs.getObject(i + 1);

                // guava可以实现下划线(Mysql)和驼峰(JavaBean)的相互转换,update_time -> updateTime
                if (underlineToCamel) {
                    columnName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, columnName.toLowerCase());
                }

                // 泛型没有get/set方法,commons-beanutils可以给JavaBean(包括泛型)赋值
                BeanUtils.setProperty(obj, columnName, value);
            }

            // 添加到列表
            list.add(obj);
        }

        // 释放资源
        ps.close();
        rs.close();

        // 返回结果集
        return list;
    }

    public static void main(String[] args) throws Exception {
        // 查询mysql
        DruidDataSource druidDataSource01 = getDataSource(ConfigInfo.MYSQL_DRIVER, ConfigInfo.MYSQL_URL, ConfigInfo.MYSQL_USER, ConfigInfo.MYSQL_PASSWORD);
        DruidPooledConnection conn01 = druidDataSource01.getConnection();
        System.out.println(queryList(conn01, "select * from mock.base_trademark", JSONObject.class, false));
        conn01.close();

        // 查询phoenix
        DruidDataSource druidDataSource02 = getDataSource(ConfigInfo.PHOENIX_DRIVER, ConfigInfo.PHOENIX_SERVER, null, null);
        DruidPooledConnection conn02 = druidDataSource02.getConnection();
        System.out.println(queryList(conn02, "select * from dim.dim_base_trademark", JSONObject.class, false));
        conn02.close();

        // 查询hive
        DruidDataSource druidDataSource03 = getDataSource(ConfigInfo.HIVE_DRIVER, ConfigInfo.HIVE_URL, ConfigInfo.HIVE_USER, ConfigInfo.HIVE_PASSWORD);
        DruidPooledConnection conn03 = druidDataSource03.getConnection();
        System.out.println(queryList(conn03, "select * from ods.ods_trd_trade_admin_spu_info_all_d where dt=20230425", JSONObject.class, false));
        conn03.close();

        // 查询clickhouse
        DruidDataSource druidDataSource04 = getDataSource(ConfigInfo.CLICKHOUSE_DRIVER, ConfigInfo.CLICKHOUSE_URL, ConfigInfo.CLICKHOUSE_USER, ConfigInfo.CLICKHOUSE_PASSWORD);
        DruidPooledConnection conn04 = druidDataSource04.getConnection();
        System.out.println(queryList(conn04, "select * from realtime.dirty_log", JSONObject.class, false));
        conn04.close();
    }
}
