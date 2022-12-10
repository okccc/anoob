package com.okccc.realtime.app.ods;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.util.HdfsUtil;
import com.okccc.realtime.util.HiveUtil;
import com.okccc.realtime.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * @Author: okccc
 * @Date: 2022/1/24 2:46 下午
 * @Desc: 除了sqoop/datax以外,还可以手动同步mysql某一天历史数据到hive
 */
public class MysqlToHdfs {

    private static final Logger logger = LoggerFactory.getLogger(MysqlToHdfs.class);

    public static void main(String[] args) throws Exception {
        // 接收传递参数
        String env = "fat";
        String dsn = "mysql_eduplatform0_fat";
        String table = "knowledge_record0";
        String column = "create_time";
        String dt = "20220126";

        // 1.解析apollo地址获取mysql连接信息
        String data = HttpUtil.login(env);
        JSONArray jsonArray = JSON.parseArray(data).getJSONObject(0).getJSONArray("items");
//        System.out.println(jsonArray);
        // 存放所有key-value的集合
        HashMap<String, JSONObject> hashMap = new HashMap<>();
        // 遍历json数组
        for (Object value : jsonArray) {
            JSONObject jsonObject = JSON.parseObject(value.toString());
            JSONObject item = jsonObject.getJSONObject("item");
            hashMap.put(item.getString("key"), item.getJSONObject("value"));
        }
//        System.out.println(hashMap);
        JSONObject value = hashMap.get(dsn);  // ${dsn}
        String url = value.getString("url");
        String username = value.getString("username");
        String password = value.getString("password");
        System.out.println(url +" "+username+" "+password);

        // 2.连接数据库查询数据
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection conn = DriverManager.getConnection(url, username, password);
        System.out.println(conn);

        // 存放结果集的列表
        List<String> list = new ArrayList<>();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 预编译sql
            String sql = "select * from " + table + " where replace(substr(" + column + ",1,10),'-','')" + "='" + dt + "';";
            System.out.println(sql);
            ps = conn.prepareStatement(sql);
            // 执行查询,返回结果集
            rs = ps.executeQuery();
            // 获取结果集的元数据信息
            ResultSetMetaData md = rs.getMetaData();
            // 遍历结果集
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < md.getColumnCount(); i++) {
                    // 从元数据获取字段名称,从结果集获取字段值
                    Object columnValue = rs.getObject(i + 1);
                    System.out.println(columnValue);
                    if (columnValue != null) {
                        if (i == md.getColumnCount()-1) {
                            sb.append(columnValue);
                        } else {
                            sb.append(columnValue).append("\001");
                        }
                    } else {
                        sb.append(" ");
                    }
                }
                // 添加到列表
                list.add(sb.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        } finally {
            // 释放资源
            close(conn, ps, rs);
        }
        // 返回结果集
        System.out.println(list);

        // 3.将查询数据写入hdfs
        HdfsUtil.overwriteToHdfs("/data/hive/warehouse/ods.db/ods_" + table + "/dt=" + dt + "/" + UUID.randomUUID(), list);

        // 4.msck修复hive读不到hdfs数据
        HiveUtil.msck("ods.ods_" + table);
    }

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
}
