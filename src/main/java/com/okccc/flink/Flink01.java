package com.okccc.flink;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Random;

/**
 * Author: okccc
 * Date: 2021/9/1 下午2:35
 * Desc: WordCount案例、自定义数据源SourceFunction
 */
public class Flink01 {
    public static void main(String[] args) throws Exception {
        /*
         * 数据处理架构演变
         * OLTP - OLAP - LAMBDA - Flink(有状态的流处理,在本地维护状态并定期checkpoint到远端hdfs等文件系统)
         * OLTP：初期数据很少,mysql搞定一切
         * OLAP：数据越来越多,业务也越来越复杂,将mysql数据导入hive做统计分析,mysql只负责最基本的增删改查功能,解耦
         * LAMBDA：ss保证低延迟,hive保证结果准确(ss无法处理迟到数据),相同的计算逻辑要维护实时离线两套代码,冗余,所以流批一体的flink应运而生
         *
         * flink主要特点
         * 1.事件驱动：以事件的发生来驱动程序运行,通过event-log而不是rest调用进行通信,并将应用程序的数据保存为本地状态而不是写入外部数据库
         * flink是来一条数据就触发一次计算而ss是攒一批数据再计算,redis的多路io复用和nginx负载均衡底层都使用了linux内核的epoll(event-poll)
         * 2.基于流的世界观：flink认为一切都是由流组成,离线数据是有界流,实时数据是无界流,流批统一架构
         * 3.支持时间语义：EventTime + Watermark可以处理迟到和乱序数据保证结果准确,sparkStreaming没有时间语义概念,无法处理延迟数据
         * 4.支持精准消费：exactly-once的状态一致性保证
         * 5.分层api：顶层的table api和sql使用方便,中间层的DataStream是核心api,底层的ProcessFunction可以搞定一切
         *
         * 为什么flink比spark延迟低？
         * spark采用RDD数据结构,是离线数据集,所以spark是批处理,RDD将数据划分到不同分区进行计算,分区间由于数据量不一致等原因会存在速度差异,
         * 比如同一个Stage内部多个分区间的map算子有快有慢,必须要等当前Stage内的所有算子全部执行完才能继续下一个Stage,所以spark有秒级延迟
         * flink采用Integer/String/Long/POJO类等数据结构,是标准的流处理模式,map - keyBy - reduce一直往下运行不用等,所以flink没有延迟
         *
         * flink架构
         * JobManager：作业管理器,对应一个jvm进程,包括ResourceManager、Dispatcher和JobMaster三个线程
         * ResourceManager：管理TaskManager的静态资源slot(插槽)
         * Dispatcher：在web界面提交flink应用程序,并为提交的作业启动一个新的JobMaster,命令行提交不需要
         * JobMaster：负责单个JobGraph的执行,flink可以同时运行多个作业,每个作业都有自己的JobMaster
         * TaskManager：任务管理器,对应一个jvm进程,由task slot控制任务数,即并发执行能力,子任务可以共享slot,子任务就是程序中的各种算子
         *
         * 并行度
         * 算子的子任务subtask个数,流的并行度通常是所有算子的最大并行度,一个任务槽最多运行一个并行度,parallelism(动态) <= task slot(静态)
         * one-to-one：map/filter/flatMap基本转换算子,元素个数和顺序保持不变,相同并行度的one-to-one算子可以形成任务链,减少网络io
         * redistributing：keyBy键控流转换算子,基于hashCode按键分区,broadcast和rebalance随机重分区,类似spark的shuffle
         *
         * 状态管理
         * 算子状态(Operator State)：可见范围是当前任务槽(范围太广,没什么用)
         * 键控状态(Keyed State)：可见范围是当前key,包括ValueState/ListState/MapState/ReducingState & AggregatingState
         * 状态后端：默认内存级别(MemoryStateBackend),负责本地状态的存储、访问和维护,以及将检查点checkpoint状态写入远程hdfs存储
         *
         * 一致性检查点
         * flink故障恢复机制的核心就是应用状态的一致性检查点,就是当所有任务恰好处理完一个相同输入数据时(检查点屏障),会将当前状态做一份拷贝(快照)
         * 故障恢复机制保证flink内部的精确一次性：1.重启应用 2.从checkpoint读取状态,将状态重置 3.开始消费并处理检查点到发生故障之间的所有数据
         * 检查点的实现算法
         * 同步实现：暂停应用,保存状态到检查点,再重新恢复应用(sparkStreaming)
         * 异步实现：基于Chandy-Lamport的分布式异步快照算法,将检查点的保存和数据处理分开,不暂停应用(flink)
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
//        env.enableCheckpointing(10 * 1000L);

        // Source
        // 实时：监听socket数据流,先在终端开启`nc -lk 9999`
        DataStreamSource<String> inputStream = env.socketTextStream("localhost", 9999);
        // 离线：flink是流批统一的,即便是离线数据集也会当做流来处理,每来一条数据都会驱动一次整个程序运行并输出一个结果,ss批处理只会输出最终结果
        DataStreamSource<String> inputStream02 = env.fromElements("aaa bbb", "aaa bbb");
        // 自定义数据源
        DataStreamSource<Event> inputStream03 = env.addSource(new UserActionSource());

        // Transform
        // 针对流中每个输入元素：map输出1个元素,filter输出0/1个元素,flatMap输出0/1/N个元素,flatMap是map和filter的泛化实现
        SingleOutputStreamOperator<WordCount> mapStream = inputStream.flatMap(new FlatMapFunction<String, WordCount>() {
            // 输入类型：流中元素String,输出类型：WordCount对象
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
        // key可以是输入元素本身,也可以是任意的Integer/String/Boolean,比如1或者true这样的常量值作为key表示将数据都划分到同一个分区
        KeyedStream<WordCount, String> keyedStream = mapStream.keyBy(new KeySelector<WordCount, String>() {
            // 输入类型：流中元素WordCount对象,输出类型：分组字段String
            @Override
            public String getKey(WordCount value) {
                return value.word;
            }
        });

        // 聚合：累加器编程思想,每个key都有自己的累加器,本质上是一个状态变量,每条数据进来和累加器滚动聚合完就丢掉,算子内部只维护累加器往下游发送
        // 比如求平均值只存累加器和元素个数就行,spark要把所有数据都存下来计算,操作KeyedStream的滚动聚合算子包括sum/max/min/minBy/maxBy
        // reduce是滚动算子的泛化实现,但是累加器和输入元素类型一致无法灵活修改,并且每次更新完都会往下游发送无法控制频率,所以还有更底层的process
        SingleOutputStreamOperator<WordCount> result = keyedStream.reduce(new ReduceFunction<WordCount>() {
            // 输入类型：流中元素WordCount对象,输出类型：WordCount对象
            @Override
            public WordCount reduce(WordCount value1, WordCount value2) {
                // 定义聚合规则
                return new WordCount(value1.word, value1.count + value2.count);
            }
        });

        // 输出到控制台
        result.print();

        // 启动程序
        env.execute("WordCount");
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
                // 通过collect方法往下游发送数据
                ctx.collect(new Event(
                        userArr[random.nextInt(userArr.length)],
                        urlArr[random.nextInt(urlArr.length)],
                        Calendar.getInstance().getTimeInMillis()
                ));
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
    public static class WordCount {
        public String word;
        public Integer count;

        public WordCount() {
        }

        public WordCount(String word, Integer count) {
            this.word = word;
            this.count = count;
        }

        @Override
        public String toString() {
            // 可以自定义WordCount对象的输出格式
            return "WordCount{" +
                    "word='" + word + '\'' +
                    ", count=" + count +
                    '}';
        }
    }

    // POJO类
    public static class Event {
        public String user;
        public String url;
        public Long timestamp;

        public Event() {
        }

        public Event(String user, String url, Long timestamp) {
            this.user = user;
            this.url = url;
            this.timestamp = timestamp;
        }

        // 模拟flink源码里大量使用的of语法糖,这样就不用每次都去写new()
        public static Event of(String user, String url, Long timestamp) {
            return new Event(user,url,timestamp);
        }

        @Override
        public String toString() {
            return "OrderEvent{" +
                    "user='" + user + '\'' +
                    ", url='" + url + '\'' +
                    // 转换Long类型的时间戳
                    ", timestamp=" + new Timestamp(timestamp) +
                    '}';
        }
    }
}
