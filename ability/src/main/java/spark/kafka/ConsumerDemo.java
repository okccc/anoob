package spark.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Author: okccc
 * Date: 2020/11/29 22:04
 * Desc: 模拟kafka消费者
 */
public class ConsumerDemo {
    public static void main(String[] args) {
        /*
         * kafka0.11版本后的幂等性机制可以保证生产者的exactly once
         * kafka默认每5秒自动提交一次偏移量 enable.auto.commit=true & auto.commit.interval.ms=5000(ms)
         * 消费者可能出现宕机情况,5秒不一定能处理完,所以自动提交显然不能保证消费者的exactly once,需要手动提交
         *
         * 先消费后提交：如果消费完之后提交之前consumer挂了,没提交成功,恢复之后会从旧的offset开始消费,导致重复消费(at least once)
         * 先提交后消费：如果提交之后消费完之前consumer挂了,没消费成功,恢复之后会从新的offset开始消费,导致数据丢失(at most once)
         *
         * 所以不管是自动提交还是手动提交都不能保证消息的精准消费,因为消费数据和提交offset这两件事不在同一个事务中
         * 方案1：利用关系型数据库的事务保证消费数据和提交offset的原子性,失败了就回滚,这样就无所谓先消费还是先提交
         * 弊端：必须是支持事务的关系型数据库,无法使用功能强大的nosql;事务本身性能不好;大数据场景可能要考虑复杂的分布式事务
         * 适用于聚合后的少量数据,并且支持事务的关系型数据库
         * 方案2：at least once + 手动实现幂等性 = exactly once 一般有主键的数据库都支持幂等性操作upsert,
         * 适用于处理大量明细数据,或者不支持事务的nosql数据库
         *
         * 偏移量保存在哪里？
         * kafka0.9版本以前保存在zk,但是zk并不适合频繁写入业务数据
         * kafka0.9版本以后保存在__consumer_offsets,弊端是提交偏移量的数据流必须是InputDStream[ConsumerRecord[String, String]]
         * 实际运算经常会做数据结构转换,就无法使用xxDstream.asInstanceOf[CanCommitOffsets].commitAsync(offsetRanges)提交offset
         * 因为offset存储于HasOffsetRanges,只有kafkaRDD继承了它,转换成别的RDD就无法再获取offset,所以生产中使用redis/mysql保存offset
         */

        // 消费者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put("bootstrap.servers", "dev-bigdata-cdh2:9092,dev-bigdata-cdh3:9092,dev-bigdata-cdh4:9092");  // kafka集群地址
        prop.put("key.deserializer", StringDeserializer.class.getName());  // key的反序列化器
        prop.put("value.deserializer", StringDeserializer.class.getName());  // value的反序列化器
        prop.put("group.id", "ttt");  // 消费者组
        // 可选参数
        prop.put("enable.auto.commit", "false");  // true自动提交(默认),false手动提交
        prop.put("auto.offset.reset", "earliest");  // 没有offset时从哪里开始消费,latest(默认)/earliest/none

        // 创建消费者对象
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(prop);
        // 订阅topic集合
        List<String> list = new ArrayList<>();
        list.add("nginx");
        // 默认是自动提交offset方式
        consumer.subscribe(list);
//        // 手动提交offset方式
//        consumer.subscribe(list, new ConsumerRebalanceListener() {
//            @Override
//            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
//
//            }
//
//            @Override
//            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
//
//            }
//        });

        // 从kafka拉取数据
        while (true) {
            // 拉取一批消息
            ConsumerRecords<String, String> records = consumer.poll(10);
            // 遍历消息记录列表
            for (ConsumerRecord<String, String> record : records) {
                // 获取每条消息的元数据信息
                System.out.println("topic=" + record.topic() +
                        ",partition=" + record.partition() +
                        ",offset=" + record.offset() +
                        ",key=" + record.key() +
                        ",value=" + record.value() +
                        ",headers=" + record.headers());
            }
            // 提交offset
            consumer.commitAsync();
        }
    }
}
