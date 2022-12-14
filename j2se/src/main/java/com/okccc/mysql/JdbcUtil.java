package com.okccc.mysql;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.alibaba.fastjson.JSONObject;
import com.okccc.pojo.Order;
import com.okccc.pojo.User;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

/**
 * @Author: okccc
 * @Date: 2022/12/9 11:20
 * @Desc: jdbc工具类
 *
 * JDBC(Java DataBase Connectivity)
 * jdbc是sun公司提供的操作数据库的接口java.sql.Driver,不同数据库厂商会提供相应的驱动类,比如
 * com.mysql.cj.jdbc.Driver、org.apache.hive.jdbc.HiveDriver、ru.yandex.clickhouse.ClickHouseDriver
 * 将数据库连接信息放到配置文件的好处：更换不同数据库时只改配置不改代码,开发人员面向接口编程
 *
 * ORM(object relation mapping)
 * mysql表和java类一一对应,表的一行对应类的一个对象,表的一列对应类的一个属性
 * 传统模式：java.sql.DriverManager每次都会创建新的连接,因为连接无法重用,如果连接过多或程序异常未及时关闭可能会OOM甚至服务器崩溃
 * 数据库连接池：javax.sql.DataSource会保持最小连接数,并且允许程序重复使用现有连接,当达到最大连接数时新的连接请求会被放入等待队列
 *
 * PreparedStatement优点
 * 1.预编译sql放入缓冲区提高效率,且下次执行相同sql时直接使用数据库缓冲区
 * 2.预编译sql可以防止sql注入(放到sql工具一看便知)
 * 手动拼接：sql经过解析器编译并执行,传递的参数也会参与编译
 * select * from user where name = 'tom' or '1=1'; 这里的 or '1=1' 会被当成sql指令运行,or被当成关键字了
 * 预编译：sql预先编译好等待传参执行,传递的参数就只是变量值
 * select * from user where name = "tom' or '1=1"; 这里的 tom' or '1=1 是一个变量整体,or也就失效了
 *
 * 异常处理
 * 框架中异常一般都会抛出做统一处理
 * getConnection()异常可以抛出,因为throws后面代码不会执行,数据库连不上也就不会有后面一系列操作
 * close()异常必须try/catch,因为finally代码块是一定会执行的,不管是否捕获到异常最终都要关闭连接
 *
 * 事务处理
 * 事务就是对表的insert/update/delete操作,使数据从一种状态变换到另一种状态
 * 一组事务中的所有操作要么全部成功(commit),要么全部失败(rollback),并且一旦提交就无法回滚
 * 1.DML操作一旦执行完就会自动提交数据 -> set autocommit = false
 * 2.数据库连接一旦断开也会自动提交数据 -> 将获取conn步骤从update方法中剥离出来单独关闭
 */
public class JdbcUtil {

    // 记录日志
    private static final Logger logger = LoggerFactory.getLogger(JdbcUtil.class);

    // 使用druid数据库连接池
    private static DataSource dataSource;
    private static Connection conn;

