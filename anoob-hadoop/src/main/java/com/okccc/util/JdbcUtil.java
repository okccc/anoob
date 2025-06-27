package com.okccc.util;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.CaseFormat;
import com.okccc.bean.UserInfo;
import org.apache.commons.beanutils.BeanUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @Author: okccc
 * @Date: 2022/12/9 11:20
 * @Desc: jdbc通用工具类,包括mysql、hive、phoenix、clickhouse
 * https://github.com/alibaba/druid/wiki/DruidDataSource%E9%85%8D%E7%BD%AE%E5%B1%9E%E6%80%A7%E5%88%97%E8%A1%A8
 *
 * JDBC(Java DataBase Connectivity)
 * jdbc是sun公司提供的操作数据库的接口java.sql.Driver,不同数据库厂商会提供相应的驱动类,比如
 * com.mysql.cj.jdbc.Driver、org.apache.hive.jdbc.HiveDriver、ru.yandex.clickhouse.ClickHouseDriver
 * 将数据库连接信息放到配置文件的好处：更换不同数据库时只改配置不改代码,开发人员面向接口编程
 *
 * ORM(object relation mapping)
 * mysql表和java类一一对应,表的一行对应类的一个对象,表的一列对应类的一个属性
 * 普通连接：java.sql.DriverManager 每次都会创建新的连接,如果连接过多或程序异常未及时关闭,可能会OOM甚至服务器崩溃
 * 数据库连接池：javax.sql.DataSource 会保持最小连接数,可以重复使用空闲连接,减少频繁创建和销毁连接带来的性能损耗,响应速度更快
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
 *
 * com.alibaba.druid.pool.DruidAbstractDataSource - discard long time none received connection.
 * jdbcUrl : jdbc:mysql://10.162.13.26:3306, version : 1.2.5, lastPacketReceivedIdleMillis : 163669
 * 为什么要清空空闲60秒以上的连接?
 * 阿里给数据库设置的空闲等待时间是60秒,mysql数据库到了空闲等待时间将关闭空闲连接,以提升数据库服务器的处理能力
 * mysql默认空闲等待时间wait_timeout=8小时,如果数据库主动关闭了空闲连接而连接池并不知道,还在使用这个连接就会产生异常
 * 解决：升级druid版本,或者启动程序时在运行参数中添加：-Ddruid.mysql.usePingMethod=false
 */
public class JdbcUtil {

