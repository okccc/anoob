package com.okccc.flink;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;

import java.sql.Timestamp;
import java.time.Duration;

import static org.apache.flink.table.api.Expressions.$;

/**
 * Author: okccc
 * Date: 2021/9/20 下午12:47
 * Desc: flink sql实现实时热门商品统计
 */
public class FlinkSql {
    public static void main(String[] args) throws Exception {
        /*
         * flink sql像mysql和hive一样也对标准sql语法做了些扩展,可以实现简单需求,复杂的还得用DataStream提供的api
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);
        // 创建表环境
        EnvironmentSettings settings = EnvironmentSettings.newInstance().inStreamingMode().build();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, settings);
        // flink-sql调优
        Configuration conf = tableEnv.getConfig().getConfiguration();
        // 1.设置空闲状态保留时间：join操作的左右表数据、distinct操作的重复数据都会一直存在状态里,需要定时清除
        tableEnv.getConfig().setIdleStateRetention(Duration.ofHours(1));
        conf.setString("table.exec.state.ttl", "1 h");
        // 2.开启微批处理：缓存一定数据再触发处理,减少对state的访问,通过增加延迟提高吞吐量并减少数据输出量,聚合场景下能显著提升性能
        conf.setString("table.exec.mini-batch.enabled", "true");
        // 批量输出的间隔时间
        conf.setString("table.exec.mini-batch.allow-latency", "5 s");
        // 防止OOM设置每个批次最多缓存的数据条数
        conf.setString("table.exec.mini-batch.size", "20000");
        // 3.开启LocalGlobal：两阶段聚合解决数据倾斜问题,针对SUM/COUNT/MAX/MIN/AVG等普通聚合
        conf.setString("table.optimizer.agg-phase-strategy", "TWO_PHASE");

        // 获取数据源
        SingleOutputStreamOperator<UserBehavior> stream = env
                .readTextFile("input/UserBehavior.csv")
                // 将流数据封装成POJO类
                .map(new MapFunction<String, UserBehavior>() {
                    @Override
                    public UserBehavior map(String value) {
                        String[] arr = value.split(",");
                        return new UserBehavior(arr[0], arr[1], arr[2], arr[3], Long.parseLong(arr[4]) * 1000);
                    }
                })
                .filter(r -> r.behavior.equals("pv"))
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        // 有序数据不用设置延迟时间
                        WatermarkStrategy.<UserBehavior>forMonotonousTimestamps()
                                // 无序数据要设置延迟时间
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                );

        // 数据流 -> 动态表
        Table table = tableEnv.fromDataStream(
                stream, $("userId"), $("itemId"), $("categoryId"), $("behavior"),
                $("timestamp").rowtime().as("ts")  // 将时间字段指定为事件时间
        );
        // 动态表 -> 数据流
//        tableEnv.toDataStream(table).print();
        // 创建临时视图
        tableEnv.createTemporaryView("userBehavior", table);

        // 先按商品分组并开窗
        String sql01 = "select itemId,count(*) as cnt,HOP_END(ts, interval '5' minute, interval '1' hour) as windowEnd "
                + "from userBehavior group by itemId,HOP(ts, interval '5' minute, interval '1' hour)";
        // 再按窗口分组并排序
        String sql02 = "select *,row_number() over(partition by windowEnd order by cnt desc) as rn from (" + sql01 + ")";
        // 取topN
        String sql03 = "select * from (" + sql02 + ") where rn <= 3";

        // 执行sql查询
        Table query = tableEnv.sqlQuery(sql03);
        // 将查询结果转换成数据流
        tableEnv.toChangelogStream(query).print();

        // 启动任务
        env.execute();
    }

    // 输入数据POJO类
    public static class UserBehavior {
        public String userId;
        public String itemId;
        public String categoryId;
        public String behavior;
        public Long timestamp;

        public UserBehavior() {
        }

        public UserBehavior(String userId, String itemId, String categoryId, String behavior, Long timestamp) {
            this.userId = userId;
            this.itemId = itemId;
            this.categoryId = categoryId;
            this.behavior = behavior;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return "UserBehavior{" +
                    "userId='" + userId + '\'' +
                    ", itemId='" + itemId + '\'' +
                    ", categoryId='" + categoryId + '\'' +
                    ", behavior='" + behavior + '\'' +
                    ", timestamp=" + new Timestamp(timestamp) +
                    '}';
        }
    }
}
