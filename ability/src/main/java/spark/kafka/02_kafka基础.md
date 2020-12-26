### zookeeper
```shell script
# zookeeper是一个分布式应用程序协调服务,分布式就是利用更多机器处理更多数据,协调就是让各个节点的信息能够同步和共享,zookeeper=文件系统+通知机制
# zk数据结构：类似linux的树形结构,每个znode节点默认存储1M数据,适合读多写少场景,比如存储少量状态和配置信息,不适合存储大规模业务数据
# znode类型：持久的znode即使zk集群宕机也不会丢失,临时的znode断开连接就会丢失
# zk三个状态：Leading/Following/Looking
# zab协议：zookeeper原子广播,包括恢复模式和广播模式,保证主从节点数据一致
# zk事务操作：当zk服务器状态发生变化时(insert/update/delete znode)会对应一个事务请求,zk会为其分配一个全局的事务编号zxid,编号越大说明事务越新
# zk选举机制：全新集群比较myid(半数机制),非全新集群(有机器中途宕机): 先比较zxid再比较myid
# watcher：zk允许用户在指定znode时注册watcher,当触发特定事件时zk服务端会将事件通知到客户端,以此实现分布式协调服务
# 应用场景：项目中配置信息比如jdbc一般写在properties配置文件,单节点没问题但是分布式集群需要统一配置管理,可以将配置信息写进znode让应用程序监听
# Hadoop使用zk做namenode高可用,Kafka依赖zk维护broker信息,Hbase客户端连接zk获取集群配置信息再进行后续操作

# znode节点
[zk: localhost:2181(CONNECTED) 0] ls /zookeeper             # zk信息
[zk: localhost:2181(CONNECTED) 0] ls /hadoop-ha             # hadoop信息
[zk: localhost:2181(CONNECTED) 0] ls /yarn-leader-election  # yarn信息
[zk: localhost:2181(CONNECTED) 0] ls /brokers               # kafka启动时会向zk注册broker节点信息
[zk: localhost:2181(CONNECTED) 0] ls /brokers/ids           # kafka存活节点的broker.id
[zk: localhost:2181(CONNECTED) 0] ls /brokers/topics        # kafka的topic信息
[zk: localhost:2181(CONNECTED) 0] get /controller           # kafka成为controller的节点
[zk: localhost:2181(CONNECTED) 0] get /consumers            # kafka消费者信息
[zk: localhost:2181(CONNECTED) 0] get /admin/delete_topics  # kafka删除的topic

# zk实现分布式锁: 当锁的持有者断开时锁会自动释放,zk的临时znode可以实现这个功能
# 在cli1创建临时znode
[zk: localhost:2181(CONNECTED) 0] create -e /lock 'lock'  # -e表示临时,此时cli2无法创建/lock会显示已存在
# 在cli2监控该znode
[zk: localhost:2181(CONNECTED) 0] stat -w /lock  # 当cli1断开连接时/lock自动丢失,此时cli2可以创建/lock

# zk实现master-worker协同: 要求系统中只能有一个master,且master能实时监控worker情况
# 在cli1创建临时znode
[zk: localhost:2181(CONNECTED) 0] create -e /master 'master:8888'
# 在cli1创建持久znode并监控该znode
[zk: localhost:2181(CONNECTED) 0] create /workers && ls -w /workers
# 在cli2的/workers节点下创建临时znode并观察监控变化
[zk: localhost:2181(CONNECTED) 0] create -e /workers/w1 'w1:8888'

# zk启动报错
org.apache.zookeeper.server.quorum.QuorumPeerConfig$ConfigException: Address unresolved: cdh1:3888
# 3888后面有空格导致无法识别端口号,linux复制文件时要注意空格
Caused by: java.lang.IllegalArgumentException: myid file is missing
# data目录下缺少myid文件
Cannot open channel to 3 at election address cdh3/192.168.152.13:3888
# 有的节点还没启动,已经启动的节点会努力寻找其它节点进行leader选举,正常现象等节点都启动就好了

# 安装
[root@cdh1 opt]$ tar -xvf zookeeper-3.6.1.tar.gz -C /opt/module
# 修改配置文件
[root@cdh1 conf]$ vim zoo.cfg
# The number of milliseconds of each tick
tickTime=2000  
# leader与follower之间初始连接能容忍的最多心跳数(tickTime数量) 
initLimit=10  
# leader与follower之间请求应答能容忍的最多心跳数(tickTime数量)
syncLimit=5
# 存储快照
dataDir=/opt/module/zookeeper-3.6.1/data
# 存储事务日志
dataLogDir=/opt/module/zookeeper-3.6.1/logs
# the port at which the clients will connect 
clientPort=2181  
# 服务器名称与地址：集群信息(服务器编号,服务器地址,LF通信端口,选举端口)
server.1=cdh1:2888:3888
server.2=cdh2:2888:3888
server.3=cdh3:2888:3888
# 添加到环境变量
[root@cdh1 ~]$ vim /etc/profile
export ZK_HOME=/opt/module/zookeeper-3.6.1
export PATH=$PATH:$ZK_HOME/bin
# 在data目录下创建myid文件
[root@cdh1 ~]$ echo '1' > myid
# 一键分发并修改myid
[root@cdh1 module]$ xsync /opt/module/zookeeper-3.6.1
# 启动,hdfs/yarn/kafka都依赖zk管理
[root@cdh1 ~]$ zkServer.sh start-foreground/stop/status
# 一键启动脚本
[root@cdh1 ~]$ vim zk.sh
#!/bin/bash
case $1 in
"start"){
    for i in cdh1 cdh2 cdh3
    do
        echo "=============== ${i}启动zk ==============="
        ssh ${i} "source /etc/profile && zkServer.sh start"
    done
};;
"stop"){
    for i in cdh1 cdh2 cdh3
    do
        echo "=============== ${i}停止zk ==============="
        ssh ${i} "source /etc/profile && zkServer.sh stop"
    done
};;
"status"){
    for i in cdh1 cdh2 cdh3
    do
        echo "=============== ${i}zk状态 ==============="
        ssh ${i} "source /etc/profile && zkServer.sh status"
    done
};;
esac
# 打开客户端
[root@cdh1 ~]$ zkCli.sh -server host:port
# 查看事务日志
[root@cdh1 version-2]$ zkTxnLogToolkit.sh log.100000001
# 查看快照文件
[root@cdh1 version-2]$ zkSnapShotToolkit.sh snapshot.0
```

