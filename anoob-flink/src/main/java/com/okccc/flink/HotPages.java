package com.okccc.flink;

import com.okccc.bean.ApacheLog;
import com.okccc.bean.PageViewCount;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.shaded.guava30.com.google.common.collect.Lists;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @Author: okccc
 * @Date: 2021/10/9 下午5:59
 * @Desc: 实时统计10min内的热门页面排名,5s刷新一次(乱序数据)
 *
 * 有刷新频率就是滑动窗口 [08:00:00 - 08:10:00]、[08:00:05 - 08:10:05]、[08:00:10 - 08:10:10]...
 * 分析：先按照商品分组统计pv,窗口大小10分钟滑动间隔5秒钟,乱序数据要设置迟到事件和侧输出流,再按照窗口分组进行排序
 *
 * 83.149.9.216 - - 17/05/2015:10:25:49 +0000 GET /cart
 * 83.149.9.216 - - 17/05/2015:10:25:50 +0000 GET /cart
 * 水位线越过windowEnd触发窗口计算
 * 83.149.9.216 - - 17/05/2015:10:25:51 +0000 GET /cart
 * 水位线继续上升触发 windowEnd + 1 的定时器
 * 83.149.9.216 - - 17/05/2015:10:25:52 +0000 GET /cart
 *
 * 83.149.9.216 - - 17/05/2015:10:25:55 +0000 GET /cart
 * 83.149.9.216 - - 17/05/2015:10:25:56 +0000 GET /cart
 * 触发窗口计算的条件：水位线越过windowEnd且该窗口内有数据
 * 83.149.9.216 - - 17/05/2015:10:25:56 +0000 GET /product
 * 83.149.9.216 - - 17/05/2015:10:25:57 +0000 GET /product
 *
 * 83.149.9.216 - - 17/05/2015:10:26:01 +0000 GET /cart
 * 83.149.9.216 - - 17/05/2015:10:26:02 +0000 GET /product
 *
 * 迟到数据可能属于多个窗口,会触发多个窗口累加器的更新
 * 83.149.9.216 - - 17/05/2015:10:25:46 +0000 GET /cart
 * 迟到数据进来注册的是已经过时的定时器不会立即触发,要等到水位线再次更新时才会触发
 * 83.149.9.216 - - 17/05/2015:10:26:03 +0000 GET /product
 * 83.149.9.216 - - 17/05/2015:10:25:56 +0000 GET /product
 * 83.149.9.216 - - 17/05/2015:10:26:04 +0000 GET /cart
 * 83.149.9.216 - - 17/05/2015:10:25:57 +0000 GET /product
 * 83.149.9.216 - - 17/05/2015:10:26:05 +0000 GET /cart
 *
 * 水位线大幅度上升,此时会触发很多窗口计算和定时器
 * 83.149.9.216 - - 17/05/2015:10:26:50 +0000 GET /cart
 * 此时窗口[10:15:50, 10:25:50)关闭,后面的迟到数据就进不来了
 * 83.149.9.216 - - 17/05/2015:10:26:51 +0000 GET /cart
 * 10:25:46所属的第一个窗口[10:15:50, 10:25:50)不会更新,后面的11个窗口还会继续更新累加器
 * 83.149.9.216 - - 17/05/2015:10:25:46 +0000 GET /cart
 * 10:15:51所属的最后一个窗口[10:15:50, 10:25:50)也关闭了,那前面的11个窗口也早就关闭了,不会有任何窗口的累加器更新,于是扔到侧输出流
 * 83.149.9.216 - - 17/05/2015:10:15:51 +0000 GET /cart
 */
public class HotPages {

