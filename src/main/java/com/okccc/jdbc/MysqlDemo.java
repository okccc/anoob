package com.okccc.jdbc;

import com.okccc.jdbc.bean.User;
import com.okccc.jdbc.bean.Order;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@SuppressWarnings("unused")
public class MysqlDemo {
    public static void main(String[] args) throws Exception {
        /*
         * jdbc
         * java提供了操作数据库表的api(java.sql包和javax.sql包),使用jdbc可以连接任何提供了jdbc驱动的数据库系统
         * jdbc是sun公司提供的一套操作数据库的接口,开发人员只需面向接口编程,不同数据库厂商需要针对这套接口实现对应的驱动类
         * 将数据库连接信息放到配置文件好处: 更换不同数据库时只改配置文件不改代码,jdbc接口是固定的java.sql.Driver
         * ORM(object relation mapping): 一个mysql表对应一个java类,表的一列对应类的一个属性,表的一行对应类的一个对象
         * 传统模式: java.sql.DriverManager每次连接都要将Connection加载到内存,消耗大量资源且连接无法重用,也无法控制创建的连接数,连接过多或程序异常未及时关闭,可能导致内存泄漏甚至服务器崩溃
         * 数据库连接池: javax.sql.DataSource会保持最小连接数,允许程序重复使用现有数据库连接,当达到最大连接数时新的请求会被放入等待队列
         *
         * PreparedStatement优点
         * 1.预编译sql放入缓冲区提高效率,且下次执行相同sql时直接使用数据库缓冲区
         * 2.预编译sql可以防止sql注入(放到sql工具一看便知)
         * 手动拼接: sql经过解析器编译并执行,传递的参数也会参与编译,select * from user where name = 'tom' or '1=1'; 这里的 or '1=1' 会被当成sql指令运行,or被当成关键字了
         * 预编译: sql预先编译好等待传参执行,传递的参数就只是变量值,select * from user where name = "tom' or '1=1"; 这里的 tom' or '1=1 是一个变量整体,or也就失效了
         *
         * 异常处理
         * 框架中异常一般都会抛出做统一处理
         * getConnection()异常可以抛出,因为throws后面代码不会执行,数据库连不上也就不会有后面一系列操作
         * close()异常必须try/catch,因为finally代码块是一定会执行的,不管是否捕获到异常最终都要关闭连接
         *
         * 事务处理
         * 事务就是对表的更新操作,使数据从一种状态变换到另一种状态
         * 一个事务中的所有操作要么全部失败然后回滚(rollback),要么全部成功提交(commit)并且一旦提交就无法回滚
         * 什么时候会提交数据？
         * a.执行DML操作,默认情况下一旦执行完会自动提交数据 -> set autocommit = false
         * b.一旦断开数据库连接,也会提交数据 -> 将获取conn步骤从update方法中剥离出来单独关闭
         */

        testConnect();
//        testUpdate();
//        testSelect();
    }

    private static void testConnect() throws Exception {
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
//        System.out.println(conn);

//        // mysql连接配置信息
//        String className = "com.mysql.jdbc.Driver";
//        String url = "jdbc:mysql://localhost:3306/test";
//        String user = "root";
//        String password = "root";
//        // 通过反射加载mysql驱动
//        Class<?> c = Class.forName(className);
//        // java.sql.Driver接口的com.mysql.jdbc.Driver实现类已经将创建和注册驱动的逻辑写在了静态代码块,随着驱动类的加载而加载,可以省略
//        Driver driver = (Driver) c.newInstance();  // 向上转型为java.sql.Driver接口类型
//        DriverManager.registerDriver(driver);
//        // 获取连接
//        Connection conn = DriverManager.getConnection(url, user, password);
//        System.out.println(conn);

        // 1.读取配置文件
        Properties prop = new Properties();
        prop.load(ClassLoader.getSystemClassLoader().getResourceAsStream("config.properties"));
        // 2.获取连接信息
        String driver = prop.getProperty("jdbc.driver");
        String url = prop.getProperty("jdbc.url");
        String user = prop.getProperty("jdbc.user");
        String password = prop.getProperty("jdbc.password");
        // 3.通过反射加载mysql驱动
        Class.forName(driver);
        // 4.建立连接
        Connection conn = DriverManager.getConnection(url, user, password);
        System.out.println(conn);  // com.mysql.jdbc.JDBC4Connection@5034c75a
    }

