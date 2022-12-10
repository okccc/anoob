package com.jiliguala.label;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: tim
 * @Date: 2022/12/6 2:54 下午
 * @Desc: 每天凌晨校验标签,将hive数据刷到mysql
 */
public class ValidateStudentInfo {

    public static void main(String[] args) throws Exception {
        // hive表、列、分区
        String hiveTable = "jlgl_ads.ads_jlgg_tutor_user_list_all_h";
        String columns = "user_id,gua_id,region,leads_order_id,leads_order_pay_time,user_tel_number,preview_lesson_finish_cnt,experience_lesson_finish_cnt,leads_salesman_tid,leads_salesman_term_id,time_removed,create_time,update_time";
        String dt = DateTime.now().toString("yyyyMMdd");
        // 查询的结果集
        ArrayList<String> records = new ArrayList<>();

        // 查询hive数据
        String driver = "org.apache.hive.jdbc.HiveDriver";
        String url = "jdbc:hive2://10.201.7.140:7001";
        String user = "hive";
        String password = "4XUY5mIg1dZlqVp2";
        Class.forName(driver);
        Connection conn =  DriverManager.getConnection(url, user, password);
        Statement statement = conn.createStatement();
        String sql = "select * from " + hiveTable + " where dt=" + dt + " and ht=23";
        ResultSet rs = statement.executeQuery(sql);
        while (rs.next()) {
            StringBuilder sb = new StringBuilder();
            for (String column : columns.split(",")) {
                String value = rs.getString(column);
                if ("leads_order_pay_time".equals(column)) {
                    sb.append(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime(value).toDate().getTime()).append(",");
                } else {
                    sb.append(value).append(",");
                }
            }
            records.add(sb.substring(0, sb.length() - 1));
        }
        System.out.println(records);

        // 刷新到mysql
        flushToMysql(records, columns);

    }

    public static void flushToMysql(List<String> records, String columns) throws Exception {
        Connection conn = null;
        try {
            // 数据库连接
            String driver = "com.mysql.cj.jdbc.Driver";
//        String url = "jdbc:mysql://10.60.107.204:3306/keira?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8&allowMultiQueries=true";
            String url = "jdbc:mysql://10.100.128.33:3306/keira?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8&allowMultiQueries=true";
            String username = "keira";
//        String password = "T5!YIWf#y0wUaH0g";
            String password = "fwlx$l8kQB#q";
            Class.forName(driver);
            conn = DriverManager.getConnection(url, username, password);
            conn.setAutoCommit(false);

            // 拼接sql语句
            // replace into是先delete再insert,直接删数据有点危险不建议
            // mysql的upsert操作ON DUPLICATE key update导致主键ID跳跃增长,很快突破int类型最大值 https://blog.csdn.net/ke2602060221/article/details/126143254
            // jdbc.url添加&allowMultiQueries=true支持多条sql语句执行,每次执行insert前先调用alter table t1=1;性能会稍微降低
            // INSERT INTO t1 VALUES(...) 常规insert语句
            // ON DUPLICATE KEY 表示后面语句是当数据有冲突时才会执行,什么属性的字段会有冲突呢？主键(Primary Key)和唯一索引(Unique Key)
            // UPDATE k1=v1,k2=v2... 常规update语句
            StringBuilder sb = new StringBuilder();
            sb.append("alter table dw_student_info_real_time auto_increment=1;insert into dw_student_info_real_time values (null,?,?,?,?,?,?,?,?,?,?,?,?,?) on duplicate key update ");
            for (String column : columns.split(",")) {
                sb.append(column).append("=values(").append(column).append("),");
            }
            String sql = sb.substring(0, sb.length() - 1);
            System.out.println(sql);

            // 执行sql
            PreparedStatement ps = conn.prepareStatement(sql);
            for (String record : records) {
                String[] arr = record.split(",");
                for (int i = 0; i < arr.length; i++) {
                    ps.setObject(i + 1, arr[i]);
                }
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            if (conn != null) {
                conn.rollback();
            }
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
    }
}
