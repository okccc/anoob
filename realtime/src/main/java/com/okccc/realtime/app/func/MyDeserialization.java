package com.okccc.realtime.app.func;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.ververica.cdc.debezium.DebeziumDeserializationSchema;
import io.debezium.data.Envelope;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.util.Collector;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;

/**
 * Author: okccc
 * Date: 2021/10/18 下午3:23
 * Desc: 自定义反序列化器,封装返回的数据格式,方便解析
 */
public class MyDeserialization implements DebeziumDeserializationSchema<String> {
    @Override
    public void deserialize(SourceRecord sourceRecord, Collector<String> collector) {
        Struct valueStruct = (Struct) sourceRecord.value();
        Struct sourceStrut = valueStruct.getStruct("source");
        // 获取数据库
        String database = sourceStrut.getString("db");
        // 获取表
        String table = sourceStrut.getString("table");
        // 获取类型
        String type = Envelope.operationFor(sourceRecord).toString().toLowerCase();
        if ("create".equals(type)) {
            type = "insert";
        }

        // 封装成JSON对象
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("database", database);
        jsonObj.put("table", table);
        jsonObj.put("type", type);

        // 获取影响的数据data
        // 源格式：id=1, name=aaa, age=17
        // 目标格式：{"id": 74603, "order_id": 28641, "order_status": "1005", "operate_time": "2021-07-30 11:35:49"}}
        Struct afterStruct = valueStruct.getStruct("after");
        JSONObject dataJsonObj = new JSONObject();
        if (afterStruct != null){
            for (Field field : afterStruct.schema().fields()) {
                String fieldName = field.name();
                Object fieldValue = afterStruct.get(field);
                dataJsonObj.put(fieldName, fieldValue);
            }
        }
        jsonObj.put("data", dataJsonObj);

        // 向下游传递数据
        collector.collect(jsonObj.toJSONString());
    }

    @Override
    public TypeInformation<String> getProducedType() {
        return TypeInformation.of(String.class);
    }
}
