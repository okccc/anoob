package com.okccc.func;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.nio.charset.StandardCharsets;

/**
 * @Author: okccc
 * @Date: 2024/5/8 17:52:01
 * @Desc: 自定义类实现KafkaRecordDeserializationSchema接口,反序列化ConsumerRecord对象,参考JSONKeyValueDeserializationSchema
 */
public class MyKafkaRecordDeserializationSchema implements KafkaRecordDeserializationSchema<String> {

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<String> out) {
        if (record.value() != null && record.value().length > 0) {
            String value = new String(record.value(), StandardCharsets.UTF_8);
            JSONObject jsonObject = JSON.parseObject(value);
            // 将kafka消息的元数据整合到value中,partition和offset很有用,方便下游比对数据
            jsonObject.put("partition", record.partition());
            jsonObject.put("offset", record.offset());
            out.collect(jsonObject.toJSONString());
        }
    }

    @Override
    public TypeInformation<String> getProducedType() {
        return BasicTypeInfo.STRING_TYPE_INFO;
    }
}
