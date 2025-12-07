package com.okccc.util;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.base.CaseFormat;
import com.okccc.app.bean.ConfigInfo;
import org.apache.commons.beanutils.BeanUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: okccc
 * @Date: 2023/4/8 09:39:03
 * @Desc: jdbc通用查询工具类 mysql、hive、presto、phoenix、clickhouse
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
        // 可以解决异常 The last packet successfully received from the server was 5,811,951 milliseconds ago.
        druidDataSource.setTestOnBorrow(true);

        // 归还连接时是否校验(会降低性能)
        druidDataSource.setTestOnReturn(false);

        // 连接空闲时是否校验(不影响性能,保证安全性)
        druidDataSource.setTestWhileIdle(true);

        // 空闲连接回收器每隔60秒(默认)运行一次,是testWhileIdle的判断依据,要小于mysql的wait_timeout
        druidDataSource.setTimeBetweenEvictionRunsMillis(60 * 1000L);

        // 空闲连接超过30分钟(默认)就被回收
        druidDataSource.setMinEvictableIdleTimeMillis(30 * 60 * 1000L);

        // 返回连接池
        return druidDataSource;
    }

    /**
     * 批量查询,将结果集封装成T类型对象
     */
    public static <T> List<T> queryList(Connection conn, String sql, Class<T> clazz, Boolean underlineToCamel) throws Exception {
        // 预编译sql
        PreparedStatement ps = conn.prepareStatement(sql);

        // 场景1.数据量一般,可以先放到结果集列表后续统一处理
        ArrayList<T> list = new ArrayList<>();

        // 场景2.数据量很大,列表放不下会导致内存溢出,可以分批处理
        ps.setFetchSize(1000);

        // 执行查询,返回结果集
        // ResultSet维护了一个指向数据库记录的游标,每次调用next就是取当前行数据放到内存并将游标下移,只要数据库连接不中断就能一直取
        // 为了提高性能和减少对数据库的访问,可以在url添加&useCursorFetch=true&defaultFetchSize=100,如果不生效就要在代码里手动设置
        ResultSet rs = ps.executeQuery();

        // 获取结果集的元数据信息
        ResultSetMetaData md = rs.getMetaData();

        // 行遍历,将行数据转换成T类型对象
        while (rs.next()) {
            // 通过反射创建对应封装类型的对象
//            T obj = clazz.newInstance();
            T obj = clazz.getConstructor().newInstance();

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
        System.out.println(queryList(conn01, "select * from eshop.brand", JSONObject.class, false));
        conn01.close();

        // 查询hive
        DruidDataSource druidDataSource02 = getDataSource(ConfigInfo.HIVE_DRIVER, ConfigInfo.HIVE_URL, ConfigInfo.HIVE_USER, ConfigInfo.HIVE_PASSWORD);
        DruidPooledConnection conn02 = druidDataSource02.getConnection();
        System.out.println(queryList(conn02, "select * from ods.ods_log_info where dt=20240101", JSONObject.class, false));
        conn02.close();

        // 查询presto,比hive快10倍
        DruidDataSource druidDataSource03 = getDataSource(ConfigInfo.PRESTO_DRIVER, ConfigInfo.PRESTO_URL, ConfigInfo.PRESTO_USER, ConfigInfo.PRESTO_PASSWORD);
        DruidPooledConnection conn03 = druidDataSource03.getConnection();
        System.out.println(queryList(conn03, "select * from ods.ods_log_info where dt='20240101'", JSONObject.class, false));
        conn03.close();

        // 查询phoenix
        DruidDataSource druidDataSource04 = getDataSource(ConfigInfo.PHOENIX_DRIVER, ConfigInfo.PHOENIX_SERVER, null, null);
        DruidPooledConnection conn04 = druidDataSource04.getConnection();
        System.out.println(queryList(conn04, "select * from dim.dim_base_trademark", JSONObject.class, false));
        conn04.close();

        // 查询clickhouse
        DruidDataSource druidDataSource05 = getDataSource(ConfigInfo.CLICKHOUSE_DRIVER, ConfigInfo.CLICKHOUSE_URL, ConfigInfo.CLICKHOUSE_USER, ConfigInfo.CLICKHOUSE_PASSWORD);
        DruidPooledConnection conn05 = druidDataSource05.getConnection();
        System.out.println(queryList(conn05, "select * from realtime.dirty_log", JSONObject.class, false));
        conn05.close();
    }

}
