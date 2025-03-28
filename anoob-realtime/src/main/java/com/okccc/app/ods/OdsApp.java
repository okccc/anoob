package com.okccc.app.ods;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONAware;
import com.alibaba.fastjson.JSONObject;
import com.okccc.util.FlinkUtil;
import com.ververica.cdc.connectors.mysql.source.MySqlSource;
import com.ververica.cdc.connectors.mysql.source.MySqlSourceBuilder;
import com.ververica.cdc.connectors.mysql.table.StartupOptions;
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import org.apache.kafka.connect.json.DecimalFormat;
import org.apache.kafka.connect.json.JsonConverterConfig;

import java.util.HashMap;

/**
 * @Author: okccc
 * @Date: 2023/9/12 16:35:26
 * @Desc: FlinkCDC读取binlog捕获MySql实时数据写入ODS层Kafka
 *
 * FlinkJob配置信息优先级：代码写死 > 命令行提交Job时动态指定(推荐) > flink-conf.yaml
 * 配置项分为两类：
 * a.官方提供了相关配置项,通过-Dkey=value指定
 * b.官方未提供相关配置项,只能通过main方法的args参数传递,然后用ParameterTool解析,配置项的key必须以-/--开头,value紧邻其后
 *
 * flink run-application \
 * -t yarn-application \
 * -p 3 \
 * -Dyarn.application.name=OdsApp \
 * -Dyarn.application.queue=root.flink \
 * -Djobmanager.memory.process.size=1024mb \
 * -Dtaskmanager.memory.process.size=2048mb \
 * -Dtaskmanager.numberOfTaskSlots=3 \
 * -Dclassloader.resolve-order=parent-first \
 * -c com.okccc.realtime.app.ods.OdsApp \
 * /data/projects-app/flinkapp/flinkapp-1.0-SNAPSHOT-jar-with-dependencies.jar \
 * --hdfs-user deploy \
 * --two-phase false
 *
 * MySql数据源
 * 启动模式：事实表只抓更新数据,维度表要刷历史数据
 * serverId：Canal/Maxwell/FlinkCDC监控binlog是基于主从复制实现的,每个并行度都会伪装成MySql集群的一个从节点,要有唯一编号
 * 数据序列化：decimal类型数据默认会被序列化为base-64编码的字符串,'0.00'会输出成'AA==',需要将默认的序列化格式更换为NUMERIC
 *
 * FlinkCDC同步MySql会导致时间字段的时区和格式发生变化,需特殊处理
 * a.date类型：会被转化为距离1970-01-01的天数,要先转换为毫秒时间戳再转换格式
 * b.datetime类型：会被转化为毫秒时间戳,且比原始时间大8小时,要先减去8小时再转换格式
 * c.timestamp类型：会被转化为2023-09-20T10:49:22Z格式,且比原始时间小8小时,要先去除"T"和"Z"再加上8小时再转换格式
 * 日期类型       存储空间    日期格式                 日期范围
 * date         3 bytes    yyyy-MM-dd             1000-01-01 ~ 9999-12-31
 * datetime     8 bytes    yyyy-MM-dd HH:mm:ss    1000-01-01 00:00:00 ~ 9999-12-31 23:59:59
 * timestamp    4 bytes    yyyy-MM-dd HH:mm:ss    1970-01-01 00:00:01 ~ 2037-12-31 23:59:59
 * datetime日期范围更大,timestamp占用字节更少并且在insert和update时会自动更新成当前时间CURRENT_TIMESTAMP
 *
 * 并行度设置
 * Flink并行度通常与Kafka分区数保持一致,可以在提交Job时通过-p参数动态指定
 * {"id":1,"name":"A"} -> {"id":1,"name":"B"} -> {"id":1,"name":"C"} 如果数据乱序下游可能先收到第二次修改,导致最终name=B
 * 从kafka读数据时,同一主键的数据可能进入不同的并行度导致数据乱序,所以Source算子并行度设置为1可以保证数据严格有序
 * 处理kafka数据时,同一主键的数据可能进入不同的并行度导致数据乱序,所以flatMap算子并行度也设置为1
 * 往kafka写数据时,同一主键的数据可能进入不同的分区导致数据乱序,可以先按照主键分组,保证相同主键的数据进入同一个分区
 */
