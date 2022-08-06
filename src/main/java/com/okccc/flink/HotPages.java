package com.okccc.flink;

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

/**
 * Author: okccc
 * Date: 2021/10/9 下午5:59
 * Desc: 实时统计10min内的热门页面排名,5s刷新一次(乱序数据)
 */
public class HotPages {
    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);
        // 设置侧输出流
        OutputTag<LogEvent> outputTag = new OutputTag<LogEvent>("output") {};

        // 获取数据源
        SingleOutputStreamOperator<String> result = env
                .readTextFile("input/apache.log")
                // 将流数据封装成POJO类
                .map(new MapFunction<String, LogEvent>() {
                    @Override
                    public LogEvent map(String value) throws Exception {
                        // 83.149.9.216 - - 17/05/2015:10:05:03 +0000 GET /presentations/kibana-search.png
                        String[] arr = value.split(" ");
                        // 将日志中的时间字符串转换成Long类型的时间戳
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy:HH:mm:ss");
                        Long ts = sdf.parse(arr[3]).getTime();
                        return new LogEvent(arr[0], arr[1], ts, arr[5], arr[6]);
                    }
                })
                .filter(r -> r.method.equals("GET"))
                // 提取时间戳生成水位线
                .assignTimestampsAndWatermarks(
                        // 无序数据要设置延迟时间
                        WatermarkStrategy.<LogEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                .withTimestampAssigner(new SerializableTimestampAssigner<LogEvent>() {
                                    @Override
                                    public long extractTimestamp(LogEvent element, long recordTimestamp) {
                                        return element.timestamp;
                                    }
                                })
                )
                // 按照页面分组
                .keyBy(r -> r.url)
                // 开窗,有刷新频率的就是滑动窗口,一个EventTime可以属于窗口大小(10min)/滑动间隔(5s)=120个窗口
                .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.seconds(5)))
                // 允许迟到事件,水位线越过windowEnd + allowLateness时窗口才销毁
                .allowedLateness(Time.seconds(60))
                // 设置侧输出流
                .sideOutputLateData(outputTag)
                // 统计每个页面在每个窗口的访问量
                .aggregate(new CountAgg(), new WindowResult())
                // 再按照窗口分组
                .keyBy(r -> r.windowEnd)
                // 对窗口内所有页面的访问量排序取前3
                .process(new TopN(3));

        result.print();
        result.getSideOutput(outputTag).print("output");

        // 启动任务
        env.execute();
    }

    // 自定义预聚合函数
    public static class CountAgg implements AggregateFunction<LogEvent, Integer, Integer> {
        @Override
        public Integer createAccumulator() {
            return 0;
        }
        @Override
        public Integer add(LogEvent value, Integer accumulator) {
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
    public static class WindowResult extends ProcessWindowFunction<Integer, PageViewCount, String, TimeWindow> {
        @Override
        public void process(String key, Context context, Iterable<Integer> elements, Collector<PageViewCount> out) throws Exception {
            out.collect(new PageViewCount(key, context.window().getEnd(), elements.iterator().next()));
        }
    }

    // 自定义处理函数
    public static class TopN extends KeyedProcessFunction<Long, PageViewCount, String> {
        private final Integer n;

        public TopN(Integer n) {
            this.n = n;
        }

        // 由于迟到数据的存在,窗口的统计结果会不断更新,而ListState状态无法判断进来的url是否存在,这就可能导致多个相同url的PageViewCount
        // 进入process方法的定时器排序,所以迟到数据场景下应该使用MapState管理状态,当迟到数据进来时先判断url是否存在,存在就更新不存在就添加
        MapState<String, Integer> mapState;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            // 实例化状态变量
            mapState = getRuntimeContext().getMapState(
                    new MapStateDescriptor<>("mapState", Types.STRING, Types.INT));
        }

        @Override
        public void processElement(PageViewCount value, Context ctx, Collector<String> out) throws Exception {
            // 添加或更新状态
            mapState.put(value.url, value.count);
            // 注册一个windowEnd + 1(ms)触发的定时器,比如[0,4999]窗口加1ms就是[0,5000],当watermark>=5000时就触发
            ctx.timerService().registerEventTimeTimer(value.windowEnd + 1);
            // 注册一个windowEnd + 1(min)触发的定时器,此时窗口等待1分钟后已经彻底关闭,不会再有迟到数据进来,这时候就可以清空状态了
            ctx.timerService().registerEventTimeTimer(value.windowEnd + 60000);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            super.onTimer(timestamp, ctx, out);
            // 当有多个定时器时可以先根据触发时间判断是哪个定时器,这里触发定时器的key就是PageViewCount对象的windowEnd
            if (timestamp == ctx.getCurrentKey() + 60000) {
                // 后面的定时器触发时,前面的定时器肯定已经触发执行完了,此时可以清空状态结束程序
                mapState.clear();
                return;
            }

            // MapState本身没有排序功能,需要借助ArrayList
            ArrayList<Tuple2<String, Integer>> urlCounts = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : mapState.entries()) {
                urlCounts.add(Tuple2.of(entry.getKey(), entry.getValue()));
            }
            // 使用Comparator接口进行比较器排序
            urlCounts.sort(new Comparator<Tuple2<String, Integer>>() {
                @Override
                public int compare(Tuple2<String, Integer> o1, Tuple2<String, Integer> o2) {
                    return o2.f1 - o1.f1;
                }
            });
            // 窗口信息
            long windowEnd = timestamp - 1;

            // 将排名信息拼接成字符串展示,实际应用场景可以写入数据库
            StringBuilder sb = new StringBuilder();
            sb.append("========================================\n");
            sb.append("窗口区间：" + new Timestamp(windowEnd) + "\n");
            for (int i = 0; i < n; i++) {
                Tuple2<String, Integer> tuple2 = urlCounts.get(i);
                sb.append("NO " + (i+1) + " 的页面 " + tuple2.f0 + " 访问次数 " + tuple2.f1 + "\n");
            }
            // 收集结果往下游发送
            out.collect(sb.toString());
        }
    }

    // POJO类
    public static class LogEvent {
        public String ip;
        public String userId;
        public Long timestamp;
        public String method;
        public String url;

        public LogEvent() {
        }

        public LogEvent(String ip, String userId, Long timestamp, String method, String url) {
            this.ip = ip;
            this.userId = userId;
            this.timestamp = timestamp;
            this.method = method;
            this.url = url;
        }

        @Override
        public String toString() {
            return "LogEvent{" +
                    "ip='" + ip + '\'' +
                    ", userId='" + userId + '\'' +
                    ", timestamp=" + new Timestamp(timestamp) +
                    ", method='" + method + '\'' +
                    ", url='" + url + '\'' +
                    '}';
        }
    }

    public static class PageViewCount {
        public String url;
        public Long windowEnd;
        public Integer count;

        public PageViewCount() {
        }

        public PageViewCount(String url, Long windowEnd, Integer count) {
            this.url = url;
            this.windowEnd = windowEnd;
            this.count = count;
        }

        @Override
        public String toString() {
            return "PageViewCount{" +
                    "url='" + url + '\'' +
                    ", windowEnd=" + new Timestamp(windowEnd) +
                    ", count=" + count +
                    '}';
        }
    }
}
