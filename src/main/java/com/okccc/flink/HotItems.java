package com.okccc.flink;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2021/9/15 下午5:22
 * Desc: 实时统计1小时内的热门商品排名,5分钟刷新一次
 * 分析：先按照商品分组统计pv,窗口大小1hour,滑动间隔5min,再按照窗口分组进行排序
 * 流程：分流 - 开窗 - 聚合 - 分流 - 聚合
 */
public class HotItems {
    public static void main(String[] args) throws Exception {
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
                        // 无序数据要设置延迟时间
//                        WatermarkStrategy.<UserBehavior>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner(new SerializableTimestampAssigner<UserBehavior>() {
                            @Override
                            public long extractTimestamp(UserBehavior element, long recordTimestamp) {
                                return element.timestamp;
                            }
                        })
                )
                // 按照商品分组
                .keyBy(r -> r.itemId)
                // 有刷新频率就是滑动窗口,窗口大小1小时,滑动间隔5分钟,一个EventTime可以属于窗口大小(1hour)/滑动间隔(5min)=12个窗口
                .window(SlidingEventTimeWindows.of(Time.hours(1), Time.minutes(5)))
                // 统计每个商品在每个窗口的访问量
                .aggregate(new CountAgg(), new WindowResult())
                // ItemViewCount{itemId='3031354', windowStart=2017-11-26 17:00:00.0, windowEnd=2017-11-26 18:00:00.0, cnt=10}
                // 按照窗口分组
                .keyBy(r -> r.windowStart)
                // 对窗口内所有商品的访问量排序取前3
                .process(new TopN(3))
                .print("topN");

        // 启动任务
        env.execute();
    }

    // 自定义增量聚合函数
    public static class CountAgg implements AggregateFunction<UserBehavior, Integer, Integer> {
        @Override
        public Integer createAccumulator() {
            return 0;
        }
        @Override
        public Integer add(UserBehavior value, Integer accumulator) {
            return accumulator + 1;
        }
        @Override
        public Integer getResult(Integer accumulator) {
            return accumulator;
        }
        @Override
        public Integer merge(Integer a, Integer b) {
            return null;
        }
    }

    // 自定义全窗口函数
    public static class WindowResult extends ProcessWindowFunction<Integer, ItemViewCount, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<Integer> elements, Collector<ItemViewCount> out) throws Exception {
            out.collect(new ItemViewCount(key, context.window().getStart(), context.window().getEnd(), elements.iterator().next()));
        }
    }

    // 自定义处理函数求topN
    public static class TopN extends KeyedProcessFunction<Long, ItemViewCount, String> {
        private final Integer n;

        public TopN(Integer n) {
            this.n = n;
        }

        // 因为涉及排序操作,所以要收集流中所有元素
        private ListState<ItemViewCount> listState;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            // 实例化状态变量
            listState = getRuntimeContext().getListState(
                    new ListStateDescriptor<>("itemViewCount-list", Types.POJO(ItemViewCount.class)));
        }

        @Override
        public void processElement(ItemViewCount value, Context ctx, Collector<String> out) throws Exception {
            // 每来一条数据就添加到状态变量
            listState.add(value);
            // 注册windowEnd + 1(ms)触发的定时器,每个key在每个时间戳只能注册一个定时器,后面的时间戳进来发现该窗口已经有定时器了就不会再注册
            ctx.timerService().registerEventTimeTimer(value.windowEnd + 1);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            super.onTimer(timestamp, ctx, out);
            // 触发定时器时进行排序,ListState本身不支持排序,需要借助ArrayList
            ArrayList<ItemViewCount> itemViewCounts = new ArrayList<>();
            for (ItemViewCount itemViewCount : listState.get()) {
                itemViewCounts.add(itemViewCount);
            }
            // 此时已经不需要listState了,清空状态,节约内存,手动进行GC
            listState.clear();
            // 使用Comparator接口进行比较器排序
            itemViewCounts.sort(new Comparator<ItemViewCount>() {
                @Override
                public int compare(ItemViewCount o1, ItemViewCount o2) {
                    return o2.cnt - o1.cnt;  // 降序
                }
            });
            // 窗口信息
            long windowEnd = timestamp - 1;
            long windowStart = windowEnd - 3600 * 1000;
//            // 取排序后的前3
//            for (int i = 0; i < n; i++) {
//                ItemViewCount ivc = itemViewCounts.get(i);
//                out.collect("窗口区间：" + new Timestamp(windowStart) + " ~ " + new Timestamp(windowEnd) +
//                        "NO " + (i + 1) + " 的商品 " + ivc.itemId + " 访问次数 " + ivc.cnt);
//            }

            // 将结果拼接成字符串输出展示
            StringBuilder sb = new StringBuilder();
            sb.append("========================================\n");
            sb.append("窗口区间：" + new Timestamp(windowStart) + " ~ " + new Timestamp(windowEnd) + "\n");
            // 取排序后的前3
            for (int i = 0; i < n; i++) {
                ItemViewCount ivc = itemViewCounts.get(i);
                sb.append("NO " + (i+1) + " 的商品 " + ivc.itemId + " 访问次数 " + ivc.cnt + "\n");
            }
            // 收集结果往下游发送
            out.collect(sb.toString());
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
    public static class ItemViewCount {
        public String itemId;
        public Long windowStart;
        public Long windowEnd;
        public Integer cnt;

        public ItemViewCount() {
        }

        public ItemViewCount(String itemId, Long windowStart, Long windowEnd, Integer cnt) {
            this.itemId = itemId;
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.cnt = cnt;
        }

        @Override
        public String toString() {
            return "ItemViewCount{" +
                    "itemId='" + itemId + '\'' +
                    ", windowStart=" + new Timestamp(windowStart) +
                    ", windowEnd=" + new Timestamp(windowEnd) +
                    ", cnt=" + cnt +
                    '}';
        }
    }

}
