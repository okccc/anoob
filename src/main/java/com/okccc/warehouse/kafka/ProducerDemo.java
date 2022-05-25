package com.okccc.warehouse.kafka;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Author: okccc
 * Date: 2020/11/29 18:22
 * Desc: 模拟kafka生产者,实际场景一般是flume或者canal
 * kafka所有配置参数在官方文档都有具体介绍,源码和文档结合使用 https://kafka.apache.org/documentation
 */
public class ProducerDemo {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        /*
         * 消息队列
         * java提供的Queue是基于内存的单机版队列,MQ通常是分布式队列并且数据可以持久化,当然系统设计会更复杂
         * java提供的HashMap也是基于内存的单机版,可以使用redis存储键值对数据,分布式存储并且数据可以持久化
         *
         * 应用场景
         * 异步：页面注册 - 写数据库 - 调用发短信接口(将请求写入MQ,短信接口作为消费者会轮询MQ处理请求) - 响应用户
         * 解耦：A系统生产数据并调用接口发送到BCD系统,随着业务发展C下线了D故障了E进来了A得忙死,将数据写入MQ需要的自取
         * 缓冲：秒杀活动请求5000/s系统只能处理1000/s,将请求写入MQ,系统按照消费能力pull数据,高峰期后请求50/s,系统会很快处理完积压消息
         *
         * 两种模式
         * 点对点模式(一对一)：一个消息只能被一个消费者消费,消费完就从queue移除
         * 发布-订阅模式(一对多)：kafka生产者发布消息到topic,消费者订阅该消息,一个消息可以被多个消费者消费,且不管是否消费都会保留7天
         *
         * 消费方式
         * push模式是消费者被动接受发送过来的数据,难以适应消费速率不同的消费者,消费者来不及处理可能会导致网络拥堵甚至程序崩溃
         * pull模式是消费者根据自身消费能力主动去broker拉数据,缺点是broker没有数据时会陷入空循环,需要指定超时参数timeout
         *
         * topic分区好处,大数据场景主要考虑存储和计算两个方面
         * 存储：将大量数据按照partition切割存储在多个broker达到负载均衡
         * 计算：将数据以partition为单位划分可以提高producer和consumer的并行度
         *
         * 生产者分区策略,全局搜索DefaultPartitioner类查看源码注释
         * a.指定partition
         * b.没有指定partition但是有key(user_id/order_info),将key的hash值与partition数取余决定写往哪个partition(很有用)
         * c.没有指定partition也没有key,采用StickyPartition粘性分区器,先随机选择一个分区一直写,等该分区batch已满再换新的分区
         *
         * 生产者数据可靠性
         * 1.ack可靠性级别
         * kafka收到数据后要向生产者发送ack确认,生产者收到ack才会发送下一轮数据,没收到就重新发送,针对可靠性和延迟性分为3种级别
         * ack=0 leader接收到数据还没落盘就返回ack,如果leader故障必然会丢数据
         * ack=1 leader落盘后返回ack,如果在follower同步完成前leader故障也会丢数据
         * ack=-1 leader和follower全部落盘才返回ack,如果在follower同步完成后发送ack前leader故障,生产者收不到ack会重发导致数据重复
         * 2.ISR
         * ack=-1时,如果某个follower故障导致迟迟不能与leader同步,也要一直等它同步结束才发送ack吗?
         * leader维护了一个动态副本同步队列Isr(in-sync replica),存放和leader保持同步的follower集合,只要Isr同步完成leader就发送ack
         * 如果follower长时间不同步数据就会被Isr剔除,可通过replica.lag.time.max.ms参数设定,默认30s,当leader故障时会从Isr中选举新的
         *
         * 数据重复
         * at most once 可能会丢数据(UDP) | at least once 可能数据重复 | exactly once 精准发送,保证每条消息都会发送且只发送一次
         * kafka0.11版本引入了幂等性机制,生产者不管发送多少次数据broker只会持久化一条 at least once + idempotent = exactly once
         * 重复数据判断标准是主键<pid,partition,seqNum>,pid是每次kafka重启会分配一个新的,partition是分区号,seqNum单调自增
         * 所以幂等性只能保证单分区单会话内数据不重复,完全不重复还得在幂等性的基础上开启事务
         *
         * 数据乱序(重点)
         * kafka1.x后的版本可以保证数据单分区有序,设置参数max.in.flight.requests.per.connection=1(未开启幂等性)/<=5(开启幂等性)
         * 多分区有序的话只能consumer收到数据后自己排序了,但是很影响性能还不如只弄一个分区,spark和flink的窗口可以实现该功能
         */

        // 1.生产者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");  // kafka集群地址
        prop.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());    // key序列化器
        prop.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());  // value序列化器
        // 生产者吞吐量
        prop.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1024*1024*32);  // 缓冲区大小,默认32m
        prop.put(ProducerConfig.BATCH_SIZE_CONFIG, 1024*16);          // 批次大小,默认16k
        prop.put(ProducerConfig.LINGER_MS_CONFIG, 10);                // 等待时间,默认0
        prop.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");   // 压缩类型,默认none
        // 生产者可靠性
        prop.put(ProducerConfig.ACKS_CONFIG, "all");                  // ack可靠性级别,0基本不用、1普通日志、-1(all)涉及钱的
        prop.put(ProducerConfig.RETRIES_CONFIG, 1);                   // 重试次数
        prop.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);     // 开启幂等性
        prop.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "tid");      // 开启事务
    }
}