    private static void testUpdate() throws SQLException {
//        // 使用dbutils包的QueryRunner工具类,查看源码发现它封装了预编译sql、填充占位符、执行更新等操作
//        Connection conn = JDBCUtils.getC3P0Connection();
//        String sql1 = "update `order` set order_name = ? where order_id = ?";
//        QueryRunner runner = new QueryRunner();
//        runner.update(conn, sql1, "CC", 4);

        Connection conn = null;
        try {
            // 获取连接
            conn = JdbcUtil.getDruidConnection();
            // 关闭自动提交
            conn.setAutoCommit(false);

            // 1.演示更新单条记录
            String sql2 = "update user_table set balance = balance - 1000 where user = ?";
            String sql3 = "update user_table set balance = balance + 1000 where user = ?";
            // 事务操作1
            updateWithTx(conn, sql2, "AA");
            // 此处模拟异常情况
//            System.out.println(1/0);
            // 事务操作2
            updateWithTx(conn, sql3, "BB");

            // 2.演示批量更新多条记录
            String sql4 = "insert into `order` values (null, ?, ?)";
            PreparedStatement ps = conn.prepareStatement(sql4);
            for (int i = 1; i <= 10000; i++) {
                ps.setObject(1, "orc" + i);
                ps.setObject(2, "2020-01-01");
                ps.addBatch();
            }
            // 执行批
            ps.executeBatch();
            ps.close();

            // 手动提交
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
            if (conn != null) {
                // 如果异常就会滚
                conn.rollback();
            }
        } finally {
            // 关闭连接
            JdbcUtil.close(conn, null, null);
        }
    }

    private static void testSelect() {
        // 1.演示查询单条记录
        // 当表名刚好是数据库里的关键字时要加斜引号`order`
        // 当表中字段名和类中属性名不一致时,查询时要使用属性名作为字段名的别名,不然报错 java.lang.NoSuchFieldException: order_id
        String sql01 = "select order_id orderId, order_name orderName from `order` where order_id = ?";
        Order order = queryOne(Order.class, sql01, 2);
        System.out.println(order);  // Order{orderId=2, orderName='BB', orderDate=null}

        // 2.演示批量查询多条记录
        String sql02 = "select id, name from customers where id < ?";
        List<User> list = queryList(User.class, sql02, 5);
        assert list != null;
        list.forEach(System.out::println);  // Customer{id=1, name='汪峰', email='null', birth=null} ...
    }

    /**
     * 带事务的通用更新操作(insert/delete/update)
     */
    private static void updateWithTx(Connection conn, String sql, Object... args) {
        PreparedStatement ps = null;
        try {
            // 1.预编译sql
            ps = conn.prepareStatement(sql);
            // 2.填充占位符
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1 , args[i]);
            }
            // 3.执行更新操作 execute()是否执行 | executeUpdate()返回影响记录数 | executeQuery()返回查询结果集
            int count = ps.executeUpdate();
            System.out.println("影响了 " + count + " 条记录");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 4.关闭ps,conn在外面单独关闭
            JdbcUtil.close(null, ps, null);
        }
    }

    /**
     * 通用查询单条记录方法
     */
    private static <T> T queryOne(Class<T> clazz, String sql, Object... args) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 1.获取连接
            conn = JdbcUtil.getDruidConnection();
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
            JdbcUtil.close(conn, ps, rs);
        }
        return null;
    }

    /**
     * 通用查询多条记录方法
     */
    private static <T> List<T> queryList(Class<T> clazz, String sql, Object... args) {
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        List<T> list = new ArrayList<>();
        try {
            // 1.获取连接
            conn = JdbcUtil.getDruidConnection();
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
            JdbcUtil.close(conn, ps, rs);
        }
        return null;
    }
}