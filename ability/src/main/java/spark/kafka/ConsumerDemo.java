package spark.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author okccc
 * @version 1.0
 * @date 2020/11/29 22:04
 */
public class ConsumerDemo {
    public static void main(String[] args) {
        /*
         * producer生产的数据在kafka中会持久化,不用担心数据丢失问题
         * consumer在消费过程中可能出现宕机等故障,恢复后要从故障前的位置继续消费,所以consumer需要实时提交offset
         *
         * 消费数据和提交offset必须是原子性的,消费完了才能提交offset,没消费完就得回滚,这样才能保证exactly once精准消费
         * 所以不管是自动提交还是手动提交offset都不能保证消息的精准消费,因为消费数据和提交offset这两件事不在同一个事务中
         * 实际场景中消费者通常是SparkStreaming这样的实时处理系统,可以将offset提交到redis
         *
         * 消费者提交的offset是消费的最后一个offset + 1
         * 先消费后提交：如果消费完之后提交之前consumer挂了,没提交成功,恢复之后会从旧的offset开始消费,导致重复消费(at least once)
         * 先提交后消费：如果提交之后消费完之前consumer挂了,没消费成功,恢复之后会从新的offset开始消费,导致数据丢失(at most once)
         */

        // 消费者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put("bootstrap.servers", "localhost:9092");  // kafka集群地址
        prop.put("key.deserializer", StringDeserializer.class.getName());  // key的反序列化器
        prop.put("value.deserializer", StringDeserializer.class.getName());  // value的反序列化器
        prop.put("group.id", "g01");  // 消费者组
        // 可选参数
        prop.put("enable.auto.commit", "false");  // true先提交后消费,false先消费后提交
        prop.put("auto.offset.reset", "earliest");  // 没有offset时从哪里开始消费,earliest/latest/none

        // 创建消费者对象
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(prop);
        // 订阅topic集合
        List<String> list = new ArrayList<>();
        list.add("t01");
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
