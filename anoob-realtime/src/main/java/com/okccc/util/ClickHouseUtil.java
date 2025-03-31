package com.okccc.util;

import com.okccc.app.bean.ConfigInfo;
import com.okccc.app.bean.TransientSink;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @Author: okccc
 * @Date: 2021/10/27 10:11:22
 * @Desc: flink写clickhouse的工具类
 */
public class ClickHouseUtil {

    /**
     * flink-connector-jdbc将脏数据写入clickhouse
     */
    public static SinkFunction<String> getJdbcSink() {
        return JdbcSink.sink(
                "insert into realtime.dirty_log values(?,?)",
                new JdbcStatementBuilder<String>() {
                    @Override
                    public void accept(PreparedStatement ps, String log) throws SQLException {
                        // nginx原始日志
                        ps.setString(1, log);
                        // 当前数据插入时间
                        ps.setObject(2, DateUtil.getCurrentDateTime());
                    }
                },
                new JdbcExecutionOptions.Builder().withBatchSize(1).build(),
                new JdbcConnectionOptions
                        .JdbcConnectionOptionsBuilder()
                        .withDriverName(ConfigInfo.CLICKHOUSE_DRIVER)
                        .withUrl(ConfigInfo.CLICKHOUSE_URL)
                        .withUsername(ConfigInfo.CLICKHOUSE_USER)
                        .withPassword(ConfigInfo.CLICKHOUSE_PASSWORD)
                        .build()
        );
    }

    /**
     * flink-connector-jdbc将java bean写入clickhouse
     * 使用泛型时返回值类型前面为什么要加<T>,不然怎么知道它是泛型呢？如果刚好有个JavaBean就叫<T>咋办呢？
     */
    public static <T> SinkFunction<T> getJdbcSink(String sql) {
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
                                // 有就直接跳过不处理,并且将索引值-1,这里不能写成i--,不然会和上面i++形成死循环
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
                        .withDriverName(ConfigInfo.CLICKHOUSE_DRIVER)
                        .withUrl(ConfigInfo.CLICKHOUSE_URL)
                        .withUsername(ConfigInfo.CLICKHOUSE_USER)
                        .withPassword(ConfigInfo.CLICKHOUSE_PASSWORD)
                        .build()
        );
    }
}
