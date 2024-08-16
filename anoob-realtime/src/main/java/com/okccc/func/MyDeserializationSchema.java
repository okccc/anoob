package com.okccc.func;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.nio.charset.StandardCharsets;

/**
 * @Author: okccc
 * @Date: 2024/5/8 17:50:56
 * @Desc: 自定义类实现DeserializationSchema接口,只反序列化ConsumerRecord对象的value部分,参考SimpleStringSchema
 */
public class MyDeserializationSchema implements DeserializationSchema<String> {

    @Override
    public String deserialize(byte[] message) {
        // flink sql的left join会生成null值,如果直接用SimpleStringSchema会报空指针异常
        if (message != null && message.length > 0) {
            return new String(message, StandardCharsets.UTF_8);
        }
        // 这里返回null不会报错,下游消费者过滤即可
        return null;
    }

    @Override
    public boolean isEndOfStream(String nextElement) {
        return false;  // kafka是无界流
    }

    @Override
    public TypeInformation<String> getProducedType() {
        return BasicTypeInfo.STRING_TYPE_INFO;
    }
}
