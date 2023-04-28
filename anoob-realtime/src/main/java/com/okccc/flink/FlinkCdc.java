package com.okccc.flink;

import com.alibaba.fastjson.JSONObject;
import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.ververica.cdc.debezium.DebeziumDeserializationSchema;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import io.debezium.data.Envelope;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * @Author: okccc
 * @Date: 2023/1/29 14:21
 * @Desc: flink-cdc动态获取mysql数据
 *
 * CDC(ChangeDataCapture): 监控并捕获数据库的insert/update/delete记录,按顺序写入消息队列供下游服务订阅和消费
 * Flink-CDC: 通过flink-cdc-connectors组件直接从mysql和pg等数据库读取全量或增量的变更数据,连kafka中间件都省了
 * github地址：https://github.com/ververica/flink-cdc-connectors
 *
 * 常见错误
 * Access denied; you need (at least one of) the SUPER, REPLICATION CLIENT privilege(s) for this operation
 * 需要dba赋权：grant REPLICATION CLIENT on ${db}.${table} to '${username}'@'%'
 *
 * Caused by: com.mysql.cj.exceptions.CJCommunicationsException: Communications link failure
 * 公司生产环境的mysql通常会在grant赋权user@'%'或者在VPN里面做网段限制,本地是连不上的,代码提交到服务器才能跑
 *
 * Caused by: org.apache.flink.table.api.ValidationException:
 * The MySQL server has a timezone offset (0 seconds ahead of UTC) which does not match the configured timezone Asia/Shanghai.
 * Specify the right server-time-zone to avoid inconsistencies for time-related fields.
 * 如果是海外的数据库要设置serverTimeZone="UTC"
 */
public class FlinkCdc {

    public static void main(String[] args) throws Exception {
        // 创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 获取数据源
        MySqlSource<String> mySqlSource = MySqlSource.<String>builder()
                .hostname("localhost")
                .port(3306)
                .databaseList("mock")  // lesson.*表示所有lesson开头的库
                .tableList()  // 默认监控所有表,lesson.*\\.node_record.*表示lesson库下所有node_record开头的表
                .username("root")
                .password("root@123")
                // 类似Kafka消费者,FlinkCDC也支持从开头、末尾、指定偏移量、指定时间戳进行消费
                .startupOptions(StartupOptions.initial())  // initial启动时会扫描历史数据,然后继续读取最新的binlog
                .serverTimeZone("Asia/Shanghai")
//                .deserializer(new StringDebeziumDeserializationSchema())  // SourceRecord格式不太友好
                .deserializer(new JsonDebeziumDeserializationSchema())  // binlog是二进制数据要反序列化,返回JSON方便解析(推荐)
//                .deserializer(new MyDebeziumDeserializationSchema())  // 也可以自定义反序列化器
                .build();

        env
                .fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "MySql Source")
                .print();

        // 启动任务
        env.execute();
    }

    // 自定义反序列化器,封装返回的数据格式,方便解析
    public static class MyDebeziumDeserializationSchema implements DebeziumDeserializationSchema<String> {

        @Override
        public void deserialize(SourceRecord record, Collector<String> out) throws Exception {
            /**
             * SourceRecord{
             *     sourcePartition={
             *         server=mysql_binlog_source
             *     },
             *     sourceOffset={
             *         transaction_id=null,
             *         ts_sec=1674979676,
             *         file=,
             *         pos=0
             *     }
             * }ConnectRecord{
             *     topic='mysql_binlog_source.ssm.t_user',
             *     kafkaPartition=null,
             *     key=Struct{
             *         id=36
             *     },
             *     keySchema=Schema{
             *         mysql_binlog_source.ssm.t_user.Key: STRUCT
             *     },
             *     value=Struct{
             *         after=Struct{
             *             id=36,
             *             username=moon,
             *             password=ne,
             *             age=21,
             *             gender=?,
             *             email=ne@qq.com
             *         },
             *         source=Struct{
             *             version=1.6.4.Final,
             *             connector=mysql,
             *             name=mysql_binlog_source,
             *             ts_ms=0,
             *             db=ssm,
             *             table=t_user,
             *             server_id=0,
             *             file=,
             *             pos=0,
             *             row=0
             *         },
             *         op=r,
             *         ts_ms=1674979676163
             *     },
             *     valueSchema=Schema{
             *         mysql_binlog_source.ssm.t_user.Envelope: STRUCT
             *     },
             *     timestamp=null,
             *     headers=ConnectHeaders(headers=)
             * }
             */

            // 获取SourceRecord的value部分
            Struct value = (Struct) record.value();

            // 获取数据：id=36, username=moon, age=21 -> {"id": 36, "username": "moon", "age": 21}
            Struct after = value.getStruct("after");
            JSONObject data = new JSONObject();
            if (after != null) {
                for (Field field : after.schema().fields()) {
                    String fieldName = field.name();
                    Object fieldValue = after.get(field);
                    data.put(fieldName, fieldValue);
                }
            }

            // 获取库和表
            Struct source = value.getStruct("source");
            String db = source.getString("db");
            String table = source.getString("table");

            // 获取操作类型
            String type = Envelope.operationFor(record).toString();
            if ("READ".equalsIgnoreCase(type) || "CREATE".equals(type)) {
                type = "INSERT";
            }

            // 获取时间戳
            Long ts_ms = value.getInt64("ts_ms");

            // 封装成JSON对象
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("data", data);
            jsonObject.put("database", db);
            jsonObject.put("table", table);
            jsonObject.put("type", type);
            jsonObject.put("ts", ts_ms);

            // 收集结果往下游发送
            out.collect(jsonObject.toJSONString());
        }

        @Override
        public TypeInformation<String> getProducedType() {
            return TypeInformation.of(String.class);
        }
    }
}