### install
```shell script
# 修改配置文件
[root@cdh1~]$ vim server.properties
# broker的全局唯一编号,不能重复
broker.id=0
# 开启删除topic功能,否则只是标记删除并没有真正删除
delete.topic.enable=true
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
# kafka日志存放路径,如果没有单独指定data.dir,消息也存放在该目录
log.dirs=/opt/module/kafka_2.11-0.11.0.2/logs
# topic在当前broker上的分区个数
num.partitions=1
# 恢复和清理data下数据的线程数
num.recovery.threads.per.data.dir=1
# The replication factor for the group metadata internal topics "__consumer_offsets" and "__transaction_state"
# For anything other than development testing, a value greater than 1 is recommended for to ensure availability such as 3.
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
# log文件保留时长
log.retention.hours=168
# segment文件最大值
log.segment.bytes=1073741824
# zk地址
zookeeper.connect=cdh1:2181,cdh2:2181,cdh3:2181
# zk连接超时时间(毫秒)
zookeeper.connection.timeout.ms=6000
group.initial.rebalance.delay.ms=0
# 添加到环境变量
[root@cdh1 ~]$ vim /etc/profile && source /etc/profile
export KAFKA_HOME=/opt/module/kafka
export PATH=$PATH:$KAFKA_HOME/bin
# 分发到其他节点并修改broker.id
[root@cdh1 ~]$ xsync /opt/module/kafka_2.11-0.11.0.2 & broker.id=1/2
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
# 杀掉kafka进程
[root@cdh1 bin]$ ps -aux | grep -i 'kafka' | grep -v grep | awk '{print $2}' | xargs kill
# 创建topic,指定分区数和副本数
[root@cdh1 ~]$ bin/kafka-topics.sh --zookeeper cdh1:2181 --create --topic t01 --partitions 3 --replication-factor 2
Topic creation {"version":1,"partitions":{"1":[1,2,0],"0":[0,1,2]}}
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
# 继续在zookeeper中删除
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
# 一键启动/停止kafka
[root@cdh1 ~]$ vim kafka.sh
#!/bin/bash
case $1 in
"start"){
    for i in cdh1 cdh2 cdh3
    do
        echo "============ ${i}启动kafka ============"
        # 启动kafka时开启JMX端口,用于后续KafkaManager监控
        ssh ${i} "source /etc/profile && cd /opt/module/kafka_2.11-0.11.0.2/bin && export JMX_PORT=9988 && kafka-server-start.sh -daemon ../config/server.properties"
    done
};;
"stop"){
    for i in cdh1 cdh2 cdh3
    do
        echo "============ ${i}停止kafka ============"
        ssh ${i} "source /etc/profile && cd /opt/module/kafka_2.11-0.11.0.2/bin && kafka-server-stop.sh"
    done
};;
esac

# kafka出问题如何排查？
# 查进程 - 查日志
# kafka关闭有延迟,如果zk先停了,/brokers/ids下的节点还在,此时kafka还存活但与zk失去连接导致无法停止,只能手动杀掉进程
# kafka故障重启可能会导致kafka的logs/meta.properties的cluster.id不一致,把这个干掉,kafka重启之后会重新生成该文件
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

# 生产者数据可靠性
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
# 5).精准一次
at most once 可能会丢数据 | at least once 可能重复消费 | exactly once 精准消费,保证每条消息都会被发送且仅发送一次
kafka0.11版本引入了幂等性机制(去重) enable.idempotence=true, kafka会自动修改ack=-1, at least once + idempotent = exactly once

# 消费者数据可靠性


# 消息丢失和重复消费场景
消息丢失：producer端ack=0/1,consumer端先提交后消费
重复消费：producer端ack=-1,consumer端先消费后提交

# 生产者分区策略
a.指定partition
b.没有指定partition但是有key,将key的hash值与partition数进行取余决定往哪个partition写数据
c.没有指定partition也没有key,将递增的随机数与partition数进行取余决定往哪个partition写数据,这就是round-robin轮询算法
# 消费者消费方式
push模式难以适应消费速率不同的消费者,因为发送速率由broker决定,消费者来不及处理会导致网络堵塞甚至程序崩溃
consumer采用pull模式从broker读取数据,根据自身能力消费,缺点是broker没数据时会陷入空循环,需要指定超时参数timeout
# 消费者分区分配策略
partition和consumer之间有两种分区分配策略,round-robin和range
# kafka高效读写数据
1.顺序写磁盘：producer往partition写数据是按照顺序追加到log文件的末端,顺序写速度快是因为省去了大量的磁头寻址时间,比如往一个大文件写比往多个小文件写速度快得多
2.零拷贝技术：计算机在网络中发送文件时直接在内核空间Kernel Space传输到网络,不用拷贝到用户空间User Space,省去在内存中的拷贝
# controller和zk
kafka集群启动时会向zookeeper注册节点信息,最先注册的broker节点就是controller,关闭kafka时zookeeper的/controller和/brokers/ids会清空
controller会监控zookeeper上节点的变化情况,负责管理broker节点上下线/leader选举/topic的分区副本分配,zookeeper辅助controller进行管理工作
```