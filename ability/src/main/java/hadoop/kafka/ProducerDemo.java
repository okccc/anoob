package hadoop.kafka;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * @author okccc
 * @version 1.0
 * @date 2020/11/29 18:22
 */
public class ProducerDemo {
    public static void main(String[] args) {
        /*
         * kafka生产者采用异步发送方式
         */

        // 生产者属性配置
        Properties prop = new Properties();
        // 必须指定参数
        prop.put("bootstrap.servers", "cdh1:9092");  // kafka集群地址
        prop.put("key.serializer", StringSerializer.class.getName());  // key的序列化器
        prop.put("value.serializer", StringSerializer.class.getName());  // value的序列化器
        // 可选参数
        prop.put("acks", "all");  // ack可靠性级别 0/1/-1(all)
        prop.put("retries", 1);  // 重试次数
        prop.put("batch.size", 1024*16);  // 批次大小
        prop.put("linger.ms", 10);  // 等待时间
        prop.put("buffer.memory", 1024*1024*16);  // 缓冲区大小

        // 创建生产者对象
        KafkaProducer<String, String> producer = new KafkaProducer<>(prop);

        // 往kafka发送数据,topic中的数据全局无序,分区内部有序
        for (int i = 0; i < 1000; i++) {
            // 将每条数据都封装成ProducerRecord对象发送,并且可以添加回调函数,在producer收到ack时调用
            producer.send(new ProducerRecord<>("t01", i + "", "message-" + i), new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    // 没有异常说明发送成功
                    if (exception==null) {
                        System.out.println("send message success: " + metadata);
                    } else {
                        // 有异常就打印日志
                        exception.printStackTrace();
                    }
                }
            });
        }

        // 关闭生产者
        producer.close();
    }
}
