package com.okccc.app.dim;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.okccc.app.bean.TableProcess;
import com.okccc.func.DimSinkFunction;
import com.okccc.func.TableProcessFunction;
import com.okccc.util.FlinkUtil;
import com.okccc.util.PhoenixUtil;
import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.BroadcastConnectedStream;
import org.apache.flink.streaming.api.datastream.BroadcastStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

/**
 * @Author: okccc
 * @Date: 2023/3/7 15:49:42
 * @Desc: Maxwell抓取的业务数据包含事实表和维度表,需要将Kafka中的维度数据拆分出来写入Hbase
 * Mock - Mysql(binlog) - Maxwell - Kafka(ODS) - DimApp(BroadcastConnectedStream) - Phoenix(HBase)
 *
 * DIM层主要用于维度关联,通过主键获取相关维度信息,此类场景K-V类型数据库效率较高,需满足条件：1.永久存储 2.主键查询
 * Mysql：性能一般,实在要用就用从库,只读不写数据量不大时也能顶一顶
 * Redis：数据常驻内存会给内存造成较大压力,不适合存放大量数据,比如用户表
 * Hbase：海量数据永久存储,根据主键快速查询,可以是行存储也可以是列存储,当列族=1时就是行存储(推荐)
 * Hive效率太低、ClickHouse并发能力差且列存储不适合主键查询、ES给所有字段加索引适合搜索但不适合存储
 *
 * 读数据：从ODS层的业务数据中过滤出维表数据,如果代码将表名写死,那么每次新增维表都要修改代码并重启任务
 * 不修改代码,只重启任务：将维表信息写到配置文件,只在程序启动时加载一次
 * 不修改代码,不重启任务：将维表信息写到MySQL,通过FlinkCDC实时监控binlog动态感知维表变化,创建配置流并将其作为广播流和主流进行连接
 *
 * 写数据：通过JdbcSink或者自定义Sink将维表数据写到Phoenix
 */
public class DimApp {

    public static void main(String[] args) throws Exception {
        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 通常和Kafka分区数保持一致,涉及多流合并的任务全局并行度设置为1可以避免很多麻烦
        env.setParallelism(3);

        // 2.读取ODS层Kafka数据(Canal/Maxwell/FlinkCDC)
        KafkaSource<String> kafkaSource = FlinkUtil.getKafkaSource("ods_base_db", "dim_app_g");
        SingleOutputStreamOperator<String> dataStream = env
                .fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "KafkaSource")
                .uid("kafka_source");

        // 3.将数据格式转换成JSON
        SingleOutputStreamOperator<JSONObject> jsonStream = dataStream
                .flatMap(new FlatMapFunction<String, JSONObject>() {
                    @Override
                    public void flatMap(String value, Collector<JSONObject> out) {
                        try {
                            JSONObject jsonObject = JSON.parseObject(value);
                            String type = jsonObject.getString("type");
                            // 保留新增、更新以及初始化数据,如果ODS层数据是FlinkCDC抓取的,可以将before、source等冗余字段删掉
                            if ("insert".equals(type) || "update".equals(type) || "bootstrap-insert".equals(type)) {
                                out.collect(jsonObject);
                            }
                        } catch (Exception e) {
                            // 脏数据可以直接过滤或写侧输出流
                            System.out.println(">>>" + value);
                        }
                    }
                })
                .uid("flatMap");

        // 4.FlinkCDC读取MySQL配置信息表创建配置流
        MySqlSource<String> mysqlSource = MySqlSource.<String>builder()
                .hostname("localhost")
                .port(3306)
                .username("root")
                .password("root@123")
                .databaseList("dim_config")
                .tableList("dim_config.table_process")
                .startupOptions(StartupOptions.initial())
                .deserializer(new JsonDebeziumDeserializationSchema())  // binlog是二进制数据需要反序列化
                .build();
        SingleOutputStreamOperator<String> mysqlStream = env
                .fromSource(mysqlSource, WatermarkStrategy.noWatermarks(), "MysqlSource")
                .setParallelism(1)
                .uid("mysql_source");

        // 5.提前创建好Hbase维度表,不然每个并行度拿到广播状态都要创建表
        SingleOutputStreamOperator<JSONObject> mapStream = mysqlStream
                .map(new MapFunction<String, JSONObject>() {
                    @Override
                    public JSONObject map(String value) {
                        JSONObject jsonObject = JSON.parseObject(value);
                        // 时间戳字段通常是ts,按照习惯这里将ts_ms替换成ts
                        jsonObject.put("ts", jsonObject.getString("ts_ms"));
                        jsonObject.remove("ts_ms");
                        // 判断操作类型
                        String op = jsonObject.getString("op");
                        if ("c".equals(op) || "r".equals(op)) {
                            JSONObject after = jsonObject.getJSONObject("after");
                            String sinkTable = after.getString("sink_table");
                            String sinkColumns = after.getString("sink_columns");
                            String sinkPk = after.getString("sink_pk");
                            String sinkExtend = after.getString("sink_extend");
                            PhoenixUtil.createTable(sinkTable, sinkColumns, sinkPk, sinkExtend);
                        }
                        return jsonObject;
                    }
                })
                .uid("map");

        // 6.主流可能有多个并行度,所以要将配置流广播出去
        // 定义广播状态,key要满足 a.主流和广播流都有 b.必须唯一不然会被覆盖,所以key=mysql表名,value=TableProcess对象
        MapStateDescriptor<String, TableProcess> mapStateDescriptor = new MapStateDescriptor<>("broadcast", Types.STRING, Types.POJO(TableProcess.class));
        BroadcastStream<JSONObject> broadcastStream = mapStream.broadcast(mapStateDescriptor);

        // 7.连接主流与广播流
        BroadcastConnectedStream<JSONObject, JSONObject> broadcastConnectedStream = jsonStream.connect(broadcastStream);

        // 8.处理连接流(难点),根据配置信息从主流中匹配出维度数据
        SingleOutputStreamOperator<JSONObject> outputStream = broadcastConnectedStream.process(new TableProcessFunction(mapStateDescriptor));
        outputStream.print("dim");

        // 9.将维度数据写入phoenix,官方提供的JdbcSink只能单表写入,多表写入需要自定义Sink
        outputStream.addSink(new DimSinkFunction());

        // 10.启动任务
        env.execute("DimApp");
    }
}
