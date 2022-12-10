package com.okccc.flink;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.ververica.cdc.connectors.mysql.MySQLSource;
import com.alibaba.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.alibaba.ververica.cdc.debezium.DebeziumDeserializationSchema;
import io.debezium.data.Envelope;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.util.Collector;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * @Author: okccc
 * @Date: 2021/8/27 上午10:22
 * @Desc: flink-cdc动态读取mysql数据
 */
public class FlinkCdc {
    public static void main(String[] args) throws Exception {
        /*
         * CDC(ChangeDataCapture): 监控并捕获数据库的insert/update/delete记录,按顺序写入消息队列供下游服务订阅和消费
         * Flink-CDC: 通过flink-cdc-connectors组件直接从mysql和pg等数据库读取全量或增量的变更数据,连kafka中间件都省了
         * github地址：https://github.com/ververica/flink-cdc-connectors
         *
         * console输出日志
         * 十月 07, 2021 6:41:20 下午 com.github.shyiko.mysql.binlog.BinaryLogClient connect
         * 信息: Connected to localhost:3306 at mysql-bin.000002/154 (sid:6388, cid:7)
         * 使用flink-cdc时要关闭canal/maxwell,不然可能抓不到数据
         *
         * 常见错误
         * Access denied; you need (at least one of) the RELOAD privilege(s) for this operation
         * 需要dba赋权：grant reload on *.* to 'username'@'%'
         * Caused by: com.mysql.cj.exceptions.CJCommunicationsException: Communications link failure
         * 公司生产环境的mysql通常会在grant赋权user@'%'或者在VPN里面做网段限制,本地是连不上的,代码提交到服务器才能跑
         */

        // 创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);

        // DataStream-api方式(推荐)
        // 获取数据源
        SourceFunction<String> sourceFunction = MySQLSource.<String>builder()
                .hostname("localhost")
                .port(3306)
                .databaseList("realtime")
                .tableList("realtime.*")  // 默认监控所有,可以通过正则指定database和table白名单
                .username("root")
                .password("root@123")
                .startupOptions(StartupOptions.initial())  // initial启动时会扫描历史数据,然后继续读取最新的binlog
//                .deserializer(new StringDebeziumDeserializationSchema()) // 默认反序列化方式返回的数据格式不太友好
                .deserializer(new MyStringDeserializationSchema())
                .build();
        env
                .addSource(sourceFunction)
                .print();

        // Table/Sql-api方式(1.12版本可以,1.13版本不行)
//        // 创建表执行环境
//        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
//        // 转换动态表
//        tableEnv.executeSql(
//                "CREATE TABLE user_info (" +
//                        " id INT NOT NULL," +
//                        " name STRING," +
//                        " age INT" +
//                        ") WITH (" +
//                        " 'connector' = 'mysql-cdc'," +
//                        " 'hostname' = 'localhost'," +
//                        " 'port' = '3306'," +
//                        " 'username' = 'root'," +
//                        " 'password' = 'root@123'," +
//                        " 'database-name' = 'realtime'," +
//                        " 'table-name' = 't_user'" +
//                        ")"
//        );
//
//        // 查询数据
//        tableEnv
//                .executeSql("select * from user_info")
//                .print();

        // 启动任务
        env.execute();
    }

    // 自定义反序列化器,封装返回的数据格式,方便解析
    public static class MyStringDeserializationSchema implements DebeziumDeserializationSchema<String> {
        @Override
        public void deserialize(SourceRecord sourceRecord, Collector<String> collector) {
            Struct valueStruct = (Struct) sourceRecord.value();
            Struct sourceStrut = valueStruct.getStruct("source");
            // 获取数据库
            String database = sourceStrut.getString("db");
            // 获取表
            String table = sourceStrut.getString("table");
            // 获取类型
            String type = Envelope.operationFor(sourceRecord).toString().toLowerCase();
            if ("create".equals(type)) {
                type = "insert";
            }

            // 封装成JSON对象
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("database", database);
            jsonObj.put("table", table);
            jsonObj.put("type", type);

            // 获取影响的数据data
            // 源格式：id=1, name=aaa, age=17
            // 目标格式：{"id": 74603, "order_id": 28641, "order_status": "1005", "operate_time": "2021-07-30 11:35:49"}}
            Struct afterStruct = valueStruct.getStruct("after");
            JSONObject dataJsonObj = new JSONObject();
            if (afterStruct != null){
                for (Field field : afterStruct.schema().fields()) {
                    String fieldName = field.name();
                    Object fieldValue = afterStruct.get(field);
                    dataJsonObj.put(fieldName, fieldValue);
                }
            }
            jsonObj.put("data", dataJsonObj);

            // 向下游传递数据
            collector.collect(jsonObj.toJSONString());
        }

        @Override
        public TypeInformation<String> getProducedType() {
            return TypeInformation.of(String.class);
        }
    }

}
