package com.okccc.flink;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.shaded.guava18.com.google.common.base.Charsets;
import org.apache.flink.shaded.guava18.com.google.common.hash.BloomFilter;
import org.apache.flink.shaded.guava18.com.google.common.hash.Funnels;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/9/27 下午5:24
 * Desc: 实时统计1小时内的独立访客数(UV=UniqueVisitor)
 */
public class UniqueVisitor {
    public static void main(String[] args) throws Exception {
        /*
         * 缓存穿透
         * 生产环境中会把一些数据放到redis做缓存,查询请求过来的时候会先查缓存,有就直接返回没有就再去查数据库并把查询结果放入缓存
         * 但是如果有大量请求故意查询一些不存在的userId,既然不存在那么肯定没有缓存,去数据库也查询不到结果也就不会往redis写数据
         * 所以这些请求都会怼到数据库,redis本来是放在前面挡一挡请求减轻数据库压力的,现在redis形同虚设相当于不存在,这就是缓存穿透
         *
         * 缓存雪崩
         * 缓存同一时间大面积失效,所有请求怼到数据库导致崩溃
         * 1.将缓存key的失效时间均匀错开 2.保证redis集群的高可用,如果已经崩了可以利用redis持久化机制将数据恢复到缓存
         *
         * 海量数据去重
         * 一亿userId存储空间大小：10^8 * 10byte ≈ 1g 使用set集合存储对服务器内存压力很大,redis也消耗不起这么多数据
         * 布隆过滤器：10^8 * 1bit ≈ 10m, 1byte = 8bit 考虑hash碰撞可以给大一点空间比如20m,随便放内存还是redis都很轻松
         *
         * 布隆过滤器
         * 位图(bit数组)和hash函数(MD5,SHA-1)组成的特殊数据结构,可以判断某个数据一定不存在(0)或可能存在(1),比List/Set/Map占用空间更少
         * hash碰撞：不同数据经过hash函数计算得到的hash值相同,会导致结果误判,可以增大位图容量和增加hash函数个数来降低碰撞概率
         * google提供的guava布隆过滤器是单机的,分布式项目可以使用redis的bitmap数据结构(本质上还是字符串)实现布隆过滤器
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);

        // 获取数据源
        Properties prop = new Properties();
        prop.setProperty("bootstrap.servers", "localhost:9092");
        prop.setProperty("group.id", "g01");
        prop.setProperty("key.deserializer", StringDeserializer.class.toString());
        prop.setProperty("value.deserializer", StringDeserializer.class.toString());
        env
                .addSource(new FlinkKafkaConsumer<>("nginx", new SimpleStringSchema(), prop))
                // 将流数据封装成POJO类
                .map(new MapFunction<String, UserBehavior>() {
                    @Override
                    public UserBehavior map(String value) throws Exception {
                        // 561558,3611281,965809,pv,1511658000
                        String[] arr = value.split(",");
                        return new UserBehavior(arr[0], arr[1], arr[2], arr[3], Long.parseLong(arr[4]) * 1000);
                    }
                })
                .filter(r -> r.behavior.equals("pv"))
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        // 有序数据不用设置延迟时间
                        WatermarkStrategy.<UserBehavior>forMonotonousTimestamps()
                        .withTimestampAssigner(new SerializableTimestampAssigner<UserBehavior>() {
                            @Override
                            public long extractTimestamp(UserBehavior element, long recordTimestamp) {
                                return element.timestamp;
                            }
                        })
                )
                // 将数据都放到一条流
                .keyBy(r -> 1)
                // 1小时的滚动窗口
                .window(TumblingEventTimeWindows.of(Time.hours(1)))
                // 先增量聚合再全窗口处理
                .aggregate(new CountAggWithBloomFilter(), new WindowResult())
                .print();

        // 启动任务
        env.execute();
    }

    // 自定义增量聚合函数
    // 计算UV要去重所以使用HashSet存放userId,都放在HashSet会消耗大量内存,此处可以用BloomFilter优化
    public static class CountAgg implements AggregateFunction<UserBehavior, HashSet<String>, Integer> {
        @Override
        public HashSet<String> createAccumulator() {
            // 创建累加器
            return new HashSet<>();
        }
        @Override
        public HashSet<String> add(UserBehavior value, HashSet<String> accumulator) {
            // 每来一条数据就添加到累加器,然后返回更新后的累加器
            accumulator.add(value.userId);
            return accumulator;
        }
        @Override
        public Integer getResult(HashSet<String> accumulator) {
            // 窗口关闭时返回累加器大小,即UV数
            return accumulator.size();
        }
        @Override
        public HashSet<String> merge(HashSet<String> a, HashSet<String> b) {
            return null;  // 合并累加器,一般用不到
        }
    }

    // 由于BloomFilter没有.size这种api,可以借助Tuple2<f0, f1>的第一个参数求BloomFilter的大小
    public static class CountAggWithBloomFilter implements AggregateFunction<UserBehavior, Tuple2<Integer, BloomFilter<String>>, Integer> {
        @Override
        public Tuple2<Integer, BloomFilter<String>> createAccumulator() {
            // 创建布隆过滤器,设置要去重的数据量和误差率
            return Tuple2.of(0, BloomFilter.create(Funnels.stringFunnel(Charsets.UTF_8), 100000, 0.01));
        }
        @Override
        public Tuple2<Integer, BloomFilter<String>> add(UserBehavior value, Tuple2<Integer, BloomFilter<String>> accumulator) {
            // 如果布隆过滤器一定不包含当前userId就将其添加进来
            if (!accumulator.f1.mightContain(value.userId)) {
                // put操作就是将字符串传入hash函数,将位图置为1
                accumulator.f1.put(value.userId);
                accumulator.f0 += 1;
            }
            return accumulator;
        }
        @Override
        public Integer getResult(Tuple2<Integer, BloomFilter<String>> accumulator) {
            // 窗口关闭时返回布隆过滤器大小,即UV数
            return accumulator.f0;
        }
        @Override
        public Tuple2<Integer, BloomFilter<String>> merge(Tuple2<Integer, BloomFilter<String>> a, Tuple2<Integer, BloomFilter<String>> b) {
            return null;
        }
    }

    // 自定义窗口处理函数
    public static class WindowResult extends ProcessWindowFunction<Integer, UniqueVisitorCount, Integer, TimeWindow> {
        @Override
        public void process(Integer integer, Context context, Iterable<Integer> elements, Collector<UniqueVisitorCount> out) {
            out.collect(new UniqueVisitorCount(context.window().getStart(), context.window().getEnd(), elements.iterator().next()));
        }
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

    // 输出结果POJO类
    public static class UniqueVisitorCount {
        public Long windowStart;
        public Long windowEnd;
        public Integer cnt;

        public UniqueVisitorCount() {
        }

        public UniqueVisitorCount(Long windowStart, Long windowEnd, Integer cnt) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.cnt = cnt;
        }

        @Override
        public String toString() {
            return "UniqueVisitorCount{" +
                    "windowStart=" + new Timestamp(windowStart) +
                    ", windowEnd=" + new Timestamp(windowEnd) +
                    ", cnt=" + cnt +
                    '}';
        }
    }
}
