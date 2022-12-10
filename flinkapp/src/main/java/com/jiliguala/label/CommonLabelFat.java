package com.jiliguala.label;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jiliguala.util.MyFlinkUtil;
import com.jiliguala.util.PropertiesUtil;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Author: tim
 * @Date: 2022/11/14 4:33 下午
 * @Desc:
 */
public class CommonLabelFat {
    /**
     * Caused by: com.mysql.cj.exceptions.CJCommunicationsException:
     * The last packet successfully received from the server was 5,811,951 milliseconds ago.
     * The last packet sent successfully to the server was 5,812,024 milliseconds ago.
     * is longer than the server configured value of 'wait_timeout'.
     * You should consider either expiring and/or testing connection validity before use in your application,
     * increasing the server configured values for client timeouts,
     * or using the Connector/J connection property 'autoReconnect=true' to avoid this problem.
     * 原因：当数据库重启或当前连接空闲时间超过mysql的wait_timeout,数据库会强行断开链接
     * 解决：服务端调大wait_timeout(不建议),客户端使用连接前先校验当前连接是否有效(推荐)
     *
     * Caused by: org.apache.kafka.common.errors.ProducerFencedException: Producer attempted an operation with an old epoch.
     * Either there is a newer producer with the same transactionalId, or the producer's transaction has been expired by the broker.
     * kafka生产者exactly_once：幂等性只能保证单分区单会话内数据不重复,完全不重复还得在幂等性的基础上开启事务
     *
     * Caused by: org.apache.kafka.common.KafkaException: Unexpected error in InitProducerIdResponse;
     * The transaction timeout is larger than the maximum value allowed by the broker (as configured by transaction.max.timeout.ms).
     * https://nightlies.apache.org/flink/flink-docs-release-1.13/zh/docs/connectors/datastream/kafka/#kafka-producer-%E5%92%8C%E5%AE%B9%E9%94%99
     * Kafka broker事务最大超时时间transaction.max.timeout.ms=15分钟,而FlinkKafkaProducer的transaction.timeout.ms=1小时,
     * 因此在使用Semantic.EXACTLY_ONCE模式之前应该调小transaction.timeout.ms的值
     */

    private static Connection conn;

    public static void main(String[] args) throws Exception {
        // topic列表
        List<String> topics = new ArrayList<>();
        topics.add("fat_usr__user__inter_user");
        topics.add("fat_ord__user__t_orders");
        topics.add("fat_crm__leads_assign__salesman_user_binding");

        // 数据库连接信息
        Properties load = PropertiesUtil.load("label.properties");
        String driver = load.getProperty("fat.driver");
        String url = load.getProperty("fat.url");
        String username = load.getProperty("fat.username");
        String password = load.getProperty("fat.password");

        // 创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
//        // 开启检查点
//        env.enableCheckpointing(TimeUnit.MINUTES.toMillis(1), CheckpointingMode.EXACTLY_ONCE);
//        CheckpointConfig config = env.getCheckpointConfig();
//        config.setCheckpointTimeout(TimeUnit.MINUTES.toMillis(3));
//        config.setTolerableCheckpointFailureNumber(3);
//        config.setCheckpointStorage("hdfs://10.201.7.140:4007/flink/cp/label/common");
//        config.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
//        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, TimeUnit.MINUTES.toMillis(1)));
//        // 状态后端
//        env.setStateBackend(new EmbeddedRocksDBStateBackend());
//        System.setProperty("HADOOP_USER_NAME", "deploy");

        // 获取kafka数据
        DataStreamSource<String> dataStream = env.addSource(MyFlinkUtil.getKafkaSourceInter(topics, "tmp"));
//        dataStream.print();

        // 数据处理
        SingleOutputStreamOperator<String> result = dataStream.flatMap(new RichFlatMapFunction<String, String>() {
            @Override
            public void open(Configuration parameters) throws Exception {
                Class.forName(driver);
                conn = DriverManager.getConnection(url, username, password);
            }

            @Override
            public void flatMap(String value, Collector<String> out) throws Exception {
                if (!conn.isValid(3600)) {
                    conn = DriverManager.getConnection(url, username, password);
                }

                JSONObject jsonObject = JSON.parseObject(value);
                String tableName = jsonObject.getString("table_name");
                JSONObject obj = new JSONObject();

                if ("inter_user".equals(tableName)) {
                    // gua_id,user_no,ip_country_code,phone
                    String gua_id = jsonObject.getString("gua_id");
                    String user_no = jsonObject.getString("user_no");
                    String ip_country_code = jsonObject.getString("ip_country_code");
                    String mobile = null;
                    String phone = jsonObject.getString("phone");
                    String area_code = jsonObject.getString("area_code");
                    if (phone != null && area_code != null) {
                        mobile = "(" + area_code + ")" + phone;
                    }
                    String area_name = null;

                    String sql = "select area_name from user.inter_phone_district_code where country_code = ?";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setString(1, ip_country_code);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        area_name = rs.getString(1);
                    }

                    obj.put("gua_id", gua_id);
                    obj.put("user_no", user_no);
                    obj.put("phone", mobile);
                    obj.put("location", area_name);
                    out.collect(obj.toJSONString());

                } else if ("t_orders".equals(tableName)) {
                    // order_no,user_no,pay_at
                    String order_no = jsonObject.getString("order_no");
                    String user_no = jsonObject.getString("user_no");
                    String pay_at = jsonObject.getString("pay_at");
                    obj.put("order_no", order_no);
                    obj.put("user_no", user_no);
                    obj.put("pay_at", pay_at);
                    out.collect(obj.toJSONString());

                } else if ("salesman_user_binding".equals(tableName)) {
                    // uid,salesman_id,term_id
                    String uid = jsonObject.getString("uid");
                    String salesman_id = jsonObject.getString("salesman_id");
                    String term_id = jsonObject.getString("term_id");
                    obj.put("user_no", uid);
                    obj.put("salesman_id", salesman_id);
                    obj.put("term_id", term_id);
                    out.collect(obj.toJSONString());
                }
            }

            @Override
            public void close() throws Exception {
                if (conn != null) {
                    conn.close();
                }
            }
        });
        result.print("res");

        // 写入下游kafka
        result.addSink(MyFlinkUtil.getKafkaSinkInter("fat_student_info"));

        // 启动任务
        env.execute();
    }
}