public class OdsApp {

    public static void main(String[] args) throws Exception {
        // 获取命令行参数
        ParameterTool parameterTool = ParameterTool.fromArgs(args);
        // 本地调试时要设置能访问hdfs的用户
        String hdfsUser = parameterTool.get("hdfs-user", "deploy");
        System.setProperty("HADOOP_USER_NAME", hdfsUser);

        // 1.创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // 事实表
        mySqlToKafka(env, "dwd");

        // 维度表
        mySqlToKafka(env, "dim");

        // 6.启动任务
        env.execute();
    }

    public static void mySqlToKafka(StreamExecutionEnvironment env, String tableType) {
        // Serializes the JSON Decimal as a base-64 string. For example, serializing the value `10.2345` with the BASE64 setting will result in `"D3J5"`.
        // Serializes the JSON Decimal as a JSON number. For example, serializing the value `10.2345` with the NUMERIC setting will result in `10.2345`.
        HashMap<String, Object> customConverterConfigs = new HashMap<>();
        customConverterConfigs.put(JsonConverterConfig.DECIMAL_FORMAT_CONFIG, DecimalFormat.NUMERIC.name());

        // 2.MySQL数据源
        MySqlSource<String> mysqlSource = null;
        MySqlSourceBuilder<String> builder = MySqlSource.<String>builder()
                .hostname("localhost")
                .port(3306)
                .username("root")
                .password("root@123")
                .databaseList("mock")
//                .tableList("")
//                .serverId("")
//                .startupOptions(StartupOptions.initial())
                .deserializer(new JsonDebeziumDeserializationSchema(false, customConverterConfigs));

        switch (tableType) {
            // 事实表
            case "dwd":
                String[] dwdTables = {"mock.cart_info", "mock.order_info", "mock.order_detail", "mock.payment_info"};
                mysqlSource = builder
                        .tableList(dwdTables)
                        .serverId("5801")
                        .startupOptions(StartupOptions.initial())
                        .build();
                break;
            // 维度表
            case "dim":
                String[] dimTables = {"mock.user_info", "mock.sku_info", "mock.spu_info", "mock.base_province"};
                mysqlSource = builder
                        .tableList(dimTables)
                        .serverId("5802")
                        .startupOptions(StartupOptions.initial())
                        .build();
                break;
        }

        // 3.从mysql读数据
        SingleOutputStreamOperator<String> dataStream = env
                .fromSource(mysqlSource, WatermarkStrategy.noWatermarks(), "MysqlSource")
                .setParallelism(1)
                .uid(tableType + "_source");

        // 4.简单etl处理
        KeyedStream<JSONObject, String> keyedStream = dataStream
                .flatMap(new FlatMapFunction<String, JSONObject>() {
                    @Override
                    public void flatMap(String value, Collector<JSONObject> out) {
                        try {
                            JSONObject jsonObject = JSON.parseObject(value);
                            // c,d,u分别对应增删改操作,initial()方法执行全表扫描时操作类型为r
                            if (!"d".equals(jsonObject.getString("op"))) {
                                // 时间戳字段通常是ts,按照习惯这里将ts_ms替换成ts
                                jsonObject.put("ts", jsonObject.getString("ts_ms"));
                                jsonObject.remove("ts_ms");
                                out.collect(jsonObject);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setParallelism(1)
                .uid(tableType + "_flatMap")
                .keyBy(r -> r.getJSONObject("after").getString("id"));

        // 5.往kafka写数据
        keyedStream
                .map(JSONAware::toJSONString)
                .sinkTo(FlinkUtil.getKafkaSink("ods_base_db", tableType + "_mysql"))
                .uid(tableType + "_sink");
    }
}
