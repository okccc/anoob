package com.okccc.warehouse.ods;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.utils.MyFlinkUtil;
import com.okccc.realtime.utils.PropertiesUtil;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Author: okccc
 * Date: 2022/7/8 5:04 下午
 * Desc: flink消费kafka数据写入hdfs
 */
public class FlinkToHdfs {
    public static void main(String[] args) throws Exception {
        /**
         * Could not initialize class org.apache.flink.runtime.entrypoint.parser.CommandLineOptions
         * 服务器缺少解析bin/flink run命令行的依赖,pom.xml添加flink-clients
         *
         * java.lang.NoSuchMethodError: org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator.sinkTo
         * 服务器提交命令行bin/flink run的flink版本过低
         *
         * 从保存点恢复任务报错：Truncation is not available in hadoop version < 2.7 , You are on Hadoop 2.6.0
         * https://nightlies.apache.org/flink/flink-docs-release-1.15/docs/connectors/datastream/filesystem/#important-considerations
         */

        // 1.创建流处理环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        // 禁用算子链,方便定位导致反压的具体算子
        env.disableOperatorChaining();
        // 设置状态后端
        env.setStateBackend(new EmbeddedRocksDBStateBackend());
        // 检查点时间间隔：通常1~5分钟,查看Checkpoints - Summary - End to End Duration,综合考虑性能和时效性
        env.enableCheckpointing(TimeUnit.MINUTES.toMillis(2), CheckpointingMode.EXACTLY_ONCE);
        CheckpointConfig config = env.getCheckpointConfig();
        // 检查点存储路径
        config.setCheckpointStorage("hdfs:///flink/cp/xxx");
        // 检查点超时时间
        config.setCheckpointTimeout(TimeUnit.MINUTES.toMillis(5));
        // 检查点可容忍的连续失败次数
        config.setTolerableCheckpointFailureNumber(3);
        // 检查点最小等待间隔,通常是时间间隔一半
        config.setMinPauseBetweenCheckpoints(TimeUnit.MINUTES.toMillis(1));
        // 检查点保留策略：job取消时默认会自动删除检查点,可以保留防止任务故障重启失败,还能从检查点恢复任务,后面手动删除即可
        config.setExternalizedCheckpointCleanup(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        // 重启策略：重试间隔调大一点,不然flink监控页面一下子就刷新过去变成job failed,看不到具体异常信息
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(3, TimeUnit.MINUTES.toMillis(1)));
        // 本地调试时要指定能访问hadoop的用户
        System.setProperty("HADOOP_USER_NAME", "deploy");

        // 获取表和字段
        String tableName = "xxx";
        Properties load = PropertiesUtil.load("config.properties");
        String columns = load.getProperty(tableName + ".columns");
        // 输出路径
        String output = "hdfs:///user/hive/warehouse/ods.db/ods_xxx_realtime";

        // 2.获取kafka数据
        ArrayList<String> topics = new ArrayList<>();
        topics.add("eduplatform01");
        topics.add("eduplatform02");
        String groupId = tableName + "_g";
        DataStreamSource<String> dataStream = env.addSource(MyFlinkUtil.getKafkaSource(topics, groupId));
//        dataStream.print(">>>");

        // 3.数据处理
        SingleOutputStreamOperator<String> result = dataStream.flatMap(new FlatMapFunction<String, String>() {
            @Override
            public void flatMap(String s, Collector<String> collector) throws Exception {
                JSONObject jsonObject = JSON.parseObject(s);
                String table = jsonObject.getString("table");
                JSONObject data = jsonObject.getJSONArray("data").getJSONObject(0);
                if (table.startsWith(tableName)) {
                    StringBuilder sb = new StringBuilder();
                    String[] arr = columns.split(",");
                    for (int i = 0; i < arr.length; i++) {
                        String value = data.getString(arr[i]);
                        if (i == arr.length - 1) {
                            sb.append(value);
                        } else {
                            // 字段分隔符要和hive建表语句保持一致,默认是\001
                            sb.append(value).append("\001");
                        }
                    }
                    collector.collect(sb.toString());
                }
            }
        });
        result.print("res");

        // 4.写入hdfs
        result.sinkTo(MyFlinkUtil.getHdfsSink(output));

        // 5.启动任务
        env.execute();
    }
}
