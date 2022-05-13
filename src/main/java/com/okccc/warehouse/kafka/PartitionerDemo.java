package com.okccc.warehouse.kafka;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;

import java.util.Map;

/**
 * Author: okccc
 * Date: 2022/5/11 3:44 下午
 * Desc: 自定义kafka分区器
 */
public class PartitionerDemo implements Partitioner {
    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        // 获取数据
        String msg = value.toString();
        // 分区编号
        int partitionNum;
        // 自定义分区规则,生产环境中可以过滤脏数据
        if (msg.contains("hello")) {
            partitionNum = 0;
        } else {
            partitionNum = 1;
        }
        return partitionNum;
    }

    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> configs) {

    }
}
