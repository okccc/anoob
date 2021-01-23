### zookeeper
```shell script
# 下载安装
[root@cdh1 opt]$ wget http://archive.apache.org/dist/zookeeper/zookeeper-3.6.1/apache-zookeeper-3.6.1-bin.tar.gz
[root@cdh1 opt]$ tar -xvf apache-zookeeper-3.6.1-bin.tar.gz -C /opt/module
# 修改配置文件
[root@cdh1 conf]$ vim zoo.cfg
# 存储快照
dataDir=/Users/okc/modules/apache-zookeeper-3.6.1-bin/data
# 存储事务日志
dataLogDir=/Users/okc/modules/apache-zookeeper-3.6.1-bin/logs
# 端口
clientPort=2181
# 服务器编号=服务器地址:LF通信端口:选举端口
server.1=cdh1:2888:3888
server.2=cdh2:2888:3888
server.3=cdh3:2888:3888
# 添加到环境变量
[root@cdh1 ~]$ vim /etc/profile
export ZK_HOME=/Users/okc/modules/apache-zookeeper-3.6.1-bin
export PATH=$PATH:$ZK_HOME/bin
# 在data目录下创建myid文件
[root@cdh1 ~]$ echo 1 > myid
# 一键分发并修改myid
[root@cdh1 module]$ xsync /Users/okc/modules/apache-zookeeper-3.6.1-bin

# 启动,hdfs/yarn/kafka都依赖zk管理
[root@cdh1 ~]$ zkServer.sh start-foreground/stop/status
# 一键启动zk集群
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
[zk: cdh1:2181(CONNECTED) 0] ls /brokers/ids                # kafka存活节点的信息
[zk: cdh1:2181(CONNECTED) 0] ls /brokers/topics             # kafka的topic基本信息
[zk: cdh1:2181(CONNECTED) 0] ls /config/topics              # kafka的topic配置信息
[zk: cdh1:2181(CONNECTED) 0] ls /zookeeper                  # zk信息
[zk: cdh1:2181(CONNECTED) 0] ls /bigdata-ha                 # hadoop信息
[zk: cdh1:2181(CONNECTED) 0] ls /yarn-leader-election       # yarn信息
[zk: cdh1:2181(CONNECTED) 0] get /controller                # kafka成为controller的节点
[zk: cdh1:2181(CONNECTED) 0] get /consumers                 # kafka消费者信息
[zk: cdh1:2181(CONNECTED) 0] get /admin/delete_topics       # kafka删除的topic
[zk: cdh1:2181(CONNECTED) 0] get /isr_change_notification   # isr变化通知
[zk: cdh1:2181(CONNECTED) 0] get /latest_producer_id_block  # 最新生产者块信息

# zk实现分布式锁: 当锁的持有者断开时锁会自动释放,zk的临时znode可以实现这个功能
# 在cli1创建临时znode
[zk: cdh1:2181(CONNECTED) 0] create -e /lock 'lock'  # -e表示临时,此时cli2无法创建/lock会显示已存在
# 在cli2监控该znode
[zk: cdh1:2181(CONNECTED) 0] stat -w /lock  # 当cli1断开连接时/lock自动丢失,此时cli2可以创建/lock

# zk实现master-worker协同: 要求系统中只能有一个master,且master能实时监控worker情况
# 在cli1创建临时znode
[zk: cdh1:2181(CONNECTED) 0] create -e /master 'master:8888'
# 在cli1创建持久znode并监控该znode
[zk: cdh1:2181(CONNECTED) 0] create /workers && ls -w /workers
# 在cli2的/workers节点下创建临时znode并观察监控变化
[zk: cdh1:2181(CONNECTED) 0] create -e /workers/w1 'w1:8888'

# zk常见问题
1.找不到或无法加载主类 org.apache.zookeeper.server.quorum.QuorumPeerMain
# apache-zookeeper-3.6.1.tar.gz是未编译的包,要下载apache-zookeeper-3.6.1-bin.tar.gz
2.org.apache.zookeeper.server.quorum.QuorumPeerConfig$ConfigException: Address unresolved: cdh1:3888
# 3888后面有空格导致无法识别端口号,linux复制文件时要注意空格
3.Caused by: java.lang.IllegalArgumentException: myid file is missing
# data目录下缺少myid文件
4.Cannot open channel to 3 at election address cdh3/192.168.152.13:3888
# 有的节点还没启动,已经启动的节点会努力寻找其它节点进行leader选举,正常现象等节点都启动就好了
```

