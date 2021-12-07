package com.okccc.realtime.app.func;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.bean.TableProcess;
import com.okccc.realtime.common.MyConfig;
import org.apache.flink.api.common.state.BroadcastState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReadOnlyBroadcastState;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Author: okccc
 * Date: 2021/10/19 上午11:16
 * Desc: 自定义广播处理函数,实现动态分流
 */
public class TableProcessFunction extends BroadcastProcessFunction<JSONObject, String, JSONObject> {
    // 声明侧输出流标签
    private final OutputTag<JSONObject> dimTag;
    // 声明全局广播状态
    private final MapStateDescriptor<String, TableProcess> mapState;
    // 声明数据库连接信息
    Connection conn;

    public TableProcessFunction(OutputTag<JSONObject> dimTag, MapStateDescriptor<String, TableProcess> mapState) {
        this.dimTag = dimTag;
        this.mapState = mapState;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // 通过反射加载驱动
        Class.forName(MyConfig.PHOENIX_DRIVER);
        // 创建连接
        conn = DriverManager.getConnection(MyConfig.PHOENIX_SERVER);
    }

    // 处理canal抓取的主流数据
    @Override
    public void processElement(JSONObject value, ReadOnlyContext ctx, Collector<JSONObject> out) throws Exception {
        /* {
         *     "data":[
         *         {
         *             "id":"1450387954178547736",
         *             "user_id":"1283",
         *             "nick_name":null,
         *         }
         *     ],
         *     "database":"maxwell",
         *     "es":1634634409000,
         *     "table":"comment_info",
         *     "ts":1634634413093,
         *     "type":"INSERT"
         * }
         */

        // 获取表和类型
        String table = value.getString("table");
        String type = value.getString("type").toLowerCase();

        // 非广播流对广播状态是只读的
        ReadOnlyBroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapState);
        // 从广播状态获取配置信息
        String key = table + ":" + type;
        System.out.println("baseStream>>> " + key);  // order_info:insert
        TableProcess tableProcess = broadcastState.get(key);

        // 判断是否能找到当前业务流操作对应的配置信息
        if (tableProcess != null) {
            // 添加输出表
            value.put("sink_table", tableProcess.getSinkTable());

            // 向下游传递数据之前,先将不需要的字段过滤掉
            // data={"tm_name":"华为", "logo_url":"/static/default.jpg", "id":12}
            JSONObject data = value.getJSONArray("data").getJSONObject(0);
            // 数组没有contains方法,可以先转换成列表,columns=[id, tm_name]
            List<String> columns = Arrays.asList(tableProcess.getSinkColumns().split(","));
//            System.out.println("过滤字段前的数据 " + data);
            Set<String> keys = data.keySet();
            keys.removeIf(s -> !columns.contains(s));
//            System.out.println("过滤字段后的数据 " + data);

            // 继续判断输出类型
            String sinkType = tableProcess.getSinkType();
            if (sinkType.equals(TableProcess.SINK_TYPE_KAFKA)) {
                // 事实数据放主流
                out.collect(value);
            } else if (sinkType.equals(TableProcess.SINK_TYPE_HBASE)){
                // 维度数据放侧输出流
                ctx.output(dimTag, value);
            }
        } else {
            // 没找到当前操作对应的配置信息
            System.out.println("TableProcess does not have this key " + key);
        }
    }

    // 处理flink-cdc抓取的广播流数据
    @Override
    public void processBroadcastElement(String value, Context ctx, Collector<JSONObject> out) throws Exception {
        /* {
         *     "database":"realtime",
         *     "data":{
         *         "operate_type":"insert",
         *         "sink_type":"hbase",
         *         "sink_table":"dim_base_trademark",
         *         "source_table":"base_trademark",
         *         "sink_extend":"",
         *         "sink_pk":"id",
         *         "sink_columns":"id,tm_name"
         *     },
         *     "type":"insert",
         *     "table":"table_process"
         * }
         */

        // 将字符串转换为JSON对象
        JSONObject jsonObj = JSON.parseObject(value);
        // 将配置表中的更新数据封装成java对象
        TableProcess tableProcess = JSON.parseObject(jsonObj.getString("data"), TableProcess.class);
        // 业务数据库表名：order_info/user_info...
        String sourceTable = tableProcess.getSourceTable();
        // 操作类型：insert/update/delete
        String operateType = tableProcess.getOperateType();
        // 输出类型：kafka/hbase
        String sinkType = tableProcess.getSinkType();
        // 输出表：dwd_order_info/dim_user_info...
        String sinkTable = tableProcess.getSinkTable();
        // 输出字段：id,name,age...
        String sinkColumns = tableProcess.getSinkColumns();
        // 主键：id
        String sinkPk = tableProcess.getSinkPk();
        // 扩展语句
        String sinkExtend = tableProcess.getSinkExtend();

        // 如果读取到的配置信息是维度表,就在hbase自动创建对应的表,而不是在外面手动创建
        if (sinkType.equals(TableProcess.SINK_TYPE_HBASE) && "insert".equals(operateType)) {
            checkTable(sinkTable, sinkPk, sinkColumns, sinkExtend);
        }

        // 广播流对广播状态是可读可写的
        BroadcastState<String, TableProcess> broadcastState = ctx.getBroadcastState(mapState);
        // 往广播状态添加配置信息
        String key = sourceTable + ":" + operateType;
        System.out.println("broadcastStream>>> " + key);  // order_info:insert
        broadcastState.put(key, tableProcess);
    }

    // 通过phoenix在hbase创建表
    private void checkTable(String tableName, String pk, String fields, String ext) throws SQLException {
        // 判断主键和扩展语句
        if (pk == null) {
            pk = "id";
        }
        if (ext == null) {
            ext = "";
        }
        // 拼接建表语句
        StringBuilder sb = new StringBuilder("create table if not exists " + MyConfig.HBASE_SCHEMA + "." + tableName + "(");
        // 获取所有字段
        String[] arr = fields.split(",");
        for (int i = 0; i < arr.length; i++) {
            String field = arr[i];
            if (field.equals(pk)) {
                // 为了方便读写数据,字段类型都用VARCHAR
                sb.append(field + " VARCHAR primary key");
            } else {
                sb.append(field + " VARCHAR");
            }
            if (i < arr.length - 1) {
                sb.append(",");
            }
        }
        sb.append(")" + ext);
//        System.out.println("phoenix的建表语句是 " + sb);

        PreparedStatement ps = null;
        try {
            // 预编译sql
            ps = conn.prepareStatement(sb.toString());
            // 执行sql
            ps.execute();
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("phoenix创建表失败");
        } finally {
            // 释放资源,conn只在初始化时创建了一次,不需要关闭,不然后面数据进来就没有连接了,程序关闭时conn自然会关闭
            if (ps != null) {
                ps.close();
            }
        }
    }
}
