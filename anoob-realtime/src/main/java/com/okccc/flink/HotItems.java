package com.okccc.flink;

import com.okccc.bean.ItemViewCount;
import com.okccc.bean.UserBehavior;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.guava30.com.google.common.collect.Lists;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * @Author: okccc
 * @Date: 2021/9/15 下午5:22
 * @Desc: 实时统计1小时内的热门商品排名,5分钟刷新一次(有序数据)
 *
 * 有刷新频率就是滑动窗口 [08:00:00 - 09:00:00]、[08:05:00 - 09:05:00]、[08:10:00 - 09:10:00]...
 * 分析：先按照商品分组统计pv,窗口大小1小时滑动间隔5分钟,再按照窗口分组进行排序
 *
 * env.readTextFile()报错Caused by: java.lang.ClassNotFoundException: org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
 * 类找不到大概率是jar包冲突,先去本地maven仓库确认一下包路径org.apache.commons:commons-compress
 * 分析maven依赖 mvn dependency:tree -Dverbose -Dincludes=org.apache.commons:commons-compress
 * com.okccc:flink:jar:1.0-SNAPSHOT
 * +- org.apache.flume:flume-ng-core:jar:1.9.0:compile
 * |  \- org.apache.avro:avro:jar:1.7.4:compile
 * |     \- org.apache.commons:commons-compress:jar:1.4.1:compile
 * \- org.apache.flink:flink-java:jar:1.15.2:compile
 *    \- org.apache.flink:flink-core:jar:1.15.2:compile
 *       \- (org.apache.commons:commons-compress:jar:1.21:compile - omitted for conflict with 1.4.1)
 * 发现是flink-core依赖的commons-compress:1.21和flume-ng依赖的commons-compress:1.4.1冲突了
 * 可以在配置flume-ng依赖时添加exclusion将其排除,或者更换flink api使用env.fromSource(FileSource.forRecordStreamFormat(...))
 */
public class HotItems {

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);

        env
                .readTextFile("anoob-realtime/input/UserBehavior.csv", "UTF-8")
                // 将输入数据封装成POJO类
                .map((MapFunction<String, UserBehavior>) value -> {
                    // 561558,3611281,965809,pv,1511658000
                    String[] arr = value.split(",");
                    return new UserBehavior(arr[0], arr[1], arr[2], arr[3], Long.parseLong(arr[4]) * 1000);
                })
                // 分配时间戳和生成水位线
                .assignTimestampsAndWatermarks(
                        // 观察数据发现是顺序的,不用设置延迟时间
                        WatermarkStrategy.<UserBehavior>forMonotonousTimestamps()
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                )
                // 按照商品分组
                .keyBy(r -> r.itemId)
                // 有刷新频率就是滑动窗口,窗口大小1小时,滑动间隔5分钟,一个EventTime可以属于窗口大小(1hour)/滑动间隔(5min)=12个窗口
                .window(SlidingEventTimeWindows.of(Time.hours(1), Time.minutes(5)))
                // 先统计每个商品在每个窗口的访问量
                .aggregate(
                        new AggregateFunction<UserBehavior, Integer, Integer>() {
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
                                return a + b;
                            }
                        },
                        new ProcessWindowFunction<Integer, ItemViewCount, String, TimeWindow>() {
                            @Override
                            public void process(String key, ProcessWindowFunction<Integer, ItemViewCount, String, TimeWindow>.Context context, Iterable<Integer> elements, Collector<ItemViewCount> out) throws Exception {
                                // 当水位线越过windowEnd时开始计算然后关闭窗口,后面时间戳小于水位线的数据就进不来了
                                out.collect(new ItemViewCount(key, context.window().getStart(), context.window().getEnd(), elements.iterator().next()));
                            }
                        }
                )
                // ItemViewCount{itemId='3031354', windowStart=2017-11-26 17:00:00.0, windowEnd=2017-11-26 18:00:00.0, cnt=10}
                // 再按照窗口分组
                .keyBy(r -> r.windowEnd)
                // 对窗口内所有商品的访问次数排序取前3
                .process(new TopNItems(3))
                .print("topN");

        // 启动任务
        env.execute();
    }

    // 自定义处理函数求topN
    public static class TopNItems extends KeyedProcessFunction<Long, ItemViewCount, String> {

        public final Integer num;

        public TopNItems(Integer num) {
            this.num = num;
        }

        // 排序要收集流中所有元素,声明一个ListState作为累加器
        private ListState<ItemViewCount> acc;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            // 实例化状态变量
            acc = getRuntimeContext().getListState(
                    // POJO类必须是公共且独立的 org.apache.flink.api.common.typeinfo.Types源码260行
                    new ListStateDescriptor<>("acc", Types.POJO(ItemViewCount.class)));
        }

        @Override
        public void processElement(ItemViewCount value, KeyedProcessFunction<Long, ItemViewCount, String>.Context ctx, Collector<String> out) throws Exception {
            // 每来一条数据就添加到累加器
            acc.add(value);

            // 注册windowEnd + 1(ms)触发排序的定时器
            ctx.timerService().registerEventTimeTimer(value.windowEnd + 1L);
        }

        @Override
        public void onTimer(long timestamp, KeyedProcessFunction<Long, ItemViewCount, String>.OnTimerContext ctx, Collector<String> out) throws Exception {
            super.onTimer(timestamp, ctx, out);
            // 获取窗口信息
            long windowEnd = timestamp - 1L;
            long windowStart = windowEnd - 3600 * 1000L;

            // ListState本身不支持排序,需要借助ArrayList
            ArrayList<ItemViewCount> itemViewCounts = Lists.newArrayList(acc.get());
            itemViewCounts.sort(new Comparator<ItemViewCount>() {
                @Override
                public int compare(ItemViewCount o1, ItemViewCount o2) {
                    return o2.cnt - o1.cnt;  // 降序
                }
            });

            // 因为没有迟到事件所以当前窗口的累加器不会再更新,用完可以立即清空节省内存
            acc.clear();

            // 将排名结果拼接成字符串展示,实际应用场景可以写入数据库
            StringBuilder sb = new StringBuilder();
            sb.append("==============================================\n");
            sb.append("窗口区间 [" + new Timestamp(windowStart) + ", " + new Timestamp(windowEnd) + ")\n");
            // 排序后取前3,注意索引是从0开始
            for (int i = 0; i < Math.min(num, itemViewCounts.size()); i++) {
                ItemViewCount itemViewCount = itemViewCounts.get(i);
                sb.append("NO" + (i+1) + " 商品 " + itemViewCount.itemId + " 访问次数 " + itemViewCount.cnt + "\n");
            }

            // 收集结果往下游发送
            out.collect(sb.toString());
        }
    }
}
