package bigdata.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Author: okccc
 * Date: 2020/11/29 18:22
 * Desc: 模拟kafka生产者
 */
public class ProducerDemo {
    public static void main(String[] args) throws InterruptedException {
        /*
         * 消息队列
         * java提供的Queue是基于内存的单机版队列,MQ通常是分布式队列并且数据可以持久化,当然系统设计会更复杂
         * java提供的HashMap也是基于内存的单机版,可以使用redis存储键值对数据,分布式存储并且数据可以持久化
         *
         * 应用场景
         * 异步：页面注册 - 写数据库 - 调用发短信接口(将请求写入MQ,短信接口作为消费者会轮询MQ处理请求) - 响应用户
         * 解耦：A系统生产数据并调用接口发送到BCD系统,随着业务发展C下线了D故障了E进来了A得忙死,将数据写入MQ需要的自取
         * 缓冲：秒杀活动瞬间请求5K/s系统只能处理2K/秒,将请求写入MQ,系统按照消费能力pull数据,高峰期后请求50个/秒,系统很快就处理完积压的消息
         *
         * 两种模式
         * 点对点模式(一对一)：一个消息只能被一个消费者消费,消费完就从queue移除
         * 发布-订阅模式(一对多)：kafka生产者发布消息到topic,消费者订阅该消息,一个消息可以被多个消费者消费,且不管是否消费都会保留7天
         *
         * 消费方式
         * push模式是消费者被动接受发送过来的数据,难以适应消费速率不同的消费者,消费者来不及处理可能会导致网络拥堵甚至程序崩溃
         * pull模式是消费者根据自身消费能力主动去broker拉数据,缺点是broker没有数据时会陷入空循环,需要指定超时参数timeout
         *
         * 生产者数据可靠性
         * 为了保证生产者发送数据的可靠性,topic的每个partition收到数据后都要向生产者发送ack确认,生产者收到ack才会发送下一轮数据,否则重新发送
         * 1).副本同步策略
         * leader何时向生产者发送ack呢?
         * 方案1：半数follower同步完成就发送ack,优点是延迟低,缺点是选举新leader容忍n个节点的故障需要2n+1个副本会造成大量数据冗余
         * 方案2：所有follower同步完成才发送ack,优点是选举新leader容忍n个节点的故障只需要n+1个副本,缺点是有的节点同步速度慢导致延迟高
         * kafka采用的是第二种,因为网络延迟对kafka的影响相较于数据冗余要小很多
         * 2).Isr
         * 如果有一个follower故障导致迟迟不能与leader同步,也要一直等它同步结束才发送ack吗?
         * leader维护了一个动态的Isr(in-sync replica),存放和leader保持同步的follower集合,只要Isr同步完成leader就发送ack
         * 如果follower长时间不同步数据就会被Isr剔除,通过replica.lag.time.max.ms参数设定,当leader故障时会从Isr中选举新的leader
         * 3).ack可靠性级别
         * 有些数据对可靠性要求不高允许有少量丢失,kafka提供了3种可靠性级别,用户可以针对可靠性和延迟性权衡
         * ack=0 leader接收到数据还没落盘就返回ack,如果leader故障必然会丢数据
         * ack=1 leader落盘后返回ack,如果在follower同步完成前leader故障也会丢数据
         * ack=-1(all) leader和follower全部落盘才返回ack,如果在follower同步完成后发送ack前leader故障,生产者收不到ack会重发导致数据重复
         * 4).故障恢复
         * LEO(log end offset)每个副本的最后一个offset | HW(high watermark)所有副本中最小的LEO
         * LEO和HW只能保证数据的一致性,要么都丢数据要么都数据重复,数据不丢失不重复是由ack保证的
         * leader故障会从Isr中选举新leader,为了保证副本数据一致性,其余follower会将log文件高于HW的部分截掉,然后从新的leader同步数据
         * follower故障会被临时踢出Isr,恢复后读取本地磁盘记录的HW,并将log文件高于HW的部分截掉,然后同步数据,等到追上leader时会重新加入Isr
         * 5).精准发送
         * at most once 可能会丢数据 | at least once 可能数据重复 | exactly once 精准发送,保证每条消息都会发送且只发送一次
         * kafka0.11版本引入了幂等性机制(保证数据唯一性) enable.idempotence=true, at least once + idempotent = exactly once
         *
         * 生产者分区策略
         * a.指定partition
         * b.没有指定partition但是有key,将key的hash值与partition数进行取余决定往哪个partition写数据
         * c.没有指定partition也没有key,将递增的随机数与partition数进行取余决定往哪个partition写数据,这就是round-robin轮询算法
         *
         * controller和zk
         * kafka集群启动时会向zk注册节点信息,最先注册的broker节点就是controller,关闭kafka时zk的/controller和/brokers/ids会清空
         * controller会监控zk的节点变化情况,负责管理broker节点上下线/leader选举/topic分区和副本分配,zk辅助controller进行管理工作
         *
         * kafka高效读写数据
         * 1.顺序写磁盘：生产者往partition写数据是按顺序追加到log文件末尾,省去了大量的磁头寻址时间,写一个大文件比写多个小文件速度快得多
         * 2.零拷贝技术：计算机在网络中发送文件时直接在内核空间Kernel Space传输到网络,不用拷贝到用户空间User Space,省去在内存中的拷贝
         *
         * kafka出问题先看进程,再查日志
         * kafka关闭有延迟,如果zk先停了,/brokers/ids下的节点还在,此时kafka还存活但与zk失去连接导致无法停止,只能手动杀掉进程
         * kafka故障重启可能会导致kafka的logs/meta.properties的cluster.id不一致,把这个干掉,kafka重启之后会重新生成该文件
         */

        // 1.生产者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put("bootstrap.servers", "localhost:9092");                 // kafka集群地址
        prop.put("key.serializer", StringSerializer.class.getName());    // key的序列化器
        prop.put("value.serializer", StringSerializer.class.getName());  // value的序列化器
        // 可选参数
        prop.put("acks", "all");                  // ack可靠性级别 0/1/-1(all)
        prop.put("enable.idempotence", true);     // 开启幂等性机制,配合ack=-1确保生产者exactly once
        prop.put("retries", 1);                   // 重试次数
        prop.put("batch.size", 1024*16);          // 批次大小
        prop.put("linger.ms", 10);                // 等待时间
        prop.put("buffer.memory", 1024*1024*16);  // 缓冲区大小
        // 添加拦截器集合(可选)
        List<String> interceptors = new ArrayList<>();
        interceptors.add("bigdata.kafka.InterceptorDemo");
        prop.put("interceptor.classes", interceptors);

        // 2.创建生产者对象,参数是topicName和eventLog
        KafkaProducer<String, String> producer = new KafkaProducer<>(prop);

        // 3.往kafka发送数据
        while (true) {
            // 随机生成一条用户日志
            String eventLog = getEventLog();
            // 将消息封装成ProducerRecord对象发送,并且可以添加回调函数,在producer收到ack时调用
            producer.send(new ProducerRecord<>("nginx", eventLog));
            // 设置发送间隔
            Thread.sleep(100);
        }
    }

    public static String getEventLog() {
        // 创建字符串缓冲区
        StringBuilder sb = new StringBuilder();
        // 生成时间
        String time = DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss"));
        // 随机生成用户ID
        String uid = "u-" + new Random().nextInt(1000);
        // 随机生成行为
        String[] actions = {"点赞", "评论", "收藏", "喜欢", "关注", "投币"};
        String action = actions[new Random().nextInt(6)];
        // 往字符串缓冲区添加内容,拼接成一条日志
        sb.append(time).append("\t").append(uid).append("\t").append(action);
        // 返回这条日志
        return sb.toString();
    }
}