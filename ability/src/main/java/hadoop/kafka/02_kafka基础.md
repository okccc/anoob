### install
```shell script
# 修改配置文件
[root@cdh1=~]# vim server.properties
############################# Server Basics #############################
# broker的全局唯一编号,不能重复
broker.id=0
# 开启删除topic功能,否则只是标记删除并没有真正删除
delete.topic.enable=true
############################# Socket Server Settings #############################
# 处理网络请求的线程数
num.network.threads=3
# 处理磁盘io的线程数
num.io.threads=8
# 发送套接字的缓冲区大小
socket.send.buffer.bytes=102400
# 接收套接字的缓冲区大小
socket.receive.buffer.bytes=102400
# 请求套接字的最大值
socket.request.max.bytes=104857600
############################# Log Basics #############################
# kafka运行日志存放路径
log.dirs=/opt/module/kafka/logs
# topic在当前broker上的分区个数
num.partitions=1
# 恢复和清理data下数据的线程数
num.recovery.threads.per.data.dir=1
############################# Internal Topic Settings  #############################
# The replication factor for the group metadata internal topics "__consumer_offsets" and "__transaction_state"
# For anything other than development testing, a value greater than 1 is recommended for to ensure availability such as 3.
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
############################# Log Retention Policy #############################
# log文件保留时长
log.retention.hours=168
# segment文件最大值
log.segment.bytes=1073741824
############################# Zookeeper #############################
# zk地址
zookeeper.connect=cdh1:2181,cdh2:2181,cdh3:2181
# zk连接超时时间(毫秒)
zookeeper.connection.timeout.ms=6000
############################# Group Coordinator Settings #############################
group.initial.rebalance.delay.ms=0

# 添加到环境变量
[root@cdh1 ~]# vim /etc/profile && source /etc/profile
export KAFKA_HOME=/opt/module/kafka
export PATH=$PATH:$KAFKA_HOME/bin

# 分发到其他节点
scp -r kafka cdh2:/opt/module/kafka && broker.id=1
scp -r kafka cdh3:/opt/module/kafka && broker.id=2
```

### kafka
```shell script
producer
# 生产者：往partition写数据,分区方便kafka横向扩展,提高并发和吞吐量,这样集群就可以适应任意大小的数据量
consumer
# 消费者：可以消费同一个broker上的多个partition,依赖controller和zk在消费者端做负载均衡
consumer-group
# 消费者组：逻辑上的订阅者,组内每个consumer对应topic的一个partition,且partition只能被组内的一个consumer消费,但是消费者组之间互不干扰
broker
# 节点：存储topic并负责消息的读写,一个broker可以容纳多个topic,最先注册到zk的broker会被选举为controller
topic
# 消息队列/消息分类：topic是逻辑的partition是物理的,一个topic分成多个partition,分区可以让消费者并行处理,分区内部消息有序先进先出全局无序
partition
# 分区：每个partition对应一个log文件,生产者生产的消息会不断追加到文件末尾,且每条消息都有offset,保存在kafka的内置topic __consumer_offsets
replication
# 副本：为了保证高可用性,每个partition都有副本,leader负责工作,follower负责同步数据,当leader故障时Isr中的某个follower会被选举为新的leader
segment
# 片段：为了防止log文件过大难以定位数据,将其分为多个segment,包含.index(存储索引)和.log(存储数据),文件以当前segment第一条消息的offset命名
offset
# 消息的偏移量：如果以消费者为单位维护,消费者挂掉offset就丢失了,当partition或consumer数量发生变化时,会触发kafka的rebalance机制重新分配分区,这对消费者组并没有影响,所以offset是以消费者组为单位维护

# 启动kafka,默认是前台进程,可以在后台启动
[root@cdh1 ~]$ bin/kafka-server-start.sh -daemon /config/server.properties | nohup bin/kafka-server-start.sh config/server.properties > (logs/kafka.log | /dev/null) 2>&1 &
# 查找kafka进程
[root@cdh1 bin]$ ps -aux | grep -i 'kafka' | grep -v grep | awk '{print $2}'
# 创建topic,指定分区数和副本数
[root@cdh1 ~]$ bin/kafka-topics.sh --zookeeper cdh1:2181 --create --topic t01 --partitions 3 --replication-factor 2
Created topic "t01".
# 查看topic列表/详细信息
[root@cdh1 ~]$ bin/kafka-topics.sh --zookeeper cdh1:2181,cdh2:2181,cdh3:2181 --list
[root@cdh1 ~]$ bin/kafka-topics.sh --zookeeper cdh1:2181,cdh2:2181,cdh3:2181 --describe --topic t01
Topic:t01       PartitionCount:3        ReplicationFactor:2     Configs:
    Topic: t01      Partition: 0    Leader: 2    Replicas: 2,1    Isr: 2,1  # 0/1/2表示broker.id
    Topic: t01      Partition: 1    Leader: 0    Replicas: 0,2    Isr: 0,2
    Topic: t01      Partition: 2    Leader: 1    Replicas: 1,0    Isr: 1,0
# 修改topic分区数,只能增加不能减少,因为partition可能已经有数据
[root@cdh1 ~]$ bin/kafka-topics.sh --zookeeper cdh1:2181 --alter --topic t01 --partitions 2
WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of the messages will be affected
Adding partitions succeeded!
# 删除topic
[root@cdh1 ~]$ bin/kafka-topics.sh --zookeeper cdh1:2181 --delete --topic t01
Topic t01 is marked for deletion.
Note: This will have no impact if delete.topic.enable is not set to true.
# 在zk中删除
[zk: localhost:2181(CONNECTED) 0] rmr /brokers/topics/t01 & rmr /admin/delete_topics/t01
# 生产者
[root@cdh1 ~]$ bin/kafka-console-producer.sh --broker-list cdh1:9092,cdh2:9092,cdh3:9092 --topic t01
>java hadoop
# 消费者,--from-beginning表示读取主题中以往所有数据
[root@cdh1 ~]$ bin/kafka-console-consumer.sh --bootstrap-server cdh1:9092 [--from-beginning] --topic t01
java hadoop
# 查看consumer-group列表/详细信息
[root@cdh1 ~]$ bin/kafka-consumer-groups.sh --bootstrap-server cdh1:9092,cdh2:9092,cdh3:9092 --list
[root@cdh1 ~]$ bin/kafka-consumer-groups.sh --bootstrap-server cdh1:9092,cdh2:9092,cdh3:9092 --describe --group g01
# 重置消费者组的offset
[root@cdh1 ~]$ bin/kafka-consumer-groups.sh --bootstrap-server cdh1:9092 --group g01 --reset-offsets --all-topics --to-earliest --execute
```

