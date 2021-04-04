package bigdata.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
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
         * 方案2：at least once + 手动实现幂等性 = exactly once 一般有主键的数据库都支持幂等性操作,适用于大量明细数据
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
        prop.put("bootstrap.servers", "10.18.3.21:9092,10.18.3.22:9092,10.18.3.23:9092");  // 测试kafka
        prop.put("key.deserializer", StringDeserializer.class.getName());    // key的反序列化器
        prop.put("value.deserializer", StringDeserializer.class.getName());  // value的反序列化器
        prop.put("group.id", "gg");                                          // 消费者组
        // 可选参数
//        prop.put("enable.auto.commit", "false");  // true自动提交(默认),false手动提交
//        prop.put("auto.offset.reset", "latest");  // 没有offset就从latest(默认)/earliest/none开始消费

        // 2.创建消费者对象,参数是topicName和eventLog
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(prop);
        // 订阅topic集合
        List<String> list = new ArrayList<>();
//        list.add("amplitude02");
//        list.add("eduplatform0");
        list.add("eduplatform-fat");
        consumer.subscribe(list);

        // 3.从kafka拉取数据
        while (true) {
            // 每隔10秒拉取一批数据
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            // 消息被封装成ConsumerRecord对象
            for (ConsumerRecord<String, String> record : records) {
                // 获取每条消息的元数据信息
                System.out.println("topic=" + record.topic() + ", partition=" + record.partition() + ", offset="
                        + record.offset() + ", value=" + record.value());
            }
            // 提交offset
            consumer.commitAsync();
        }
    }
}
