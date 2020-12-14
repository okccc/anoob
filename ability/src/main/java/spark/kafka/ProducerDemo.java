package spark.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.ArrayList;
import java.util.List;
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
         * producer发送数据过程包含main线程,线程共享变量RecordAccumulator以及sender线程
         * 用户只需要实现main线程逻辑其它的都是kafka实现,main线程调用send方法之后会依次经过拦截器、序列化器和分区器
         * 拦截器可以使producer在发送消息之后以及回调逻辑之前对消息做一些定制化需求,可以是单个拦截器也可以是多个拦截器链
         */

        // 生产者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put("bootstrap.servers", "localhost:9092");  // kafka集群地址
        prop.put("key.serializer", StringSerializer.class.getName());  // key的序列化器
        prop.put("value.serializer", StringSerializer.class.getName());  // value的序列化器
        // 可选参数
        prop.put("acks", "all");  // ack可靠性级别 0/1/-1(all)
        prop.put("retries", 1);  // 重试次数
        prop.put("batch.size", 1024*16);  // 批次大小
        prop.put("linger.ms", 10);  // 等待时间
        prop.put("buffer.memory", 1024*1024*16);  // 缓冲区大小

        // 添加拦截器集合
        List<String> interceptors = new ArrayList<>();
        interceptors.add("spark.kafka.InterceptorDemo");
        prop.put("interceptor.classes", interceptors);

        // 创建生产者对象
        KafkaProducer<String, String> producer = new KafkaProducer<>(prop);

        // 往kafka发送数据,topic中的数据全局无序,分区内部有序
        for (int i = 0; i < 1000; i++) {
            // 将数据封装成ProducerRecord对象发送,并且可以添加回调函数,在producer收到ack时调用
            producer.send(new ProducerRecord<>("topic_start", i + "", "message-" + i), new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    // 没有异常说明发送成功
                    if (exception==null) {
                        // 获取发送消息的元数据信息
                        System.out.println("topic=" + metadata.topic() +
                                ",partition=" + metadata.partition() +
                                ",offset=" + metadata.offset() +
                                ",key=" + metadata.serializedKeySize() +
                                ",value=" + metadata.serializedValueSize());
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