    public static void main(String[] args) throws Exception {
        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 设置并行度
        env.setParallelism(1);
        // 创建侧输出流标签
        OutputTag<ApacheLog> outputTag = new OutputTag<ApacheLog>("dirty") {};

        SingleOutputStreamOperator<ApacheLog> dataStream = env
                // socket方便调试代码,没问题再替换成kafka
                .socketTextStream("localhost", 9999)
//                .readTextFile("anoob-flink/input/ApacheLog.csv", "UTF-8")
                // 将输入数据封装成POJO类
                .map(new MapFunction<String, ApacheLog>() {
                    @Override
                    public ApacheLog map(String value) throws Exception {
                        // 83.149.9.216 - - 17/05/2015:10:05:03 +0000 GET /presentations/kibana-search.png
                        String[] arr = value.split(" ");
                        DateTimeFormatter DATETIME_FORMATTER = DateTimeFormat.forPattern("dd/MM/yyyy:HH:mm:ss");
                        long ts = DATETIME_FORMATTER.parseDateTime(arr[3]).toDate().getTime();
                        return new ApacheLog(arr[0], arr[1], arr[5], arr[6], ts);
                    }
                })
                // 过滤静态资源
                .filter(new FilterFunction<ApacheLog>() {
                    @Override
                    public boolean filter(ApacheLog value) throws Exception {
                        String regex = "^((?!\\.(css|js|png|ico)$).)*$";
                        return Pattern.matches(regex, value.url);
                    }
                })
                // 分配时间戳和生成水位线,因为是在keyBy之前就完成的,所以不管来的是什么url,只要时间戳变大就会推高水位线
                .assignTimestampsAndWatermarks(
                        // 观察数据发现最大乱序程度1min而排名5秒刷新一次,为了保证实时性先设置1s延迟hold住大部分符合正态分布的乱序数据
                        WatermarkStrategy.<ApacheLog>forBoundedOutOfOrderness(Duration.ofSeconds(1))
                                .withTimestampAssigner((element, recordTimestamp) -> element.timestamp)
                );
        dataStream.print("data");  // data> ApacheLog{ip='82.165.139.53', userId='-', method='GET', url='/', timestamp=2015-05-20 21:05:15.0}

        SingleOutputStreamOperator<PageViewCount> windowStream = dataStream
                // 按照页面分组
                .keyBy(r -> r.url)
                // 开窗,有刷新频率的就是滑动窗口,一个EventTime可以属于窗口大小(10min)/滑动间隔(5s)=120个窗口
                .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.seconds(5)))
                // 允许迟到事件,水位线越过windowEnd + allowLateness时窗口才关闭
                .allowedLateness(Time.seconds(60))
                // 设置侧输出流
                .sideOutputLateData(outputTag)
                // 先统计每个页面在每个窗口的访问量
                .aggregate(
                        new AggregateFunction<ApacheLog, Integer, Integer>() {
                            @Override
                            public Integer createAccumulator() {
                                return 0;
                            }
                            @Override
                            public Integer add(ApacheLog value, Integer accumulator) {
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
                        new ProcessWindowFunction<Integer, PageViewCount, String, TimeWindow>() {
                            @Override
                            public void process(String key, ProcessWindowFunction<Integer, PageViewCount, String, TimeWindow>.Context context, Iterable<Integer> elements, Collector<PageViewCount> out) throws Exception {
                                // 当水位线越过windowEnd且窗口内有数据时开始计算,但是不会马上关闭窗口,还会继续等待迟到事件
                                Timestamp windowEnd = new Timestamp(context.window().getEnd());
                                Timestamp watermark = new Timestamp(context.currentWatermark());
                                System.out.println("当前水位线 " + watermark + " 触发窗口计算 " + windowEnd);
                                // 收集结果往下游发送
                                out.collect(new PageViewCount(key, context.window().getStart(), context.window().getEnd(), elements.iterator().next()));
                            }
                        }
                );
        windowStream.print("agg"); // PageViewCount{url='/favicon.ico', windowStart=2015-05-20 01:55:50.0, windowEnd=2015-05-20 02:05:50.0, cnt=12}
        windowStream.getSideOutput(outputTag).print("dirty");

        windowStream
                // 再按照窗口分组
                .keyBy(r -> r.windowEnd)
                // 对窗口内所有页面的访问次数排序取前3
                .process(new TopNPages(3))
                .print("topN");

        // 启动任务
        env.execute();
    }

    // 自定义处理函数求topN
    public static class TopNPages extends KeyedProcessFunction<Long, PageViewCount, String> {

        public final Integer num;

        public TopNPages(Integer num) {
            this.num = num;
        }

        // 排序要收集流中所有元素,需声明一个ListState/MapState作为累加器
//        private ListState<PageViewCount> acc;
        // 由于ListState<PageViewCount>无法判断当前进来的url是否存在,所以当有迟到数据进来时累加器会变成这样,结果显然不对
        // 窗口 2015-05-17 10:26:00.0 的累加器 [PageViewCount{url='/cart', cnt=2}, PageViewCount{url='/cart', cnt=3}]
        // 所以乱序数据应该使用MapState<url, cnt>作为累加器,key存在就更新不存在就添加,这样就不会出现多个相同url进入定时器排序的情况
        private MapState<String, Integer> acc;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            // 实例化状态变量
            acc = getRuntimeContext().getMapState(
                    new MapStateDescriptor<>("acc", Types.STRING, Types.INT));
        }

        @Override
        public void processElement(PageViewCount value, KeyedProcessFunction<Long, PageViewCount, String>.Context ctx, Collector<String> out) throws Exception {
            // 有数据进来就添加或更新状态
            acc.put(value.url, value.cnt);

            Timestamp windowEnd = new Timestamp(value.windowEnd);
            Timestamp watermark = new Timestamp(ctx.timerService().currentWatermark());
            System.out.println("当前水位线 " + watermark + " 窗口 " + windowEnd + " 累加器 " + acc.entries());

            // 注册windowEnd + 1(ms)触发排序的定时器,迟到数据进来注册的是已经过时的定时器不会立即触发,要等到水位线再次更新时才会触发
            ctx.timerService().registerEventTimeTimer(value.windowEnd + 1L);
            // 注册windowEnd + 1(min)触发清空状态的定时器,迟到事件的1分钟也过去了,此时窗口关闭累加器不会再更新
            ctx.timerService().registerEventTimeTimer(value.windowEnd + 60000L);
        }

        @Override
        public void onTimer(long timestamp, KeyedProcessFunction<Long, PageViewCount, String>.OnTimerContext ctx, Collector<String> out) throws Exception {
            super.onTimer(timestamp, ctx, out);
            System.out.println("当前水位线 " + new Timestamp(ctx.timerService().currentWatermark()) + " 触发定时器 " + new Timestamp(timestamp));

            // 当有多个定时器时可以先根据触发时间判断是哪个定时器
            if (timestamp == ctx.getCurrentKey() + 60000L) {
                // 后面的定时器触发时,前面的定时器肯定已经触发执行完了,清空状态
                acc.clear();
                // 结束进程,不然后面排序代码还会执行,而此时acc==null结果就不对了
                return;
            }

            // 获取窗口信息,当按照窗口分组时有两种方法获取windowEnd
//            long windowEnd = timestamp - 1L;
            long windowEnd = ctx.getCurrentKey();
            long windowStart = windowEnd - 600 * 1000L;

            // MapState本身不支持排序,需要借助ArrayList
            ArrayList<Map.Entry<String, Integer>> pageViewCounts = Lists.newArrayList(acc.entries());
            pageViewCounts.sort(new Comparator<Map.Entry<String, Integer>>() {
                @Override
                public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                    return o2.getValue() - o1.getValue();  // 降序
                }
            });

            // 因为要等迟到事件,此时还不能清空累加器,要等到allowedLateness的1min之后再清空
//            acc.clear();

            // 将排名结果拼接成字符串展示,实际应用场景可以写入数据库
            StringBuilder sb = new StringBuilder();
            sb.append("==============================================\n");
            sb.append("窗口区间 [" + new Timestamp(windowStart) + ", " + new Timestamp(windowEnd) + ")\n");
            // 排序后取前3,注意索引是从0开始
            for (int i = 0; i < Math.min(num, pageViewCounts.size()); i++) {
                Map.Entry<String, Integer> entry = pageViewCounts.get(i);
                sb.append("NO" + (i+1) + " 页面 " + entry.getKey() + " 访问次数 " + entry.getValue() + "\n");
            }

            // 收集结果往下游发送
            out.collect(sb.toString());
        }
    }
}
