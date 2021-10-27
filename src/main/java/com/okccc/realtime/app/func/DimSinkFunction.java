package com.okccc.realtime.app.func;

import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.common.MyConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Author: okccc
 * Date: 2021/10/24 下午1:33
 * Desc: 通过phoenix将维度数据写入hbase
 */
public class DimSinkFunction extends RichSinkFunction<JSONObject> {
    // 声明数据库连接信息
    Connection conn;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // 通过反射加载驱动
        Class.forName(MyConfig.PHOENIX_DRIVER);
        // 创建连接
        conn = DriverManager.getConnection(MyConfig.PHOENIX_SERVER);
    }

    @Override
    public void invoke(JSONObject value, Context context) throws Exception {
        /* {
         *     "data": [
         *         {
         *             "tm_name": "联想",
         *             "id": "13"
         *         }
         *     ],
         *     "type": "INSERT",
         *     "database": "maxwell",
         *     "sink_table": "dim_base_trademark",
         *     "table": "base_trademark",
         *     "ts": 1635131300181
         * }
         */

        // 获取hbase表名
        String tableName = value.getString("sink_table");
        // 获取插入表的字段和对应值
        JSONObject data = value.getJSONArray("data").getJSONObject(0);
        String fields = StringUtils.join(data.keySet(), ",");
        String values = StringUtils.join(data.values(), "','");
        // 拼接sql
        String sql = "upsert into " + MyConfig.HBASE_SCHEMA + "." + tableName + " (" + fields + ") " + "values('" + values + "')";
//        System.out.println("phoenix的插入语句是 " + sql);  // upsert into realtime.dim_base_trademark values('联想','12')

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("phoenix插入数据失败");
        } finally {
            if (ps != null) {
                ps.close();
            }
        }
    }
}
