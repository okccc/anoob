package com.okccc.bigdata.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;

/**
 * Author: okccc
 * Date: 2020/11/29 22:04
 * Desc: 模拟kafka消费者,实际场景一般是SparkStreaming或Flink
 */
public class ConsumerDemo {
    public static void main(String[] args) {
        /*
         * 消息丢失和重复消费
         * 消息丢失：生产者端ack=0/1,消费者端先提交后消费
         * 重复消费：生产者端ack=-1(all),消费者端先消费后提交
         *
         * 消费者数据可靠性
         * 1).自动提交
         * kafka默认每5秒自动提交一次偏移量 enable.auto.commit=true & auto.commit.interval.ms=5000(ms)
         * 但是消费者可能出现宕机情况,5秒不一定能处理完,自动提交显然不能保证消费者的exactly once
         * 2).手动提交
         * 先消费后提交：如果消费数据后提交offset前consumer挂了,没提交成功,恢复之后会从旧的offset开始消费,导致重复消费(at least once)
         * 先提交后消费：如果提交offset后消费数据前consumer挂了,没消费成功,恢复之后会从新的offset开始消费,导致数据丢失(at most once)
         * 所以不管是自动提交还是手动提交都不能保证消息的精准消费,因为消费数据和提交offset这两件事不在同一个事务中
         * 3).精准消费
         * 方案1：利用关系型数据库的事务保证消费数据和提交offset的原子性,缺点是事务性能一般,大数据场景还得考虑分布式事务,适用于少量聚合数据
         * 方案2：at least once + 手动实现幂等性 = exactly once 适用于大量明细数据
         * 实现幂等性之redis：通过set数据结构实现,key存在就进不来,保留前面的
         * 实现幂等性之es：通过索引的doc_id实现,存在就覆盖,保留后面的,其实有主键id的数据库都可以实现幂等性操作
         * 4).offset维护
         * kafka0.9版本以前保存在zk,但是zk并不适合频繁写入业务数据
         * kafka0.9版本以后保存在__consumer_offsets,弊端是提交偏移量的数据流必须是InputDStream[ConsumerRecord[String, String]]
         * 因为offset存储于HasOffsetRanges,只有kafkaRDD实现了该特质,转换成别的RDD就无法再获取offset,生产环境通常使用redis保存offset
         * kafka自己也会存一份,但是我们是从redis读写offset而不是使用kafka的latest/earliest/none
         */

        // 1.消费者属性配置
        Properties prop = new Properties();
        // 必选参数
//        prop.put("bootstrap.servers", "localhost:9092");                     // 本地kafka
//        prop.put("bootstrap.servers", "10.18.0.7:9092,10.18.0.8:9092,10.18.0.9:9092");  // 生产kafka
//        prop.put("bootstrap.servers", "10.18.3.21:9092,10.18.3.22:9092,10.18.3.23:9092");  // 测试kafka
        prop.put("bootstrap.servers", "10.18.2.7:9092,10.18.2.8:9092,10.18.2.9:9092");  // 新集群kafka
//        prop.put("bootstrap.servers", "10.100.176.47:9092");  // 腾讯云kafka
        prop.put("key.deserializer", StringDeserializer.class.getName());    // key的反序列化器
        prop.put("value.deserializer", StringDeserializer.class.getName());  // value的反序列化器
        prop.put("group.id", "gg");                                          // 消费者组,consumer-group之间互不影响
        // 可选参数
        prop.put("enable.auto.commit", "false");  // true自动提交(默认),false手动提交
        // 当kafka中没有初始偏移量或找不到当前偏移量(比如数据被删除)才会生效,此时会粗粒度地指定从latest(默认)/earliest/none(抛异常)开始消费
        prop.put("auto.offset.reset", "latest");

        // 2.创建消费者对象,参数是topicName和eventLog
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(prop);

        // 3.订阅topic集合
        List<String> list = new ArrayList<>();
        list.add("amplitude02");
//        list.add("eduplatform01");
//        list.add("nginx");
        consumer.subscribe(list);

        // 4.从kafka拉取数据
        while (true) {
            // poll会从消费者上次提交的offset处拉取数据
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));

            // assignment获取消费者分配到的分区信息
            Set<TopicPartition> assignment = consumer.assignment();
//            System.out.println(assignment);  // [nginx-0, nginx-1, nginx-2]

            // seek会重置消费者分配到的分区偏移量,可以更加细粒度地控制offset
//            // a.从头开始消费,分区的起始位置是0,但是随着日志定时清理,起始位置也会越来越大
//            consumer.seekToBeginning(assignment);
//            // b.从末尾开始消费
//            consumer.seekToEnd(assignment);
//            // c.从指定位置开始消费
//            for (TopicPartition tp : assignment) {
//                consumer.seek(tp,10000);
//            }
            // d.从某个时间点开始消费(更符合实际需求)
            Map<TopicPartition, Long> timestampsToSearch = new HashMap<>();
            for (TopicPartition tp : assignment) {
                // 设置查询分区的时间戳
                timestampsToSearch.put(tp, System.currentTimeMillis() - 24 * 3600 * 1000);
            }
            Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(timestampsToSearch);
            for (TopicPartition tp : assignment) {
                // 获取该分区的offset和时间戳
                OffsetAndTimestamp offsetAndTimestamp = offsets.get(tp);
                // 如果offset和时间戳不为空,说明当前分区有符合时间戳的条件信息
                if (offsetAndTimestamp != null) {
                    // 根据时间戳寻址
                    consumer.seek(tp, offsetAndTimestamp.offset());
                }
            }

            // 消息被封装成ConsumerRecord对象
            for (ConsumerRecord<String, String> record : records) {
                // 获取每条消息的元数据信息
//                System.out.println("topic=" + record.topic() + ", partition=" + record.partition() + ", offset="
//                        + record.offset() + ", value=" + record.value());
                if (record.value().contains("e4d0e46863d445ef961379788abd836b")) {
                    System.out.println(record.value());
                }
            }
            // 手动提交offset
            consumer.commitSync();
        }
    }
}
