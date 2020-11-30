package hadoop.kafka;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.Map;

/**
 * @author okccc
 * @version 1.0
 * @date 2020/11/30 22:17
 */
public class InterceptorDemo implements ProducerInterceptor<String, String> {
    private int successNum = 0;
    private int errorNum = 0;

    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        // 在record的value前面加上时间戳
        ProducerRecord<String, String> record_new = new ProducerRecord<>(record.topic(), record.partition(), record.timestamp(), record.key(),
                DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")) + "   " + record.value());
        return record_new;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // 统计发送消息成功和失败的数量
        if (exception==null) {
            successNum += 1;
        } else {
            errorNum += 1;
        }
    }

    @Override
    public void close() {
        // 输出统计结果
        System.out.println("successNum=" + successNum);
        System.out.println("errorNum=" + errorNum);
    }

    @Override
    public void configure(Map<String, ?> configs) {

    }
}
