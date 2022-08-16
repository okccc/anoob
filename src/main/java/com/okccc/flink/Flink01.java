package com.okccc.flink;

import com.alibaba.fastjson.JSON;
import com.okccc.realtime.utils.MysqlUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkFunction;
import org.apache.flink.streaming.connectors.elasticsearch.RequestIndexer;
import org.apache.flink.streaming.connectors.elasticsearch7.ElasticsearchSink;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommand;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommandDescription;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisMapper;
import org.apache.flink.util.Collector;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Author: okccc
 * Date: 2021/9/1 下午2:35
 * Desc: flink简介
 */
public class Flink01 {
    public static void main(String[] args) throws Exception {
        /*
         * 电商用户行为分析
         * 统计分析(实时)：热门商品、热门页面、访问流量、app市场推广统计、页面广告点击量
         * 风险控制(实时)：页面广告黑名单过滤、恶意登录监控、订单支付失效监控、支付实时对账
         * 偏好分析(离线)：点赞、收藏、评价、用户画像、推荐系统
         *
         * 数据处理架构演变
         * OLTP - OLAP - LAMBDA - Flink(有状态的流处理,在本地维护状态并定期checkpoint到远端hdfs等文件系统)
         * OLTP：初期数据很少,mysql搞定一切
         * OLAP：数据越来越多,业务也越来越复杂,将mysql数据导入hive做统计分析,mysql只负责最基本的增删改查功能,解耦
         * LAMBDA：ss保证低延迟但无法处理迟到数据,需由hive保证结果准确性,相同逻辑要维护实时离线两套冗余代码,所以流批一体的flink应运而生
         *
         * flink主要特点
         * 1.事件驱动：以事件发生来驱动程序运行,通过event-log而不是rest调用进行通信,并将应用程序的数据保存为本地状态而不是写入外部数据库
         * flink是来一条数据触发一次计算而ss是攒一批数据再计算,redis多路io复用和nginx负载均衡底层都使用了linux内核的epoll(event-poll)
         * 2.基于流的世界观：flink认为一切都是由流组成,离线数据是有界流,实时数据是无界流,流批统一架构
         * 3.支持时间语义：EventTime + Watermark可以处理迟到和乱序数据保证结果准确,sparkStreaming没有时间语义概念,无法处理延迟数据
         * 4.支持精准消费：exactly-once的状态一致性保证
         * 5.分层api：顶层的table api和sql使用方便,中间层的DataStream是核心api,底层的ProcessFunction可以搞定一切
         *
         * 为什么flink比spark延迟低？
         * spark采用RDD数据结构,离线数据集,是批处理模式,RDD将数据划分到不同分区进行计算,分区间由于数据量不一致等原因会存在速度差异
         * 比如同一个Stage内部多个分区间的map算子有快有慢,必须要等所有map算子全部执行完(木桶原理)才能继续下一个Stage,会有秒级延迟
         * flink采用Integer/String/Long/POJO类等数据结构,是标准流处理模式,map -> keyBy -> reduce一直往下运行不用等,没有延迟
         *
         * flink部署模式
         * 会话模式：先启动一个集群并确定所有资源,提交的作业会竞争集群资源,资源不足就会提交失败,适合数据规模小执行时间短的任务
         * 单作业模式：为每个提交的作业都单独启动一个集群,资源隔离互不影响,作业完成就关闭集群释放资源,只支持yarn在flink1.15版本已弃用
         * 应用模式：上面main方法都运行在client,会占用大量网络带宽下载依赖和发送二进制数据到集群,于是不要客户端直接提交到JobManager运行
         *
         * flink运行架构
         * JobManager：负责任务管理和调度,对应一个jvm进程,包括下面三个线程
         * Dispatcher：提交应用并启动一个新的JobMaster,不是必须组件比如往yarn提交任务时就不需要
         * ResourceManager：负责资源分配和管理,资源是指TaskManager的任务槽task slots
         * JobMaster：接收要执行的应用,将JobGraph转换成ExecutionGraph,并向RM申请资源分发给TaskManager,每个job都对应一个JobMaster
         * TaskManager：执行任务处理数据,对应一个jvm进程,包含一定数量的任务槽task slots,对应其能并行处理的任务数
         *
         * 并行度
         * 并行度：算子之间有执行顺序,每来一条数据都要经过source/transform/sink依次执行,算子在同一时刻只能处理一条数据,为了提高吞吐量
         * 将算子拆分成多个并行子任务,分发到不同节点实现"并行计算",算子的子任务个数就是"并行度",不同算子可以设置不同并行度,程序取最大并行度
         * Source端并行度通常设置为kafka对应topic的分区数,如果消费速度跟不上生产速度可以考虑扩大kafka分区同时调大并行度
         * map/filter/flatMap等处理较快的算子和source端保持一致即可,keyBy之后的算子建议设置为2的整次幂
         * Sink端并行度要看下游系统的承受能力,如果是写kafka就和分区数保持一致即可
         * 算子链
         * source和map之间的数据流不会重分区,并行度相同的one-to-one算子可以合并成"算子链",减少资源消耗,类似spark窄依赖
         * map和keyBy之间的数据流会基于hashCode按键分区,属于redistributing操作,会伴随shuffle的过程,类似spark宽依赖
         * 任务槽
         * 每个任务都占据一个task slot,对应一组独立计算资源,但是不同算子耗费资源不一样,忙的忙死闲的闲死,为了充分利用集群资源,
         * 不同算子的子任务可以共享任务槽,并行度(TaskManager实际使用的并发,动态概念) <= 任务槽(TaskManager拥有的并发能力,静态概念)
         *
         * State
         * 无状态算子：不依赖其它数据,比如map/filter/flatMap只处理当前进来的数据
         * 有状态算子：会依赖其它数据,比如aggregate/window都会用到之前已经到达的数据,也就是所谓的状态
         * 算子任务会按并行度分为多个并行子任务执行,不同子任务占据不同task slot,所以状态在资源上是物理隔离的,只对当前子任务有效
         * 而且aggregate/window等有状态算子都会先keyBy,后续计算都是针对当前key的,所以状态也应该按照key隔离,于是状态分为以下两种
         * 算子状态(Operator State)：可见范围是当前子任务,相当于本地变量,范围太大应用场景较少
         * 按键分区状态(Keyed State)：可见范围是当前key,包括ValueState/ListState/MapState/AggregatingState/ReducingState
         *
         * Checkpoint
         * flink故障恢复机制的核心就是检查点,会定期拷贝当前状态(快照),时间节点是当所有任务都处理完一个相同输入数据时(检查点分界线)
         * "检查"是针对故障恢复的结果而言,故障恢复后继续处理的结果应该和发生故障前完全一致,所以也叫"一致性检查点",默认只保存最近一次检查点
         * 检查点同步实现：暂停应用,保存状态到检查点,再重新恢复应用(sparkStreaming)
         * 检查点异步实现：基于Chandy-Lamport分布式异步快照算法,将检查点的保存和数据处理分开,不暂停应用(flink)
         *
         * Checkpoint & Savepoint
         * Checkpoint由flink创建和删除,会定期自动触发,为意外失败的作业提供恢复机制,job停止后自动删除,可手动更改检查点保留策略
         * Savepoint由用户创建和删除,需要手动触发,为版本升级/代码更新/调整并行度等有目的的暂停提供恢复机制,创建后就一直存在需手动删除
         * 保存点中状态是以(算子id-状态名称)这样的key-value组织起来的,保存点在程序修改后能兼容的前提是状态的拓扑结构和数据类型保持不变,
         * 对于不设置id的算子flink会自动配置,这样应用重启后可能会因为id不同导致无法兼容以前的状态,为了方便后期维护建议为每个算子都指定id
         *
         * StateBackend
         * 状态后端负责两件事：1.读写本地状态 2.将检查点写入远程持久化存储
         * HashMapStateBackend(默认)：本地状态放内存,读写速度极快但不安全,并且会消耗集群大量内存资源,适合较大state和window
         * EmbeddedRocksDBStateBackend：本地状态放RocksDB,硬盘存储读写要序列化/反序列化降低性能但是安全,适合超大state和window
         *
         * 状态一致性(结果准确性)
         * 最多一次：任务故障直接重启啥也不干,会丢数据但是速度最快
         * 至少一次：生产上至少得保证不丢数据,即任务故障时能够重放数据,比如kafka可以重置偏移量,有些特殊场景重复处理不影响结果比如uv
         * 精准一次：在不丢数据的基础上还要保证每个数据只会处理一次,flink使用轻量级快照机制checkpoint实现exactly once
         *
         * 端到端的状态一致性
         * Source端：FlinkKafkaConsumer会将消费数据的偏移量保存为算子状态并写入检查点,故障时从检查点恢复状态并由连接器重置偏移量
         * flink内部：checkpoint机制保证flink内部状态一致性
         * Sink端：故障恢复时不会重复写入数据,可以借助幂等写入或事务的原子性实现,输出端难点在于"覆水难收",写入外部系统的数据难以撤回
         * 幂等写入(Idempotent)：比如往HashMap插入相同键值对结果不变,但是并没有真正解决重复写入问题,只是重复写入不影响最终结果而已
         * 限制在于外部系统必须支持幂等写入,比如redis/mysql/es,并且在做保存点时可能会出现短暂不一致的情况
         * 两阶段提交(2PC)：将事务和检查点绑定,当第一条数据到来或者收到检查点分界线时sink端就开启事务,后面数据都由这个事务写入外部系统,
         * 但此时事务还没提交,所以外部系统的数据并不可用,处于"预提交状态",等到sink端收到JobManager发来检查点完成的通知时才正式提交事务
         *
         * 反压
         * 场景1：当前节点发送速率跟不上生产速率,比如flatMap算子一条输入多条输出,当前节点就是反压根源
         * 场景2：下游节点接收速率较慢,通过反压机制限制了上游节点发送速率,继续排查下游节点,一直找到第一个OK的就是反压根源(常见)
         * 反压可能导致state过大甚至OOM以及checkpoint超时失败,先找到第一个出现反压的节点,根源要么是这个节点要么是紧挨着的下游节点
         * WebUI查看算子反压程度：Overview - BackPressure - Backpressure Status(OK/LOW/HIGH)
         * Metrics指标分析：buffers.outPoolUsage发送端buffer使用率/buffers.inPoolUsage接收端buffer使用率
         * 反压原因：1.数据倾斜 2.cpu/内存资源不足 3.外部组件交互
         * 查看数据是否倾斜：Overview - SubTasks - RecordsReceived & RecordsSent
         * 火焰图：对TaskManager进行CPU profile,横向是出现次数对应执行时长,纵向是调用链顶层就是正在执行函数,过宽说明存在性能瓶颈
         * 下载GC日志：对TaskManager进行内存分析,尤其是full gc后老年代剩余大小
         * Source端或Sink端性能较差,看看kafka是否需要扩容、clickhouse是否达到瓶颈、hbase的rowkey是否遇到热点问题
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 配置参数优先级：算子并行度(代码写死) > 全局并行度(代码写死) > flink run -p(动态指定) > flink-conf.yaml(集群配置)
        // Source算子并行度设置为1可以保证数据有序
        // reduce这种聚合算子最好是能通过提交脚本-p动态扩展,所以代码一般不设置全局并行度,不然会覆盖动态指定,而具体的算子并行度则不会
        env.setParallelism(1);

        // 实时：监听socket数据流,先在终端开启`nc -lk 9999`
        DataStreamSource<String> inputStream = env.socketTextStream("localhost", 9999);
        // 离线：flink是流批统一的,离线数据集也会当做流来处理,每来一条数据都会驱动程序运行并输出一个结果,ss批处理只会输出最终结果
        DataStreamSource<String> inputStream02 = env.fromElements("aaa bbb", "aaa bbb");
        // 自定义数据源
        DataStreamSource<Event> inputStream03 = env.addSource(new UserActionSource());

        // 针对流中每个输入元素：map输出1个元素,filter输出0/1个元素,flatMap输出0/1/N个元素,flatMap是map和filter的泛化实现
        // 所以不能用map算子过滤数据,会报空指针异常,filter和flatMap算子可以
        // MapFunction/KeySelector/ReduceFunction/ProcessWindowFunction/AggregateFunction等所有UDF都继承自Function接口
        SingleOutputStreamOperator<WordCount> mapStream = inputStream.flatMap(new FlatMapFunction<String, WordCount>() {
            // 输入类型String,输出类型WordCount
            @Override
            public void flatMap(String value, Collector<WordCount> out) {
                String[] words = value.split(" ");
                // 通过collect方法向下游发送数据
                for (String word : words) {
                    out.collect(new WordCount(word, 1));
                }
            }
        }).uid("flatmap").setParallelism(1);

        // 分组：shuffle操作
        // 按照key将数据分发到不同的逻辑分区,相同key一定在同一个任务槽(物理分区),所以会有数据倾斜问题,不同key有可能在同一个任务槽
        // key可以是输入元素本身,也可以是任意的Integer/String/Boolean,比如1或true这样的常量值作为key表示将数据都划分到同一个分区
        KeyedStream<WordCount, String> keyedStream = mapStream.keyBy(new KeySelector<WordCount, String>() {
            // 输入类型WordCount,分组字段String
            @Override
            public String getKey(WordCount value) {
                return value.word;
            }
        });

        // 聚合：累加器编程思想,每个key都有单独的累加器(状态),每条数据进来和累加器滚动聚合完就丢掉,算子内部只维护累加器往下游发送
        // 比如求平均值只存累加器和元素个数就行,而spark是把所有数据都存下来计算,flink操作KeyedStream的滚动聚合算子包括sum/max/min
        // reduce是滚动算子的泛化实现,但是输出类型和输入类型相同无法修改,并且每次更新完都会往下游发送无法控制频率,所以还有更底层的process
        SingleOutputStreamOperator<WordCount> result = keyedStream.reduce(new ReduceFunction<WordCount>() {
            // 输入类型WordCount,输出类型WordCount
            @Override
            public WordCount reduce(WordCount value1, WordCount value2) {
                // 定义滚动聚合规则
                value1.setCount(value1.getCount() + value2.getCount());
                return value1;
            }
        });

        // 输出到控制台
        result.print();
        // 输出到mysql(不常用,因为mysql并发性能一般,通常不超过100)
        result.addSink(new MyJdbcSink());
        // 输出到redis(不常用,因为redis并非持久化存储,更适合做缓存)
        result.addSink(new RedisSink<>(new FlinkJedisPoolConfig.Builder().setHost("localhost").build(), new MyRedisMapper()));
        // 输出到es,适合存放大量明细数据
        List<HttpHost> httpHosts = new ArrayList<>();
        httpHosts.add(new HttpHost("localhost", 9200, "http"));
        ElasticsearchSink.Builder<Event> esBuilder = new ElasticsearchSink.Builder<>(httpHosts, new ElasticsearchSinkFunction<Event>() {
            @Override
            public void process(Event element, RuntimeContext ctx, RequestIndexer indexer) {
                System.out.println(element);
                // 创建IndexRequest对象
                IndexRequest indexRequest = new IndexRequest();
                // 指定_index和_id,索引会自动创建一般以日期结尾,不写id会自动生成uuid
                indexRequest.index("flink-es");
                // 添加_source,必须是json格式不然报错,将java对象转换成json字符串
                indexRequest.source(JSON.toJSONString(element), XContentType.JSON);
                // 执行index操作
                indexer.add(indexRequest);
            }
        });
        // flink来一条处理一条,将bulk批量操作的缓冲数设置为1
        esBuilder.setBulkFlushMaxActions(1);
        inputStream03.addSink(esBuilder.build());

        // 启动程序
        env.execute();
    }

    // 自定义数据源实现SourceFunction接口,数据源的泛型可以是Integer/Long/String/Double,也可以是POJO类
    public static class UserActionSource implements SourceFunction<Event> {
        // 模拟数据
        private boolean running = true;
        private final String[] userArr = {"grubby", "moon", "sky", "fly", "ted"};
        private final String[] urlArr = {"./home", "./cart", "./fav", "./prod?id=1", "./prod?id=2"};
        private final Random random = new Random();
        @Override
        public void run(SourceContext<Event> ctx) throws Exception {
            while (running) {
                // 随机生成数据
                String userId = userArr[random.nextInt(userArr.length)];
                String url = urlArr[random.nextInt(urlArr.length)];
                long timestamp = System.currentTimeMillis();
                // 通过collect方法往下游发送数据
                ctx.collect(Event.of(userId, url, timestamp));
                Thread.sleep(1000L);
            }
        }
        @Override
        public void cancel() {
            running = false;
        }
    }

    public static class NumberSource implements SourceFunction<Integer> {
        // 模拟数据
        private boolean running = true;
        private final Random random = new Random();
        @Override
        public void run(SourceContext<Integer> ctx) throws Exception {
            while (running) {
                ctx.collect(random.nextInt(10));
                Thread.sleep(1000L);
            }
        }
        @Override
        public void cancel() {
            running = false;
        }
    }

    // POJO类必须满足三个条件：公有类,公有字段,公有无参构造  类似scala的case class
    public static class Event {
        public String user;
        public String url;
        public Long timestamp;

        public Event() {}

        public Event(String user, String url, Long timestamp) {
            this.user = user;
            this.url = url;
            this.timestamp = timestamp;
        }

        // 模拟flink源码里大量使用的of语法糖,这样就不用每次都写new()
        public static Event of(String user, String url, Long timestamp) {
            return new Event(user, url, timestamp);
        }

        @Override
        public String toString() {
            return "Event{" +
                    "user='" + user + '\'' +
                    ", url='" + url + '\'' +
                    ", timestamp=" + new Timestamp(timestamp) +  // 转换Long类型的时间戳
                    '}';
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WordCount {
        public String word;
        public Integer count;
    }

    // 自定义SinkFunction
    public static class MyJdbcSink extends RichSinkFunction<WordCount> {
        // 声明连接和预编译
        private Connection conn;
        private PreparedStatement selectPS;
        private PreparedStatement insertPS;
        private PreparedStatement updatePS;
        @Override
        public void open(Configuration parameters) throws Exception {
            // 初始化
            conn = MysqlUtil.initConnection();
            selectPS = conn.prepareStatement("select * from wc where word = ?");
            insertPS = conn.prepareStatement("insert into wc values(?, ?)");
            updatePS = conn.prepareStatement("update wc set count = ? where word = ?");
        }
        @Override
        public void invoke(WordCount value, Context context) throws Exception {
            selectPS.setString(1, value.word);
            ResultSet rs = selectPS.executeQuery();
            if (rs.next()) {
                // 存在就更新
                updatePS.setInt(1, value.count);
                updatePS.setString(2, value.word);
                updatePS.execute();
            } else {
                // 不存在就添加
                insertPS.setString(1, value.word);
                insertPS.setInt(2, value.count);
                insertPS.execute();
            }
        }
        @Override
        public void close() throws Exception {
            // 释放资源
            MysqlUtil.close(conn, null, null);
        }
    }

    // 自定义RedisMapper
    public static class MyRedisMapper implements RedisMapper<WordCount> {
        @Override
        public RedisCommandDescription getCommandDescription() {
            return new RedisCommandDescription(RedisCommand.HSET, "wc");
        }
        @Override
        public String getKeyFromData(WordCount data) {
            return data.word;
        }
        @Override
        public String getValueFromData(WordCount data) {
            return data.count.toString();
        }
    }
}
