package com.okccc.kafka;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;

/**
 * @Author: okccc
 * @Date: 2020/11/29 22:04
 * @Desc: 模拟kafka消费者,实际场景一般是SparkStreaming或Flink
 *
 * 消费方式
 * push模式是消费者被动接受发送过来的数据,难以适应消费速率不同的消费者,消费者来不及处理可能会导致网络拥堵甚至程序崩溃
 * pull模式是消费者根据自身消费能力主动去broker拉数据,缺点是broker没有数据时会陷入空循环,需要指定超时参数timeout
 *
 * 消费者分区分配
 * Range(默认)：针对每个topic,将分区数/消费者数取余决定哪个consumer消费哪个partition,除不尽时前面的消费者会多消费1个分区,
 * 缺点是如果订阅N个topic的话,C0会多消费N个分区容易导致数据倾斜
 * RoundRobin：针对所有topic,将所有partition数和consumer数按照hashcode进行排序,然后通过轮询算法来分配哪个消费者消费哪个分区
 *
 * 消费者提高吞吐量(数据积压问题)
 * 1.消费者消费能力不足：增加topic的分区数和消费者数量,分区数=消费者数
 * 2.下游数据处理不及时：提高每批次拉取的数据量 fetch.max.bytes=50M(大小) max.poll.records=500(条数)
 *
 * 消费者数据可靠性
 * 1).自动提交
 * kafka默认每5秒自动提交一次偏移量,但是消费者可能出现宕机情况,5秒不一定能处理完,自动提交显然不能保证消费者的exactly once
 * 2).手动提交
 * 先消费后提交：如果消费数据后提交offset前consumer挂了,没提交成功,恢复之后会从旧的offset开始消费,导致重复消费(at least once)
 * 先提交后消费：如果提交offset后消费数据前consumer挂了,没消费成功,恢复之后会从新的offset开始消费,导致数据丢失(at most once)
 * 所以不管是自动提交还是手动提交都不能保证消息的精准消费,因为消费数据和提交offset这两件事不在同一个事务中
 * 3).精准消费
 * 方案1：利用关系型数据库的事务保证消费数据和提交offset的原子性,缺点是事务性能一般,大数据场景还得考虑分布式事务,适用于少量聚合数据
 * 方案2：at least once + 手动实现幂等性 = exactly once 适用于大量明细数据
 * 实现幂等性之redis：通过set数据结构实现,key存在就进不来,保留前面的
 * 实现幂等性之es：通过索引的doc_id实现,存在就覆盖,保留后面的,其实有主键id的数据库都可以实现幂等性操作
 *
 * offset维护
 * kafka0.9版本以前保存在zk,但是zk并不适合频繁写入业务数据,且会产生大量网络通信
 * kafka0.9版本以后保存在__consumer_offsets,弊端是提交偏移量的数据流必须是InputDStream[ConsumerRecord[String, String]]
 * 因为offset存储于HasOffsetRanges,只有kafkaRDD实现了该特质,转换成别的RDD就无法再获取offset,生产环境通常使用redis保存offset
 * kafka自己也会存一份,但是我们是从redis读写offset而不是使用kafka的latest/earliest/none
 * 消费者提交的偏移量是当前消费到的最新消息的offset+1,因为偏移量记录的是下一条即将要消费的数据
 */
public class ConsumerDemo {

    public static void main(String[] args) {
        // 1.消费者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.201.7.63:9092");  // 腾讯云db
//        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.100.113.98:9092");  // 腾讯云测试
//        prop.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.201.7.34:9092");  // 腾讯云log
        prop.put(ConsumerConfig.GROUP_ID_CONFIG, "g01");  // 消费者组,命令行不指定group时会随机分配一个
        prop.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());    // key反序列化器
        prop.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());  // value反序列化器
        // 消费者吞吐量
        prop.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 52428800);  // 每批次抓取最大数据量,默认50m
        prop.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);      // 每次拉取数据返回的最大记录数,默认500条
        // 其它参数
        prop.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, RoundRobinAssignor.class.getName());  // 分区分配方式
        prop.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");  // 是否自动提交偏移量
        prop.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");  // 当kafka没有初始偏移量或当前偏移量数据已删除时重置偏移量

        // 2.创建消费者对象,<String, String>是topics和record
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(prop);

        // 3.订阅topic集合
        List<String> topics = new ArrayList<>();
//        topics.add("amplitude02-new");
        topics.add("eduplatform01");
        consumer.subscribe(topics);
        // 订阅主题并指定分区(不常用)
//        ArrayList<TopicPartition> topicPartitions = new ArrayList<>();
//        topicPartitions.add(new TopicPartition("test", 0));
//        consumer.assign(topicPartitions);

        // 4.从kafka获取数据
        while (true) {
            // poll会从消费者上次提交的offset处拉取数据
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

            // assignment获取消费者分配到的分区信息
            Set<TopicPartition> assignment = consumer.assignment();
//            System.out.println(assignment);  // [nginx-0, nginx-1, nginx-2, ...]

            // seek会重置消费者分配到的分区偏移量,可以更加细粒度地控制offset,但是手动指定位置后每次poll的都是那个位置的数据
            // a.从头开始消费,分区的起始位置是0,但是随着日志定时清理,起始位置也会越来越大
//            consumer.seekToBeginning(assignment);
            // b.从末尾开始消费
//            consumer.seekToEnd(assignment);
            // c.从指定偏移量开始消费
//            for (TopicPartition tp : assignment) {
//                consumer.seek(tp, 73935099);
//            }
            // d.从指定时间点开始消费(更符合实际需求,比如要查找nginx日志2022-03-07 10:02:20的某条数据,将其转换成linux时间戳就行)
            Map<TopicPartition, Long> timestampsToSearch = new HashMap<>();
            for (TopicPartition tp : assignment) {
                // 设置查询分区的时间戳
                timestampsToSearch.put(tp, 1646618540000L);  // 精准定位
//                timestampsToSearch.put(tp, System.currentTimeMillis() - 2 * 3600 * 1000);  // 范围搜索
            }
            Map<TopicPartition, OffsetAndTimestamp> map = consumer.offsetsForTimes(timestampsToSearch);
            for (TopicPartition tp : assignment) {
                // 获取该分区的offset和时间戳
                OffsetAndTimestamp offsetAndTimestamp = map.get(tp);
                // 如果offset和时间戳不为空,说明当前分区有符合时间戳的条件信息
                if (offsetAndTimestamp != null) {
                    // 根据时间戳寻址
                    consumer.seek(tp, offsetAndTimestamp.offset());
                }
            }

            // 消息被封装成ConsumerRecord对象
            for (ConsumerRecord<String, String> record : records) {
                // 获取每条消息的元数据信息
//                System.out.println(record.timestamp() + " - " + record.topic() + " - " + record.partition() + " - " + record.offset() + " - " + record.value());
                if (record.value().contains("1753005805632")) {
                    // record.timestamp(): kafka接收数据的时间戳,消费者5秒poll一次,每个轮询批次内的数据timestamp是一样的
                    // record.offset(): 消息在kafka分区的偏移量,可以用来判断是否重复消费,partition - offset相同说明重复消费了
                    System.out.println(record.timestamp() + " - " + record.topic() + " - " + record.partition() + " - " + record.offset() + " - " + record.value());
                }
            }

            // 手动提交offset
            consumer.commitAsync();
        }
    }
}