### kafka
- [kafka官方文档](http://kafka.apache.org/0110/documentation.html)
```shell script
# 下载安装
[root@cdh1~]$ wget https://mirror.bit.edu.cn/apache/kafka/2.4.1/kafka_2.11-2.4.1.tgz
[root@cdh1~]$ tar -xvf kafka_2.11-2.4.1.tgz -C /opt/module
# 修改配置文件
[root@cdh1~]$ vim server.properties
# broker的全局唯一编号,不能重复
broker.id=0
# 开启删除topic功能,否则只是标记删除并没有真正删除
delete.topic.enable=true
# kafka日志存放路径,消息也存放在该目录
log.dirs=/Users/okc/modules/kafka_2.11-2.4.1/logs
# zk地址
zookeeper.connect=cdh1:2181,cdh2:2181,cdh3:2181
# 添加到环境变量
[root@cdh1 ~]$ vim /etc/profile
export KAFKA_HOME=/Users/okc/modules/kafka_2.11-2.4.1
export PATH=$PATH:$KAFKA_HOME/bin
# 分发到其他节点并修改broker.id
[root@cdh1 ~]$ xsync /Users/okc/modules/kafka_2.11-2.4.1 & broker.id=1/2

# 启动kafka,默认是前台进程,可以在后台启动
[root@cdh1 ~]$ kafka-server-start.sh -daemon /config/server.properties  # 日志默认存放在logs/server.log
[root@cdh1 ~]$ nohup kafka-server-start.sh config/server.properties > (logs/server.log | /dev/null) 2>&1 &
# 关闭/杀掉进程
[root@cdh1 ~]$ kafka-server-stop.sh / ps -aux | grep -i 'kafka' | grep -v grep | awk '{print $2}' | xargs kill
[root@cdh1 bin]$ 
# 一键启动kafka集群
[root@cdh1 ~]$ vim kafka.sh
#!/bin/bash
kafka_home=/opt/module/kafka_2.11-0.11.0.2
case $1 in
"start"){
    for i in cdh1 cdh2 cdh3
    do
        echo "============ ${i}启动kafka ============"
        # 启动kafka时开启JMX(Java Management Extensions)端口,用于后续KafkaManager监控
        ssh ${i} "source /etc/profile && cd {kafka_home}/bin && export JMX_PORT=9988 && kafka-server-start.sh -daemon ../config/server.properties"
    done
};;
"stop"){
    for i in cdh1 cdh2 cdh3
    do
        echo "============ ${i}关闭kafka(略耗时) ============"
        ssh ${i} "source /etc/profile && cd {kafka_home}/bin && kafka-server-stop.sh"
    done
};;
esac

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
# 分区：每个partition对应一个log文件,生产者生产的消息会不断追加到文件末尾,且每条消息都有offset,保存在kafka内置topic __consumer_offsets
replication
# 副本：为了保证高可用性,每个partition都有副本,leader负责工作,follower负责同步数据,当leader故障时Isr中的某个follower会被选举为新的leader
segment
# 片段：为了防止log文件过大难以定位数据,将其分为多个segment,包含.index(存储索引)和.log(存储数据),文件以当前segment第一条消息的offset命名
offset
# 偏移量：如果是消费者维护消费者挂掉offset就丢失了,当分区或消费者发生变化时会触发rebalance机制在消费者组内重新分配,所以offset是消费者组维护
```

### shell
```shell script
# 创建topic,必须指定分区数和副本数,额外配置信息可选,不写就使用默认值
[root@cdh1 ~]$ kafka-topics.sh --zookeeper cdh1:2181 --create --topic t01 --partitions 3 --replication-factor 2 [--config key=value]
Topic creation {"version":1,"partitions":{"1":[1,2,0],"0":[0,1,2]}}
Created topic "t01".

# 查看topic列表/详细信息
[root@cdh1 ~]$ kafka-topics.sh --zookeeper cdh1:2181,cdh2:2181,cdh3:2181 --list
[root@cdh1 ~]$ kafka-topics.sh --zookeeper cdh1:2181,cdh2:2181,cdh3:2181 --describe [--topic t01]
Topic:t01       PartitionCount:3        ReplicationFactor:2     Configs:
    Topic: t01      Partition: 0    Leader: 2    Replicas: 2,1    Isr: 2,1  # 0/1/2表示broker.id
    Topic: t01      Partition: 1    Leader: 0    Replicas: 0,2    Isr: 0,2
    Topic: t01      Partition: 2    Leader: 1    Replicas: 1,0    Isr: 1,0
    
# 修改topic分区数,只能增加不能减少,因为partition可能已经有数据
[root@cdh1 ~]$ kafka-topics.sh --zookeeper cdh1:2181 --alter --topic t01 --partitions 2
WARNING: If partitions are increased for a topic that has a key, the partition logic or ordering of message will affect
Adding partitions succeeded!

# 修改topic配置信息
[root@cdh1 ~]$ kafka-configs.sh --zookeeper cdh1:2181 --entity-type topics --entity-name nginx --alter --add-config max.message.bytes=5242880
Completed Updating config for entity: topic 'nginx'.
# 查看修改内容
[root@cdh1 ~]$ kafka-configs.sh --zookeeper cdh1:2181 --entity-type topics --describe [--entity-name nginx]
Configs for topic 'nginx' are max.message.bytes=5242880
# 修改完会在Configs中显示配置信息
[root@cdh1 ~]$ kafka-topics.sh --zookeeper cdh1:2181 --describe --topic nginx                                                                
Topic: nginx	PartitionCount: 1	ReplicationFactor: 1	Configs: max.message.bytes=5242880
	Topic: nginx	Partition: 0	Leader: 0	Replicas: 0	Isr: 0
# 也可以在zk中查看topic配置信息
[zk: cdh1:2181(CONNECTED) 0] get /config/topics/nginx
{"version":1,"config":{"max.message.bytes":"5242880"}}
# 撤销修改
[root@cdh1 ~]$ kafka-configs.sh --zookeeper cdh1:2181 --entity-type topics --entity-name nginx --alter --delete-config max.message.bytes
Completed Updating config for entity: topic 'nginx'.

# 删除topic,对应的数据文件和zk上的节点信息也会被删除
[root@cdh1 ~]$ kafka-topics.sh --zookeeper cdh1:2181 --delete --topic t01
Topic t01 is marked for deletion.
Note: This will have no impact if delete.topic.enable is not set to true.

# 生产者
[root@cdh1 ~]$ kafka-console-producer.sh --broker-list cdh1:9092,cdh2:9092,cdh3:9092 --topic t01
>java bigdata
# 消费者,--from-beginning表示读取主题中以往所有数据
[root@cdh1 ~]$ kafka-console-consumer.sh --bootstrap-server cdh1:9092 [--from-beginning] --topic t01
java bigdata

# 查看consumer-group列表/详细信息
[root@cdh1 ~]$ kafka-consumer-groups.sh --bootstrap-server cdh1:9092,cdh2:9092,cdh3:9092 --list
[root@cdh1 ~]$ kafka-consumer-groups.sh --bootstrap-server cdh1:9092,cdh2:9092,cdh3:9092 --describe --group g01
# 重置消费者组的offset
[root@cdh1 ~]$ kafka-consumer-groups.sh --bootstrap-server cdh1:9092 --group g01 --reset-offsets --all-topics --to-earliest --execute
```