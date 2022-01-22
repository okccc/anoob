package com.okccc.realtime.utils;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.bean.TransientSink;
import com.okccc.realtime.common.MyConfig;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Field;
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

    /**
     * 获取数据库连接
     */
    public static Connection initConnection() throws Exception {
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

    /**
     * 使用druid连接池获取数据库连接
     */
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
     * 批量查询,将结果集封装成T类型对象
     */
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
            // 释放资源,先别关闭conn,因为内存中地址值并不会立即释放,此时conn!=null,下次进来时就不会执行init,也就没有连接可用程序报错
            close(null, ps, rs);
        }
        // 返回结果集
        return list;
    }

    /**
     * flink-connector-jdbc将java bean写入mysql
     */
    public static <T> SinkFunction<T> getJdbcSinkBySchema(String sql) {
        // JdbcSink内部使用了预编译器,可以批量提交优化写入速度,但是只能操作一张表,如果是一流写多表就得自定义类实现SinkFunction接口
        return JdbcSink.sink(
                sql,
                new JdbcStatementBuilder<T>() {
                    @Override
                    public void accept(PreparedStatement ps, T obj) {
                        // 获取对象属性,给sql语句的问号占位符赋值
                        // 正常获取属性是obj.getXxx(),工具类是通用的都是泛型,还不知道当前对象是啥,可以通过反射动态获取对象信息
                        Field[] fields = obj.getClass().getDeclaredFields();
                        // 当java bean属性和表中字段不一致时处理方式：1.将添加注解的属性都放最后 2.给占位符赋值时手动减去角标(推荐)
                        int skipNum = 0;
                        // 遍历所有属性
                        for (int i = 0; i < fields.length; i++) {
                            // 获取当前属性
                            Field field = fields[i];
                            // 判断该属性是否包含注解
                            TransientSink annotation = field.getAnnotation(TransientSink.class);
                            if (annotation != null) {
                                // 有就直接跳过不处理,并且将索引值-1
                                skipNum++;
                                continue;
                            }
                            // 私有属性要先获取访问权限
                            field.setAccessible(true);
                            try {
                                // 获取当前属性的值,反射就是反过来写obj.getField() -> field.get(obj)
                                Object value = field.get(obj);
                                // 给占位符赋值
                                ps.setObject(i + 1 - skipNum, value);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                },
                new JdbcExecutionOptions
                        .Builder()
                        .withBatchSize(10)  // 每个并行度的数据够10条就写一次,减少和数据库交互次数
                        .withBatchIntervalMs(10)  // 不够10条的话等10秒也会写一次
                        .build(),
                new JdbcConnectionOptions
                        .JdbcConnectionOptionsBuilder()
                        .withDriverName(MyConfig.MYSQL_DRIVER)
                        .withUrl(MyConfig.MYSQL_URL)
                        .withUsername(MyConfig.MYSQL_USER)
                        .withPassword(MyConfig.MYSQL_PASSWORD)
                        .build()
        );
    }

    public static void main(String[] args) throws Exception {
        List<JSONObject> jsonObjects = queryList("select * from user_info", JSONObject.class);
        System.out.println(jsonObjects);
    }
}