    /**
     * 创建数据库连接
     */
    public static Connection getConnection() throws Exception {
//        // 加载mysql驱动,Driver类是第三方api,可以改进为只用sun公司提供的java.sql包下的接口
//        Driver driver = new Driver();
//        // 数据库地址
//        String url = "jdbc:mysql://localhost:3306/test";
//        // 用户信息
//        Properties prop = new Properties();
//        prop.setProperty("user", "root");
//        prop.setProperty("password", "root");
//        // 获取连接
//        Connection conn = driver.connect(url, prop);

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

        // 读取配置文件
        Properties prop = new Properties();
        prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties"));

        // 获取连接信息
        String driver = prop.getProperty("jdbc.driver");
        String url = prop.getProperty("jdbc.url");
        String user = prop.getProperty("jdbc.user");
        String password = prop.getProperty("jdbc.password");

        // 通过反射加载驱动
        Class.forName(driver);

        // 获取连接
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
        // 存放结果集的列表
        ArrayList<T> list = new ArrayList<>();

        // 预编译sql
        PreparedStatement ps = conn.prepareStatement(sql);

        // 如果数据量很大,JDBC一次性加载所有数据可能会OOM,开启流式查询逐行读取减轻内存压力
        ps.setFetchSize(1000);

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

    /**
     * 批量更新插入(upsert)
     */
    public static void upsert(Connection conn, List<String> records, String table, String columns) throws Exception {
        // replace into是先delete再insert,直接删数据有点危险不建议
        // ON DUPLICATE key update导致主键ID跳跃增长,很快突破int类型最大值 https://blog.csdn.net/ke2602060221/article/details/126143254
        // url添加&allowMultiQueries=true支持多条sql语句执行,每次执行insert前先调用alter table t1 auto_increment=1;性能会稍微降低(不推荐)
        // INSERT INTO t1 VALUES(...)    常规insert语句
        // ON DUPLICATE KEY              当插入数据发生主键(Primary Key)或唯一键(Unique Key)冲突时就执行更新操作
        // UPDATE k1=v1,k2=v2...         常规update语句
        // 优化1：JDBC添加&rewriteBatchedStatements=true批处理参数,INSERT INTO t1 VALUES (...), (...) 大大提升写入速度

        StringBuilder sb = new StringBuilder();
        // 拼接INSERT语句
        sb.append("INSERT INTO ").append(table).append(" (");
        for (String column : columns.split(",")) {
            sb.append(column).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(") VALUES (");
        // 拼接占位符
        sb.append("?,".repeat(columns.split(",").length));
        sb.deleteCharAt(sb.length() - 1);
        // 拼接UPDATE语句
        sb.append(") ON DUPLICATE KEY UPDATE ");
        for (String column : columns.split(",")) {
            sb.append(column).append("=VALUES(").append(column).append("),");
        }
        sb.deleteCharAt(sb.length() - 1);
        // 完整SQL语句
        String sql = sb.toString();
        // INSERT INTO user_info (user_name,age) VALUES (?,?) ON DUPLICATE KEY UPDATE user_name=VALUES(user_name),age=VALUES(age)
        System.out.println(sql);

        try {
            // 关闭自动提交事务
            conn.setAutoCommit(false);

            // 预编译sql
            PreparedStatement ps = conn.prepareStatement(sql);

            // 优化2：数据量很大时要控制批次大小,所有数据都放一个batch可能会OOM
            int batchSize = 1000;
            int count = 0;

            // 遍历结果集
            for (String record : records) {
                // 填充占位符
                String[] arr = record.split(",");
                for (int i = 0; i < arr.length; i++) {
                    ps.setObject(i + 1, arr[i]);
                }
                // 攒一批sql
                ps.addBatch();
                count ++;
                // 分批执行
                if (count % batchSize == 0) {
                    // 执行批处理,executeBatch()无法精确统计影响的记录数,executeUpdate()可以但是效率很低
                    ps.executeBatch();
                    // 优化3：事务也要分批次提交,所有sql都放一个大事务持续时间很长,容易锁表或binlog无法刷新,如果失败回滚效率也很差
                    conn.commit();
                    ps.clearBatch();
                    System.out.println("已提交 batch ,当前行数：" + count);
                }
            }

            // 提交最后一批
            if (count % batchSize != 0) {
                ps.executeBatch();
                conn.commit();
                System.out.println("提交最后 batch ,总行数：" + count);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 有异常就回滚事务
            conn.rollback();
        }
    }

    /**
     * 演示批量插入100万条数据
     */
    public static void insertBatch(Connection conn, String sql) throws Exception {
        long start = System.currentTimeMillis();
        try {
            // 关闭自动提交
            conn.setAutoCommit(false);

            // 预编译sql
            PreparedStatement ps = conn.prepareStatement(sql);

            // 填充占位符
            for (int i = 1; i <= 1000999; i++) {
                ps.setObject(1, "hello" + i);
                // 挨个执行单条sql性能很差,插入100万条数据要好几个小时
//                ps.execute();
                // 攒一批sql
                ps.addBatch();
                // 分批执行,插入100万条数据只要30秒,如果关闭自动提交事务只要5秒
                if (i % 1000 == 0) {
                    ps.executeBatch();
                    conn.commit();
                    ps.clearBatch();
                    System.out.println("已提交 batch, 当前行数：" + i);
                }
            }

            // 提交最后一批
            ps.executeBatch();
            conn.commit();
            System.out.println("提交最后 batch");
        } catch (Exception e) {
            e.printStackTrace();
            // 有异常就回滚事务
            conn.rollback();
        }
        System.out.println("插入100万条数据耗时(ms): " + (System.currentTimeMillis() - start));
    }

    /**
     * 批量修改(update、delete)
     */
    public static void update(Connection conn, String sql, Object ... args) throws Exception {
        try {
            // 关闭自动提交
            conn.setAutoCommit(false);

            // 预编译sql
            PreparedStatement ps = conn.prepareStatement(sql);

            // 填充占位符
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }

            // 执行更新操作：execute是否执行,executeUpdate返回影响记录数
            ps.executeUpdate();

            // 手动提交
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
            // 有异常就回滚事务
            conn.rollback();
        }
    }

    /**
     * 演示带事务的更新
     */
    public static void updateWithTx(Connection conn) throws Exception {
        // 关闭自动提交
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

            // 当同一个事务内的所有操作都完成后手动提交
            conn.commit();
        } catch (Exception e) {
            e.printStackTrace();
            // 有异常就回滚事务
            conn.rollback();
        }
    }

    public static void main(String[] args) throws Exception {
        // 获取数据库连接
        String driverClass = "com.mysql.cj.jdbc.Driver";
        String jdbcUrl = "jdbc:mysql://localhost:3306/test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&rewriteBatchedStatements=true&allowMultiQueries=true";
        String username = "root";
        String password = "Q-D37Sq*k61#yT_y";
        DruidDataSource dataSource = getDataSource(driverClass, jdbcUrl, username, password);
        DruidPooledConnection conn = dataSource.getConnection();

        // 演示更新插入
        // create table user_info(id int primary key auto_increment, user_name varchar(10) unique key, age int);
        ArrayList<String> records = new ArrayList<>();
        records.add("grubby, 19");
        records.add("moon, 19");
        records.add("sky, 20");
        upsert(conn, records, "user_info", "user_name,age");

        // 演示批量查询
        // 当表的列名和类的属性名不一致时,要用guava将下划线转换成驼峰,或者手动添加属性名作为列名的别名
        String sql = "select user_id,user_name,age from user_info limit 1";
        System.out.println(queryList(conn, sql, UserInfo.class, true));  // [UserInfo(userId=1, userName=grubby, age=18)]
        System.out.println(queryList(conn, sql, JSONObject.class, false));  // [{"user_id":1,"user_name":"grubby","age":18}]

        // 演示批量插入
        // create table aaa(id int primary key auto_increment, name varchar(20));
        insertBatch(conn, "INSERT INTO test.aaa (name) VALUES(?)");

        // 演示批量修改
        update(conn, "update user_info set age = ? where user_id = ?", 19, 1);

        // 演示事务操作
        updateWithTx(conn);
    }
}