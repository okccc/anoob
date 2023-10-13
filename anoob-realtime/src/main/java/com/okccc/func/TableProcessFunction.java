package com.okccc.func;

import com.alibaba.fastjson.JSONObject;
import com.okccc.app.bean.TableProcess;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;

import java.sql.*;
import java.util.*;

/**
 * @Author: okccc
 * @Date: 2023/3/9 16:14:28
 * @Desc: 自定义BroadcastProcessFunction,根据广播状态的配置信息从主流中匹配出维度数据
 *
 * Caused by: java.sql.SQLException: ERROR 726 (43M10): Inconsistent namespace mapping properties. Cannot initiate
 * connection as SYSTEM:CATALOG is found but client does not have phoenix.schema.isNamespaceMappingEnabled enabled
 * 原因：phoenix不能直接操作schema,要在hbase-site.xml开启namespace映射权限
 *
 * org.apache.phoenix.schema.SchemaNotFoundException: ERROR 722 (43M05): Schema does not exist schemaName=DIM
 * 原因：phoenix操作表之前要先创建好所属的schema
 */
public class TableProcessFunction extends BroadcastProcessFunction<JSONObject, JSONObject, JSONObject> {

    // 声明广播状态,由构造函数传入
    private final MapStateDescriptor<String, TableProcess> mapStateDescriptor;

    // 声明预加载配置,和广播状态类型一致
    private final Map<String, TableProcess> preloadConfig = new HashMap<>();

    public TableProcessFunction(MapStateDescriptor<String, TableProcess> mapStateDescriptor) {
        this.mapStateDescriptor = mapStateDescriptor;
    }

    // 优化：主流和广播流没有先后顺序,谁先进来就处理谁,任务启动时就预加载配置信息,这样主流进来即使拿不到广播状态也能从内存中获取配置信息
    @Override
    public void open(Configuration parameters) throws Exception {
        // 通过反射加载驱动
        Class.forName("com.mysql.cj.jdbc.Driver");
        // 创建连接
        String url = "jdbc:mysql://localhost:3306/dim_config?user=root&password=root@123&useSSL=false&useUnicode=true&charset=utf8&TimeZone=Asia/Shanghai";
        Connection conn = DriverManager.getConnection(url);
        // 预编译sql
        PreparedStatement ps = conn.prepareStatement("select * from table_process");
        // 执行查询,返回结果集
        ResultSet rs = ps.executeQuery();
        // 获取结果集的元数据信息
        ResultSetMetaData md = rs.getMetaData();
        // 遍历结果集
        while (rs.next()) {
            JSONObject jsonObject = new JSONObject();
            for (int i = 0; i < md.getColumnCount(); i++) {
                String columnName = md.getColumnName(i + 1);
                Object columnValue = rs.getObject(i + 1);
                jsonObject.put(columnName, columnValue);
            }
            TableProcess tableProcess = jsonObject.toJavaObject(TableProcess.class);
            // 添加到预加载配置
            preloadConfig.put(tableProcess.getSourceTable(), tableProcess);
        }
        // 释放资源
        rs.close();
        ps.close();
        conn.close();
    }

    // Maxwell抓取的主流数据包含所有事实表和维度表
    @Override
    public void processElement(JSONObject value, BroadcastProcessFunction<JSONObject, JSONObject, JSONObject>.ReadOnlyContext ctx, Collector<JSONObject> out) throws Exception {
        /**
         * {
         *     "database": "mock",
         *     "data": {
         *         "id": 3992,
         *         "name": "grubby",
         *         "phone": "13754211248",
         *         "birthday": "1999-12-04",
         *         "create_time": "2020-12-04 23:28:45",
         *     },
         *     "table": "user_info",
         *     "type": "insert",
         *     "ts": 1690793186  # maxwell抓取数据的时间戳单位是秒不是毫秒
         * }
         */

        // 1.获取广播状态
        ReadOnlyBroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapStateDescriptor);
        String sourceTable = value.getString("table");

        // 2.读取配置信息,先去广播状态拿,没有再去预加载配置拿
        TableProcess tableProcess;
        if ((tableProcess = broadcastState.get(sourceTable)) != null || (tableProcess = preloadConfig.get(sourceTable)) != null) {
            // 3.处理维度表数据
            JSONObject data = value.getJSONObject("data");
            // 剔除多余字段,data={"id":12, "tm_name":"华为", "logo_url":"/static/default.jpg"}
            Set<String> keys = data.keySet();
            // 数组没有contains方法,可以先转换成列表,columns=[id, tm_name]
            List<String> sinkColumns = Arrays.asList(tableProcess.getSinkColumns().split(","));
//            System.out.println("过滤字段前的数据：" + data);
            keys.removeIf(key -> !sinkColumns.contains(key));
//            System.out.println("过滤字段后的数据：" + data);

            // 补充目的表和主键
            value.put("sink_table", tableProcess.getSinkTable());
            value.put("sink_pk", tableProcess.getSinkPk());

            // 往下游发送
            out.collect(value);
        } else {
            // 处理事实表数据,这里直接忽略,其实也可以通过侧输出流写入dwd层对应topic
            System.out.println("该表不是维度表 " + sourceTable);
        }
    }

    // FlinkCDC抓取的广播流数据只包含维度表
    @Override
    public void processBroadcastElement(JSONObject value, BroadcastProcessFunction<JSONObject, JSONObject, JSONObject>.Context ctx, Collector<JSONObject> out) throws Exception {
        /**
         * {
         *     "before":null,
         *     "after":{
         *         "source_table":"aaa",
         *         "sink_table":"dim_aaa",
         *         "sink_columns":"id,name,age",
         *         "sink_pk":"id",
         *         "sink_extend":null
         *     },
         *     "source":{
         *         "db":"dim_config",
         *         "table":"table_process"
         *     },
         *     "op":"c",
         *     "ts":1694849590315,
         * }
         */

        // 分别执行crud查看结果 insert into table_process values('aaa','dim_aaa','id,name,age','id',null);
        // {"op":"r","before":null,"after":{"k1":"v1","k2":"v2"},"ts":1694848659094}
        // {"op":"c","before":null,"after":{"k1":"v1","k2":"v2"},"ts":1694849590315}
        // {"op":"u","before":{"k1":"v1","k2":"v2"},"after":{"k1":"v1","k2":"v2"},"ts":1694849684689}
        // {"op":"d","before":{"k1":"v1","k2":"v2"},"after":null,"ts":1694849727135}

        // 输出3次说明主流的3个并行度都能获取到广播状态
//        System.out.println(value);

        // 1.获取广播状态,这里可以打断点Debug观察广播状态的key和value
        BroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapStateDescriptor);

        // 2.判断操作类型
        String op = value.getString("op");
        if ("d".equals(op)) {
            // 如果维度表删除了,对应的广播状态和预加载配置也要删除
            String sourceTable = value.getJSONObject("before").getString("source_table");
            broadcastState.remove(sourceTable);
            preloadConfig.remove(sourceTable);
        } else {
            // 否则就将配置信息更新到广播状态和预加载配置
            TableProcess tableProcess = value.getObject("after", TableProcess.class);
            String sourceTable = tableProcess.getSourceTable();
            broadcastState.put(sourceTable, tableProcess);
            preloadConfig.put(sourceTable, tableProcess);
        }
    }
}
