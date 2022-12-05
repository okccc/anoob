package com.okccc.realtime.app.func;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.ververica.cdc.connectors.mysql.MySQLSource;
import com.alibaba.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.alibaba.ververica.cdc.debezium.DebeziumSourceFunction;
import com.okccc.realtime.util.DimUtil;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Author: okccc
 * Date: 2021/11/17 下午3:03
 * Desc: canal/maxwell是基于binlog抓最新数据,mysql中维度表的历史数据要先通过flink-cdc手动同步到hbase
 */
public class DimHistoryFunction {
    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 获取数据源
        DebeziumSourceFunction<String> sourceFunction = MySQLSource.<String>builder()
                .hostname("localhost")
                .port(3306)
                .databaseList("maxwell")
                // 需要同步历史数据的维度表
                .tableList("maxwell.user_info,maxwell.base_province,maxwell.sku_info,maxwell.spu_info,maxwell.base_trademark,maxwell.base_category3")
                .username("root")
                .password("root@123")
                .startupOptions(StartupOptions.initial())
                .deserializer(new MyDeserialization())
                .build();
        DataStreamSource<String> dataStream = env.addSource(sourceFunction);

        // 结构转换
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream.map(new MapFunction<String, JSONObject>() {
            @Override
            public JSONObject map(String value) {
                // {"database":"maxwell", "data":{"gender":"F","name":"moon"...}, "type":"insert", "table":"user_info"}
                return JSON.parseObject(value);
            }
        });

        // 写入hbase
        jsonStream.addSink(new RichSinkFunction<JSONObject>() {
            @Override
            public void invoke(JSONObject value, Context context) {
                /* {
                 *     "data":{
                 *         "birthday":11660,
                 *         "gender":"F",
                 *         "create_time":1607124346000,
                 *         "login_name":"king",
                 *         "name":"moon",
                 *         "user_level":"1",
                 *         "phone_num":"13528938229",
                 *         "id":3773,
                 *         "email":"xxx@gmall.com",
                 *         "operate_time":1607124525000
                 *     },
                 *     "type":"insert",
                 *     "database":"maxwell",
                 *     "table":"user_info"
                 * }
                 */

                // 获取hbase表名
                String tableName = "dim_" + value.getString("table");
                // 获取数据
                JSONObject data = value.getJSONObject("data");
                // 部分表要先过滤不需要的字段(可选)
                if ("dim_user_info".equals(tableName)) {
                    String column = "id,login_name,name,user_level,birthday,gender,create_time,operate_time";
                    List<String> columns = Arrays.asList(column.split(","));
                    Set<String> keys = data.keySet();
                    keys.removeIf(s -> !columns.contains(s));
                } else if ("dim_base_trademark".equals(tableName)) {
                    String column = "id,tm_name";
                    List<String> columns = Arrays.asList(column.split(","));
                    Set<String> keys = data.keySet();
                    keys.removeIf(s -> !columns.contains(s));
                }
                // 根据表名和data插入数据
                DimUtil.updateDimInfo(tableName, data);
            }
        });

        // 启动任务
        env.execute();
    }
}
