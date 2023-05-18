package com.okccc.db;

import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.alibaba.fastjson.JSONObject;
import com.okccc.bean.Order;
import com.okccc.bean.User;
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
 *
 * 常见错误
 * Caused by: com.mysql.cj.exceptions.CJCommunicationsException:
 * The last packet successfully received from the server was 5,811,951 milliseconds ago.
 * The last packet sent successfully to the server was 5,812,024 milliseconds ago.
 * is longer than the server configured value of 'wait_timeout'.
 * You should consider either expiring and/or testing connection validity before use in your application, increasing the server
 * configured values for client timeouts, or using the Connector/J connection property 'autoReconnect=true' to avoid this problem.
 * 原因：当数据库重启或当前连接空闲时间超过mysql的wait_timeout,数据库会强行断开链接
 * 解决：服务端调大wait_timeout(不建议),url设置autoReconnect=true(mysql5以后已失效),客户端使用前先conn.isValid()校验是否有效(推荐)
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
        prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("jdbc.properties"));
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

    /**
     * 批量更新插入(upsert)
     */
    public static void upsert(List<String> records, String table, String columns) throws Exception {
        // 拼接upsert语句
        // replace into是先delete再insert,直接删数据有点危险不建议
        // mysql的upsert操作ON DUPLICATE key update导致主键ID跳跃增长,很快突破int类型最大值 https://blog.csdn.net/ke2602060221/article/details/126143254
        // url添加&allowMultiQueries=true支持多条sql语句执行,每次执行insert前先调用alter table t1 auto_increment=1;性能会稍微降低
        // INSERT INTO t1 VALUES(...) 常规insert语句
        // ON DUPLICATE KEY 表示后面语句是当数据有冲突时才会执行,什么属性的字段会有冲突呢？主键(Primary Key)和唯一索引(Unique Key)
        // UPDATE k1=v1,k2=v2... 常规update语句
        StringBuilder sb = new StringBuilder();
        sb.append("alter table ").append(table).append(" auto_increment=1;");
        sb.append("insert into ").append(table).append(" values (null,?,?) on duplicate key update ");
        for (String column : columns.split(",")) {
            sb.append(column).append("=values(").append(column).append("),");
        }
        String sql = sb.substring(0, sb.length() - 1);
        // alter table user_info auto_increment=1;insert into user_info values (null,?,?) on duplicate key update name=values(name),age=values(age)
        System.out.println(sql);

        // 1.获取连接
        if (conn == null) {
            initDruidConnection();
        }
        try {
            // 关闭自动提交事务
            conn.setAutoCommit(false);
            // 2.预编译sql
            PreparedStatement ps = conn.prepareStatement(sql);
            // 遍历结果集
            for (String record : records) {
                // 3.填充占位符
                String[] arr = record.split(",");
                for (int i = 0; i < arr.length; i++) {
                    ps.setObject(i + 1, arr[i]);
                }
                // 攒一批sql
                ps.addBatch();
            }
            // 执行批处理
            ps.executeBatch();
            // 所有语句都执行完手动提交
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
            // 事务失败回滚
            conn.rollback();
        }
    }

    /**
     * 演示批量插入100万条数据
     */
    public static void insertBatch() throws Exception {
        long start = System.currentTimeMillis();
        // 1.获取连接
        if (conn == null) {
            initDruidConnection();
        }
        try {
            // 关闭自动提交
            conn.setAutoCommit(false);
            // 2.预编译sql
            String sql = "insert into test.aaa values (null,?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            // 3.填充占位符
            for (int i = 1; i <= 1000000; i++) {
                ps.setObject(1, "hello" + i);
                // 挨个执行单条sql性能很差,插入100万条数据要好几个小时
//                ps.execute();
                // 攒一批sql
                ps.addBatch();
                if (i % 1000 == 0) {
                    // 执行批处理,插入100万条数据只要30秒,如果关闭自动提交事务只要5秒
                    // 要给url添加&rewriteBatchedStatements=true否则批处理不生效
                    ps.executeBatch();
                    // 清理批
                    ps.clearBatch();
                }
            }
            // 手动提交
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("插入100万条数据耗时(ms): " + (System.currentTimeMillis() - start));
    }

    /**
     * 批量修改(update、delete)
     */
    public static void update(String sql, Object ... args) throws Exception {
        // 1.获取连接
        if (conn == null) {
            initDruidConnection();
        }
        try {
            // 关闭自动提交事务
            conn.setAutoCommit(false);
            // 2.预编译sql
            PreparedStatement ps = conn.prepareStatement(sql);
            // 3.填充占位符
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            // 4.执行更新操作：execute是否执行,executeUpdate返回影响记录数
            int i = ps.executeUpdate();
            System.out.println(i + " rows affected");
            // 5.手动提交事务
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
            // 事务失败回滚
            conn.rollback();
        }
    }

    /**
     * 演示带事务的更新
     */
    public static void updateWithTx() throws Exception {
        // 获取连接
        if (conn == null) {
            initDruidConnection();
        }
        // 关闭自动提交事务
        conn.setAutoCommit(false);
        try {
            // 事务操作1
            String sql01 = "update test.user_account set balance = balance - 100 where id = 1";
            PreparedStatement ps01 = conn.prepareStatement(sql01);
            ps01.executeUpdate();
            // 模拟异常
            System.out.println(1/0);
            // 事务操作2
            String sql02 = "update test.user_account set balance = balance + 100 where id = 2";
            PreparedStatement ps02 = conn.prepareStatement(sql02);
            ps02.executeUpdate();
            // 当一个事务内的操作都完成后,手动提交事务
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.toString());
            // 有异常就回滚事务
            conn.rollback();
        }
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

        ArrayList<String> records = new ArrayList<>();
        records.add("grubby,18");
        records.add("moon,19");
        records.add("sky,20");
        upsert(records, "user_info", "name,age");

        insertBatch();

        String sql05 = "update `order` set order_date = ? where order_id = ?";
        update(sql05, "2022-12-01", 2);

        updateWithTx();
    }
}