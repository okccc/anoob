package com.okccc.kafka;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @Author: okccc
 * @Date: 2020/11/29 18:22
 * @Desc: 模拟kafka生产者,实际场景一般是flume或者canal
 * kafka所有配置参数在官方文档都有具体介绍,源码和文档结合使用 https://kafka.apache.org/documentation
 *
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
 * topic分区好处,大数据场景主要考虑存储和计算两个方面
 * 存储：将大量数据按照分区切割存储在多个broker达到负载均衡
 * 计算：数据分区可以提高生产者(4个参数)和消费者(2个参数)的吞吐量
 *
 * 生产者分区策略,全局搜索DefaultPartitioner类查看源码注释
 * a.指定partition
 * b.没有指定partition但是有key(user_id/order_info),将key的hash值与partition数取余决定写往哪个partition(很有用)
 * c.没有指定partition也没有key,采用StickyPartition粘性分区器,先随机选择一个分区一直写,等该分区batch已满再换新的分区
 *
 * 生产者数据可靠性
 * kafka收到数据后要向生产者发送ack确认,生产者收到ack才会发送下一轮数据,没收到就重新发送,针对可靠性和延迟性分为3种级别
 * ack=0 leader接收到数据还没落盘就返回ack,如果leader故障必然会丢数据
 * ack=1 leader落盘后返回ack,如果在follower同步完成前leader故障也会丢数据
 * ack=-1 leader和follower全部落盘才返回ack,如果在follower同步完成后发送ack前leader故障,生产者收不到ack会重发导致数据重复
 * 如果某个follower故障导致迟迟不能与leader同步,也要一直等它同步结束才发送ack吗?
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
 *
 * controller和zk
 * kafka集群启动时会向zk注册节点信息,最先注册的broker节点就是controller,关闭kafka时zk的/controller和/brokers/ids会清空
 * controller会监控zk的节点变化情况,负责管理broker节点上下线/leader选举/topic分区和副本分配,zk辅助controller进行管理工作
 *
 * broker故障恢复
 * LEO(log end offset)每个副本的最后一个offset | HW(high watermark)所有副本中最小的LEO
 * LEO和HW只能保证数据的一致性,要么都丢数据要么都数据重复,数据不丢失不重复是由ack和幂等性保证的
 * leader故障会从Isr中选举新leader,为了保证副本数据一致性,其余follower会将log文件高于HW的部分截掉,然后从新的leader同步数据
 * follower故障会被临时踢出Isr,恢复后读取本地磁盘记录的HW,并将log文件高于HW的部分截掉,然后同步数据直到追上leader再重新加入Isr
 *
 * kafka高效读写数据
 * 1.将数据分区提高并行度高
 * 2.文件存储采用稀疏索引,读数据时可以快速定位要消费的数据
 * 3.顺序写磁盘：生产者将数据按顺序追加到log文件末尾,省去了大量的磁头寻址时间,写一个大文件比写多个小文件速度快得多
 * 4.零拷贝和页缓存：kafka数据加工都由生产者和消费者处理,broker应用层不关心存储的数据,传输时就不用走应用层提高效率
 *
 * kafka出问题先看进程,再查日志
 * kafka关闭有延迟,如果zk先停了,/brokers/ids下的节点还在,此时kafka还存活但与zk失去连接导致无法停止,只能手动杀掉进程
 * kafka故障重启可能会导致kafka的logs/meta.properties的cluster.id不一致,把这个干掉,kafka重启之后会重新生成该文件
 */
public class ProducerDemo {

    public static void main(String[] args) throws IOException {
        // 1.生产者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");  // kafka地址
        prop.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());    // key序列化器
        prop.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());  // value序列化器
        // 生产者吞吐量
        prop.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1024 * 1024 * 32);  // 缓冲区大小,默认32m
        prop.put(ProducerConfig.BATCH_SIZE_CONFIG, 1024 * 16);          // 批次大小,默认16k
        prop.put(ProducerConfig.LINGER_MS_CONFIG, 10);                // 等待时间,默认0
        prop.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");   // 压缩类型,默认none
        // 生产者可靠性
        prop.put(ProducerConfig.ACKS_CONFIG, "all");                  // ack可靠性级别,0基本不用、1普通日志、-1(all)涉及钱的
        prop.put(ProducerConfig.RETRIES_CONFIG, 1);                   // 重试次数
        prop.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);     // 开启幂等性
//        prop.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "tid");      // 开启事务,速度会变慢

        // 添加拦截器集合(可选)
        List<String> interceptors = new ArrayList<>();
        interceptors.add("com.okccc.kafka.InterceptorDemo");
        prop.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, interceptors);
        // 添加分区器(可选)
//        prop.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, "com.okccc.kafka.PartitionerDemo");

        // 2.创建生产者对象,<String, String>是topics和record
        KafkaProducer<String, String> producer = new KafkaProducer<>(prop);
        // 初始化事务
//        producer.initTransactions();

        // 3.往kafka发送数据
        String topic = "nginx";
        BufferedReader br = new BufferedReader(new FileReader("flink/input/ApacheLog.csv"));
        String line;
        while ((line = br.readLine()) != null) {
            // 开启事务
//            producer.beginTransaction();
            try {
                // 将消息封装成ProducerRecord发送,可以指定topic/partition(N)/key(N)/value,还能添加回调函数在生产者收到ack时调用
                // 同步发送：生产者将外部数据往缓存队列中放一批,必须等这批数据发送到kafka集群再继续放下一批(不常用)
//                    producer.send(new ProducerRecord<>(topic, line)).get();
                // 异步发送：生产者将外部数据一批一批往缓存队列中放,不管这批数据有没有发送到kafka集群
                producer.send(new ProducerRecord<>(topic, line), new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata metadata, Exception exception) {
                        System.out.println("topic=" + metadata.topic() + ", partition=" + metadata.partition() + ", offset=" + metadata.offset());
                    }
                });
                // 提交事务
//                producer.commitTransaction();
//                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
                // 有异常就终止事务
//                producer.abortTransaction();
            }
        }
    }
}