### Q & A
```shell script
# 消息队列和普通队列区别
java提供了各种各样的队列类,但都是程序中基于内存的单机版队列,MQ通常是分布式队列并且数据可以持久化
java提供了HashMap存储key-value数据,但是很多时候还是会用到Redis(可以将数据持久化到磁盘,redis挂了可以从磁盘恢复)
# 消息队列缺点
为了保证消息队列的高可用就得使用分布式,为防止数据丢失还要考虑持久化,这些都会提高系统的复杂性
# 消息队列应用场景
异步：页面注册 - 写数据库 - 调用发短信接口(略耗时,可以将发短信请求写入MQ) - 响应用户(短信接口作为消费者会轮询MQ读取数据发送短信,用户不用等待这个操作消耗的时间)
解耦：A系统生产数据通过接口调用发送到BCD系统,随着业务发展C不要了D挂了E又要了A要忙死...A将数据发送到MQ需要的自己去取,A不用考虑发给谁以及是否调用成功
缓冲：秒杀活动瞬间产生大量请求(5K个/秒)系统处理不完(2K个/秒)导致崩溃,可以将请求写入MQ,系统按照自己消费能力pull数据,高峰期后请求急剧减少(50个/秒),系统很快就会将积压的消息处理掉
# 消息队列两种模式
点对点模式(一对一)：一个消息只能被一个消费者消费,消费完就从queue移除
发布-订阅模式(一对多)：kafka生产者发布消息到topic,消费者订阅该消息,一个消息可以被多个消费者消费,且消费完不会立马清除而是保存一段时间

# 数据可靠性保证
为了保证producer往topic发送数据的可靠性,每个partition收到数据后都要向producer发送ack确认,producer收到ack才会进行下一轮发送,否则重新发送
# 1).副本同步策略
leader何时向producer发送ack呢?
方案1：半数以上follower完成同步就发送ack,优点是延迟低,缺点是选举新的leader时容忍n个节点的故障需要2n+1个副本,会造成大量数据冗余
方案2：所有follower完成同步才发送ack,优点是选举新的leader时容忍n个节点的故障只需要n+1个副本,缺点是延迟高,有的节点同步速度很慢但是也要等它
kafka采用的是第二种,因为网络延迟对kafka的影响相较于数据冗余要小很多
# 2).Isr
既然是所有follower同步完成才会发送ack,如果有一个follower故障导致迟迟不能与leader同步,也要一直等它同步结束才发送ack吗?
leader维护了一个动态的Isr(in-sync replica,是all replica子集),存放的是和leader保持同步的follower集合,Isr全部同步完成后leader就发送ack
如果follower长时间没有向leader同步数据就会被Isr剔除,该时间阈值由replica.lag.time.max.ms参数设定,当leader故障时会从Isr中选举新的leader
# 3).ack应答机制
有些数据对可靠性要求不高允许有少量丢失,kafka提供了3种可靠性级别,用户可以根据可靠性和延迟性进行权衡
ack=0 leader一接收到数据还没写磁盘就返回ack,如果leader故障会丢失数据
ack=1 leader落盘成功后返回ack,如果在follower同步结束前leader故障也会丢失数据
ack=-1(all) leader和follower全部落盘成功后才返回ack,如果在follower同步结束后发送ack前leader故障,producer收不到ack会重发导致数据重复
# 4).故障恢复
LEO(log end offset)每个副本的最后一个offset | HW(high watermark)所有副本中最小的LEO
LEO和HW只能保证数据的一致性,要么都丢数据要么都数据重复,数据不丢失不重复是由ack保证的
leader故障会从Isr中选举新的leader,为保证各个副本数据的一致性,其余follower会先将各自log文件高于HW的部分截掉,然后从新的leader同步数据
follower故障会被临时踢出Isr,恢复后读取本地磁盘记录的之前的HW,并将log文件高于HW的部分截掉,然后同步数据,当该follower追上leader时会重新加入Isr
# 5).精准消费
at most once 可能会丢数据 | at least once 可能重复消费 | exactly once 精准消费,保证每条消息都会被发送且仅发送一次
kafka0.11版本引入了幂等性机制(idempotent),配合ack=-1时的at least once实现producer到broker的exactly once
设置enable.idempotence属性为true,kafka会自动修改ack=-1, idempotent + at least once = exactly once

# 消息丢失和重复消费
消息丢失场景：生产者端ack=0/1,消费者端先提交后消费
重复消费场景：生产者端ack=-1,消费者端先消费后提交 

# 生产者分区策略
a.指定partition
b.没有指定partition但是有key,将key的hash值与partition数进行取余决定往哪个partition写数据
c.没有指定partition也没有key,将递增的随机数与partition数进行取余决定往哪个partition写数据,这就是round-robin轮询算法
# 消费方式
push模式难以适应消费速率不同的消费者,因为发送速率由broker决定,消费者来不及处理会导致网络堵塞甚至程序崩溃
consumer采用pull模式从broker读取数据,根据自身能力消费,缺点是broker没数据时会陷入空循环,需要指定超时参数timeout
# 消费者分区分配策略
partition和consumer之间有两种分区分配策略,round-robin和range
# kafka高效读写数据
1.顺序写磁盘：producer往partition写数据是按照顺序追加到log文件的末端,顺序写速度快是因为省去了大量的磁头寻址时间,比如往一个大文件写比往多个小文件写速度快得多
2.零拷贝技术：计算机在网络中发送文件时直接在内核空间Kernel Space传输到网络,不用拷贝到用户空间User Space,省去在内存中的拷贝
# controller和zk
kafka集群启动时会向zookeeper注册信息,最先注册的broker节点就是controller
controller会监控zookeeper上节点的变化情况,负责管理broker上下线/leader选举/topic的分区副本分配,zk辅助controller进行管理工作
```