    /**
     * 传统方式获取数据库连接
     */
    public static Connection getConnection() throws Exception {
//        // 加载mysql驱动,Driver类是第三方api,可以改进为只用sun公司提供的java.sql包下的接口
//        Driver driver = new Driver();
//        // 数据库地址
//        String url = "jdbc:mysql://localhost:3306/test";
//        // 用户信息
//        Properties info = new Properties();
//        info.setProperty("user", "root");
//        info.setProperty("password", "root");
//        // 获取连接
//        Connection conn = driver.connect(url, info);
//
//        // mysql连接配置信息
//        String className = "com.mysql.jdbc.Driver";
//        String url = "jdbc:mysql://localhost:3306/test";
//        String user = "root";
//        String password = "root@123";
//        // 通过反射加载mysql驱动
//        Class<?> c = Class.forName(className);
//        // java.sql.Driver接口的com.mysql.jdbc.Driver实现类已经将创建和注册驱动的逻辑写在了静态代码块,随着驱动类的加载而加载,可以省略
//        Driver driver = (Driver) c.newInstance();  // 向上转型为java.sql.Driver接口类型
//        DriverManager.registerDriver(driver);
//        // 获取连接
//        Connection conn = DriverManager.getConnection(url, user, password);

        // 1.读取配置文件
        Properties prop = new Properties();
        prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties"));
        // 2.获取连接信息
        String driver = prop.getProperty("jdbc.driver");
        String url = prop.getProperty("jdbc.url");
        String user = prop.getProperty("jdbc.user");
        String password = prop.getProperty("jdbc.password");
        // 3.通过反射加载驱动
        Class.forName(driver);
        // 4.获取连接
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * druid连接池获取数据库连接
     */
    public static void initDruidConnection() throws Exception {
        if (dataSource == null) {
            // 1.加载配置文件
            Properties prop = new Properties();
            prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("druid.properties"));
            // 2.创建数据源(连接池),这里使用了工厂模式加载配置文件创建对象
            dataSource = DruidDataSourceFactory.createDataSource(prop);
        }
        // 3.获取连接对象
        conn = dataSource.getConnection();
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
     * 批量查询,将结果集封装成T类型对象(select)
     */
    public static <T> List<T> queryList(Class<T> clazz, String sql, Object ... args) throws Exception {
        // 存放查询结果的列表
        List<T> list = new ArrayList<>();
        // 1.获取连接,声明预编译和结果集
        if (conn == null) {
            initDruidConnection();
        }
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 2.预编译sql
            ps = conn.prepareStatement(sql);
            // 3.填充占位符
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            // 4.执行查询,返回结果集
            rs = ps.executeQuery();
            // 5.获取结果集的元数据信息,因为通用方法不知道具体字段,所以要先获取元数据进行分析
            ResultSetMetaData md = rs.getMetaData();
            // 遍历结果集
            while (rs.next()) {
                // 通过反射创建封装类型的对象
                T obj = clazz.newInstance();
                // 遍历所有列
                for (int i = 0; i < md.getColumnCount(); i++) {
                    // a.封装成JSONObject
                    if (obj instanceof JSONObject) {
                        // 从元数据获取列名
                        String columnName = md.getColumnName(i + 1);
                        // 从结果集获取列的值
                        Object value = rs.getObject(i + 1);
                        // 使用java bean工具类给对象的属性赋值
                        BeanUtils.setProperty(obj, columnName, value);
                    // b.封装成Java实体类
                    } else {
                        // 从元数据获取列的别名,对应java类的属性名
                        String columnLabel = md.getColumnLabel(i + 1);
                        // 从结果集获取列的值
                        Object columnValue = rs.getObject(i + 1);
                        // 通过反射获取类的属性并赋值
                        Field field = clazz.getDeclaredField(columnLabel);
                        field.setAccessible(true);
                        field.set(obj, columnValue);
                    }
                }
                list.add(obj);
            }
        } catch (Exception e) {
            // 在控制台打印错误堆栈信息(本地调试)
            e.printStackTrace();
            // 记录日志方便追溯历史排查问题(线上代码)
            logger.error(e.toString());
        } finally {
            // 6.释放资源：先别关闭conn,因为内存中的地址值并不会立即释放,此时conn!=null下次进来时就不会执行init,也就没有连接可用程序报错
            close(null, ps, rs);
        }
        // 返回查询结果
        return list;
    }

    public static void main(String[] args) throws Exception {
        // 当表名刚好是数据库关键字时要加斜引号`order`
        // 当表的列名和类的属性名不一致时,查询sql要使用属性名作为列名的别名,不然报错 java.lang.NoSuchFieldException: order_id
        String sql01 = "select order_id orderId, order_name orderName, order_date orderDate from `order`";
        String sql02 = "select * from user_info where id = ?";
        String sql03 = "select * from `order`";
        String sql04 = "select * from user_info where id = ?";
        System.out.println(queryList(Order.class, sql01));
        System.out.println(queryList(User.class, sql02, 1));
        System.out.println(queryList(JSONObject.class, sql03));
        System.out.println(queryList(JSONObject.class, sql04, 1));
    }
}