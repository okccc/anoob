package com.okccc.warehouse.flink;

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
 * Desc: WordCount案例
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
         * flink架构
         * JobManager：作业管理器,对应一个jvm进程,包括ResourceManager、Dispatcher和JobMaster三个线程
         * ResourceManager：管理TaskManager的静态资源slot(插槽)
         * Dispatcher：在web界面提交flink应用程序,并为提交的作业启动一个新的JobMaster,命令行提交不需要
         * JobMaster：负责单个JobGraph的执行,flink可以同时运行多个作业,每个作业都有自己的JobMaster
         * TaskManager：任务管理器,对应一个jvm进程,由task slot控制任务数,即并发执行能力,子任务可以共享slot,子任务就是程序中的各种算子
         *
         * 并行度
         * 算子的subtask个数,流的并行度通常是所有算子的最大并行度,一个任务槽最多运行一个并行度,parallelism(动态) <= task slot(静态)
         * one-to-one：map/filter/flatMap基本转换算子,元素个数和顺序保持不变,相同并行度的one-to-one算子可以形成任务链,减少网络io
         * redistributing：keyBy键控流转换算子,基于hashCode按键分区,broadcast和rebalance随机重分区,类似spark的shuffle
         *
         * State
         * 算子状态(Operator State)：可见范围是当前任务槽(范围太广,没什么用)
         * 键控状态(Keyed State)：可见范围是当前key,包括ValueState/ListState/MapState/ReducingState & AggregatingState
         * 状态后端：默认内存级别(MemoryStateBackend),负责读写本地状态,以及将checkpoint状态写入远程hdfs存储
         *
         * Checkpoint
         * flink故障恢复机制的核心就是检查点,会定期拷贝当前状态(快照),时间节点是当所有任务都处理完一个相同输入数据时(检查点分界线)
         * 故障恢复时重启应用 -> 从checkpoint读取数据并将状态重置 -> 开始消费并处理检查点到发生故障之间的所有数据
         * 检查点同步实现：暂停应用,保存状态到检查点,再重新恢复应用(sparkStreaming)
         * 检查点异步实现：基于Chandy-Lamport分布式异步快照算法,将检查点的保存和数据处理分开,不暂停应用(flink)
         *
         * 端到端的Exactly-Once
         * flink-source端：FlinkKafkaConsumer会保存消费数据的偏移量,故障恢复时由连接器重置偏移量
         * flink流处理端：checkpoint机制保证flink内部状态一致性
         * flink-sink端：从故障恢复时数据不会重复写入外部系统,可以借助幂等写入或事务的原子性实现,FlinkKafkaProducer采用的是2PC
         * 幂等写入(Idempotent Write)：对于特定数据结构,重复写入数据结果不变,比如hashmap/redis键值对/mysql、es等有主键id的数据库
         * 两阶段提交(two-phase-commit,2PC)：sink任务会将每个checkpoint接收到的数据输出到支持事务的下游系统(mysql/kafka)但暂不提交,
         * 等到checkpoint完成时会正式提交,commit失败会回滚,flink提供了TwoPhaseCommitSinkFunction,2PC是解决分布式事务问题的常用方法
         */

        // 创建流处理执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        // 配置参数优先级：算子并行度(代码写死) > 全局并行度(代码写死) > flink run -p(动态指定) > flink-conf.yaml(集群配置)
        // Source算子并行度设置为1可以保证数据有序
        // reduce这种聚合算子最好是能通过提交脚本-p动态扩展,所以代码一般不设置全局并行度,不然会覆盖动态指定,而具体的算子并行度则不会
        env.setParallelism(1);
        // 设置状态后端,通常是在工程中统一配置
//        env.setStateBackend(new FsStateBackend("file:///Users/okc/projects/anoob/input/cp", false));
        // 10秒保存一次检查点,flink默认只保存最近一次检查点即可正确恢复程序
        env.enableCheckpointing(10 * 1000L);

        // 实时：监听socket数据流,先在终端开启`nc -lk 9999`
        DataStreamSource<String> inputStream = env.socketTextStream("localhost", 9999);
        // 离线：flink是流批统一的,离线数据集也会当做流来处理,每来一条数据都会驱动程序运行并输出一个结果,ss批处理只会输出最终结果
        DataStreamSource<String> inputStream02 = env.fromElements("aaa bbb", "aaa bbb");
        // 自定义数据源
        DataStreamSource<Event> inputStream03 = env.addSource(new UserActionSource());

        // 针对流中每个输入元素：map输出1个元素,filter输出0/1个元素,flatMap输出0/1/N个元素,flatMap是map和filter的泛化实现
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
        }).setParallelism(1);

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