### api
```java
public class ProducerDemo {  
    public static void main(String[] args) {  
        // 生产者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put("bootstrap.servers", "cdh1:9092");  // kafka集群地址
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
        interceptors.add("hadoop.kafka.InterceptorDemo");
        prop.put("interceptor.classes", interceptors);

        // 创建生产者对象
        KafkaProducer<String, String> producer = new KafkaProducer<>(prop);

        // 往kafka发送数据,topic中的数据全局无序,分区内部有序
        for (int i = 0; i < 1000; i++) {
            // 将每条数据都封装成ProducerRecord对象发送,并且可以添加回调函数,在producer收到ack时调用
            producer.send(new ProducerRecord<>("t01", i + "", "message-" + i), new Callback() {
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

public class ConsumerDemo {  
    public static void main(String[] args) {  
        // 消费者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put("bootstrap.servers", "cdh1:9092");  // kafka集群地址
        prop.put("key.deserializer", StringDeserializer.class.getName());  // key的反序列化器
        prop.put("value.deserializer", StringDeserializer.class.getName());  // value的反序列化器
        prop.put("group.id", "g01");  // 消费者组
        // 可选参数
        prop.put("enable.auto.commit", "false");  // 是否自动提交offset
        prop.put("auto.offset.reset", "earliest");  // 没有offset时从哪里开始消费,earliest/latest/none

        // 创建消费者对象
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(prop);
        // 订阅topic集合
        List<String> list = new ArrayList<>();
        list.add("t01");
        // 自动提交offset方式
        consumer.subscribe(list);
//        // 手动提交offset方式
//        consumer.subscribe(list, new ConsumerRebalanceListener() {
//            @Override
//            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
//
//            }
//
//            @Override
//            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
//
//            }
//        });

        // 从kafka拉取数据
        while (true) {
            // 拉取一批消息
            ConsumerRecords<String, String> records = consumer.poll(10);
            // 遍历消息记录列表
            for (ConsumerRecord<String, String> record : records) {
                // 获取每条消息的元数据信息
                System.out.println("topic=" + record.topic() +
                        ",partition=" + record.partition() +
                        ",offset=" + record.offset() +
                        ",key=" + record.key() +
                        ",value=" + record.value() +
                        ",headers=" + record.headers());
            }
            // 提交offset
            consumer.commitAsync();
        }
    }  
}  
```