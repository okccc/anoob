package com.okccc.kafka;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;

import java.util.List;
import java.util.Map;

/**
 * @Author: okccc
 * @Date: 2022/5/11 3:44 下午
 * @Desc: 自定义生产者分区器实现Partitioner接口,参照RoundRobinPartitioner
 */
public class PartitionerDemo implements Partitioner {
    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        // 获取topic的分区数
        List<PartitionInfo> partitionInfos = cluster.partitionsForTopic(topic);
        int numPartitions = partitionInfos.size();

        // 分区策略：指定key(通常是uid),将key的hash值与partition数取余,保证同一个uid的数据进入相同分区
        JSONObject jsonObject = JSON.parseObject(value.toString());
        String uid = jsonObject.getString(key.toString());
        return (uid.hashCode() % numPartitions);
    }

    @Override
    public void close() {}

    @Override
    public void configure(Map<String, ?> configs) {}
}
