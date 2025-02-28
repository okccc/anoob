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

# zookeeper是一个分布式应用程序协调服务,分布式就是利用更多机器处理更多数据,协调就是让各个节点的信息同步和共享,zookeeper=文件系统+通知机制
# zk数据结构：类似linux的树形结构,每个znode节点默认存储1M数据,适合读多写少场景,比如存储少量状态和配置信息,不适合存储大规模业务数据
# znode类型：持久的znode即使zk集群宕机也不会丢失,临时的znode断开连接就会丢失
# zk三个状态：Leading/Following/Looking
# zab协议：zookeeper原子广播,包括恢复模式和广播模式,保证主从节点数据一致
# zk事务操作：当zk服务器状态发生变化时(insert/update/delete znode)会对应一个事务请求,zk会为其分配全局事务编号zxid,编号越大说明事务越新
# zk选举机制：全新集群比较myid(半数机制),非全新集群(有机器中途宕机): 先比较zxid再比较myid
# watcher：zk允许用户在指定znode时注册watcher,当触发特定事件时zk服务端会将事件通知到客户端,以此实现分布式协调服务
# 应用场景：项目配置信息比如jdbc一般写在properties配置文件,单节点没问题但分布式集群需要统一管理,可以将配置信息写进znode让应用程序监听
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
[zk: cdh1:2181(CONNECTED) 0] delete /controller             # 删除节点
[zk: cdh1:2181(CONNECTED) 0] deleteall /brokers/topics/t1   # 删除目录

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

1.找不到或无法加载主类 org.apache.zookeeper.server.quorum.QuorumPeerMain
# apache-zookeeper-3.6.1.tar.gz是未编译的包,要下载apache-zookeeper-3.6.1-bin.tar.gz
2.org.apache.zookeeper.server.quorum.QuorumPeerConfig$ConfigException: Address unresolved: cdh1:3888
# 3888后面有空格导致无法识别端口号,linux复制文件时要注意空格
3.Caused by: java.lang.IllegalArgumentException: myid file is missing
# data目录下缺少myid文件
4.Cannot open channel to 3 at election address cdh3/192.168.152.13:3888
# 有的节点还没启动,已经启动的节点会努力寻找其它节点进行leader选举,正常现象等节点都启动就好了
5.The Cluster ID yaJab1yoRxaSZLzNUbID3g doesn not match stored clusterId Some(0effDevjT-eS3VPlDVeEsw) in meta.properties
# kafka故障重启可能会导致kafka的logs/meta.properties的cluster.id和zk中的/cluster/id不一致,把这个干掉,kafka重启之后会重新生成该文件
```

### kafka3安装
- [kafka3.x官方文档](https://kafka.apache.org/documentation/)
```shell script
# 下载
[root@cdh1~]$ wget https://archive.apache.org/dist/kafka/3.3.1/kafka_2.12-3.3.1.tgz
# 安装
[root@cdh1~]$ tar -xvf kafka_2.12-3.3.1.tgz -C /opt/module
# 添加到环境变量
[root@cdh1 ~]$ vim /etc/profile
export KAFKA_HOME=/opt/module/kafka_2.12-3.3.1
export PATH=$PATH:$KAFKA_HOME/bin

# kafka2.8以后自带zk减少网络通信(可选)
[root@cdh1 ~]$ vim zookeeper.properties
# 快照存储目录
dataDir=${KAFKA_HOME}/data/zookeeper
# 端口号
clientPort=2181
# 在dataDir目录下创建myid文件
[root@cdh1 ~]$ echo 1 > myid

# 修改kafka配置文件
[root@cdh1~]$ vim server.properties
# broker的全局唯一编号,不能重复
broker.id=0
# 监听地址
listeners=PLAINTEXT://localhost:9092
# 数据存储目录
log.dirs=${KAFKA_HOME}/data/kafka-logs
# zk地址,可以用独立的也可以用自带的
zookeeper.connect=localhost:2181

# 分发到其他节点并修改broker.id
[root@cdh1 ~]$ xsync /opt/module/kafka_2.12-3.3.1 && broker.id=1,2

# broker配置(optional)
# The largest record batch size allowed by Kafka
# message.max.bytes是broker的配置,max.message.bytes是topic的配置
message.max.bytes=10485760
# The minimum age of a log file to be eligible for deletion due to age
log.retention.hours=168
# The maximum size of a log segment file. When this size is reached a new log segment will be created.
log.segment.bytes=1073741824

# producer配置(optional)
# the default batch size in bytes when batching multiple records sent to a partition
batch.size=16384
# producer will wait for up to the given delay to allow other records to be sent so that the sends can be batched together
linger.ms=0
# the maximum size of a request in bytes
max.request.size=1048576
# specify the compression codec for all data generated: none, gzip, snappy, lz4, zstd
compression.type=none
# client to resend any record whose send fails with a potentially transient error
retries=0
# The amount of time to wait before attempting to retry a failed request to a given topic partition
retry.backoff.ms=100

# consumer配置(optional)
# The maximum amount of data per-partition the server will return
max.partition.fetch.bytes=1048576
# The maximum number of records returned in a single call to poll()
max.poll.records=500
# the maximum amount of time the client will wait for the response of a request
request.timeout.ms=305000

# 先启动zk
[root@cdh1 ~]$ zookeeper-server-start.sh -daemon config/zookeeper.properties
# 再启动kafka
[root@cdh1 ~]$ kafka-server-start.sh -daemon /config/server.properties  # 启动日志存放在./logs/server.log
[root@cdh1 ~]$ nohup kafka-server-start.sh config/server.properties > (./logs/server.log | /dev/null) 2>&1 &
# 关闭/杀掉进程
[root@cdh1 ~]$ kafka-server-stop.sh / ps -ef | grep -i 'kafka' | grep -v grep | awk '{print $2}' | xargs kill
# 一键启动kafka集群
[root@cdh1 ~]$ vim kafka.sh
#!/bin/bash
kafka_home=/opt/module/kafka_2.12-3.3.1
case $1 in
"start"){
    for i in cdh1 cdh2 cdh3
    do
        echo "============ ${i}启动kafka ============"
        # 启动kafka之前先开启JMX(Java Management Extensions)端口,不然启动kafka-manager会报错
        ssh ${i} "source /etc/profile && cd ${kafka_home}/bin && export JMX_PORT=9988 && kafka-server-start.sh -daemon ../config/server.properties"
    done
};;
"stop"){
    for i in cdh1 cdh2 cdh3
    do
        echo "============ ${i}关闭kafka(略耗时) ============"
        ssh ${i} "source /etc/profile && cd ${kafka_home}/bin && kafka-server-stop.sh"
    done
};;
esac

producer
# 生产者：往partition写数据,分区方便kafka横向扩展,提高并发和吞吐量,这样集群就可以适应任意大小的数据量
consumer-group
# 消费者组：逻辑上的订阅者,consumer可以消费多个partition但是一个partition只能被组内一个consumer消费,消费者组之间互不干扰
# 可以在idea同时启动多个消费者代码观察不同分区策略下每个consumer对partition的消费情况
broker
# 节点：存储topic并负责消息的读写,一个broker可以容纳多个topic,最先注册到zk的broker会被选举为controller
topic
# 主题：topic是逻辑的partition是物理的,一个topic分成多个partition,分区可以让消费者并行处理,分区内部消息有序先进先出全局无序
partition
# 分区：每个partition对应一个log文件,生产者不断往log文件末尾追加数据,且每条消息都有offset,保存在kafka内置topic __consumer_offsets
replication
# 副本：保证高可用性,kafka副本包括leader和follower,生产者发送数据到leader,然后follower找leader同步数据,和hdfs的副本作用完全一样
# AR = ISR(和leader保持同步的follower集合,超过replica.lag.time.max.ms没有通信就剔除,默认30s) + OSR,当leader故障时会从Isr重新选举
segment
# 片段：为了防止log文件过大难以定位数据,将其分为多个segment,包含.index(索引)和.log(数据),文件以当前segment第一条消息的offset命名
offset
# 消息偏移量：类似数组下标,如果由消费者维护消费者挂掉就丢失了,当分区或消费者发生变化时会触发rebalance机制在消费者组内重新分配,所以offset由消费者组维护
```

### kafka命令行
```shell script
# 创建topic,必须指定分区数和副本数,额外配置信息可选,不写就使用默认值
[root@cdh1 ~]$ kafka-topics.sh --bootstrap-server localhost:9092 --create --topic ods_base_db --partitions 3 --replication-factor 2 [--config key=value]
Created topic ods_base_db.

# 查看topic列表/详细信息,本地命令行也可以连接生产环境的kafka,只要指定对应的zk和kafka地址即可
[root@cdh1 ~]$ kafka-topics.sh --bootstrap-server localhost:9092 --list
[root@cdh1 ~]$ kafka-topics.sh --bootstrap-server localhost:9092 --describe [--topic ods_base_db]
Topic:ods_base_db    PartitionCount:3    ReplicationFactor:2    Configs: min.insync.replicas=1,max.message.bytes=8388608
    Topic: ods_base_db    Partition: 0    Leader: 2    Replicas: 2,1    Isr: 2,1  # 0/1/2表示broker.id
    Topic: ods_base_db    Partition: 1    Leader: 0    Replicas: 0,2    Isr: 0,2
    Topic: ods_base_db    Partition: 2    Leader: 1    Replicas: 1,0    Isr: 1,0
    
# 删除topic,对应的数据文件和zk上的节点信息也会被删除
[root@cdh1 ~]$ kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic ods_base_db

# 修改topic分区数,只能增加不能减少,因为partition可能已经有数据,增加后可能出现rebalance情况
[root@cdh1 ~]$ kafka-topics.sh --bootstrap-server localhost:9092 --alter --topic ods_base_db --partitions 2
Adding partitions succeeded!

# 手动调整分区副本,执行副本存储计划并验证
[root@cdh1 ~]$ vim increase-replication-factor.json
{"version":1,"partitions":[{"topic":"test","partition":0,"replicas":[0,1]},{"topic":"test","partition":1,"replicas":[0,1]},{"topic":"test","partition":2,"replicas":[1,0]}]}
[root@cdh1 ~]$ kafka-reassign-partitions.sh --bootstrap-server localhost:9092 --reassignment-json-file increase-replication-factor.json --execute
[root@cdh1 ~]$ kafka-reassign-partitions.sh --bootstrap-server localhost:9092 --reassignment-json-file increase-replication-factor.json --verify

# 修改topic配置信息
[root@cdh1 ~]$ kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name ods_base_db --alter --add-config max.message.bytes=5242880
Completed updating config for topic ods_base_db.
# 查看修改内容
[root@cdh1 ~]$ kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --describe [--entity-name ods_base_db]
Dynamic configs for topic ods_base_db are:
  max.message.bytes=5242880 sensitive=false synonyms={DYNAMIC_TOPIC_CONFIG:max.message.bytes=5242880, DEFAULT_CONFIG:message.max.bytes=1048588}
# 修改完会在Configs中显示配置信息
[root@cdh1 ~]$ kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic ods_base_db                                                                
Topic: ods_base_db    PartitionCount: 1    ReplicationFactor: 1    Configs: max.message.bytes=5242880
# 也可以在zk中查看topic配置信息
[zk: cdh1:2181(CONNECTED) 0] get /config/topics/ods_base_db
{"version":1,"config":{"max.message.bytes":"5242880"}}
# 撤销修改
[root@cdh1 ~]$ kafka-configs.sh --bootstrap-server localhost:9092 --entity-type topics --entity-name ods_base_db --alter --delete-config max.message.bytes
Completed updating config for topic ods_base_db.

# 生产者
[root@cdh1 ~]$ kafka-console-producer.sh --bootstrap-server localhost:9092 --topic ods_base_db
>java bigdata
# 消费者
[root@cdh1 ~]$ kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ods_base_db [--from-beginning] [--max-messages 1]
java bigdata

# 查看消费者组列表
[root@cdh1 ~]$ kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
g01
# 查看消费者组详细信息(非常有用,消费进度能反映数据积压程度,看看是否需要提高消费者并行度,配合flink反压一起观察)
[root@cdh1 ~]$ kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group g01
GROUP    TOPIC    PARTITION    CURRENT-OFFSET    LOG-END-OFFSET    LAG    CONSUMER-ID    HOST    CLIENT-ID
g01   ods_base_db     0             6845               6923        78     consumer-g01-1  /172.18.1.111   consumer-g01-1
# 删除消费者组(必须先停掉消费者)
[root@cdh1 ~]$ kafka-consumer-groups.sh --bootstrap-server localhost:9092 --delete --group g01
Deletion of requested consumer groups ('g01') was successful.
# 重置消费者组的偏移量,to-earliest/to-latest/to-offset <Long>/shift-by <Long>/to-datetime <YYYY-MM-DDTHH:mm:SS.sss>
[root@cdh1 ~]$ kafka-consumer-groups.sh --bootstrap-server localhost:9092 --reset-offsets --group g01 --all-topics(--topic nginx) --to-earliest --execute
Error: Assignments can only be reset if the group 'g01' is inactive, but the current state is Stable.
GROUP    TOPIC    PARTITION    NEW-OFFSET
g01      nginx        0          0 

# 查看topic消费情况
groups=$(kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list)
for group in ${groups}
do
    kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group ${group} >> a.txt
    sleep 5
done
```

### kafka调优
```shell script
# Kafka机器数 = 2 * (峰值生产速度 * 副本数 / 100) + 1
# 比如100万日活,每人每天100条日志,每条日志大约1k,平均流量=1亿条/24*3600/1024=1M/s
# 假设峰值生产速度是30M/s,副本数为3,那么机器数量 = 2 * (30 * 3 / 100) + 1 = 3(台)

# kafka分区数 = 期望的总吞吐量 / min(生产者吞吐量, 消费者吞吐量)
# 比如生产速度100M/s,消费速度50M/s,期望kafka集群的总吞吐量150M/s,那么分区数 = 150 / min(100, 50) = 3
# 增加分区数可以提高吞吐量,partition数 >= consumer数实现最大并发,但是分区过多也会导致 1.内存开销增大 2.leader选举和offset查询耗时增加
# replication数会影响producer/consumer流量,比如3副本则实际流量 = 生产流量 × 3

# kafka压力测试：看看CPU/内存/IO使用情况,一般都是网络IO达到瓶颈
# 生产者压测
# record-size是一条信息字节数,num-records是发送信息总条数,throughput是每秒发送条数,-1表示不限流,可测出生产者的最大吞吐量
# 本例中共写入100w条数据,吞吐量为87.90 MB/sec,每次写入的平均延迟为336.12毫秒,最大延迟为666.00毫秒
[root@cdh1 ~]$ kafka-producer-perf-test.sh --producer-props bootstrap.servers=localhost:9092 --topic test --record-size 1000 --num-records 1000000 --throughput -1
1000000 records sent, 92165.898618 records/sec (87.90 MB/sec), 336.12 ms avg latency, 666.00 ms max latency
# 消费者压测
# fetch-size是每次消费的数据大小,messages是消费的消息总数
# 本例中共消费867MB数据,吞吐量为73 MB/sec,共消费1000000条,平均84516条/sec
[root@cdh1 ~]$ kafka-consumer-perf-test.sh --broker-list localhost:9092 --topic test --fetch-size 10000 --messages 1000000 --threads 1
start.time, end.time, data.consumed.in.MB, MB.sec, data.consumed.in.nMsg, nMsg.sec
2021-06-07 10:43:13:883, 2021-06-07 10:43:25:715, 867.8436, 73.3472, 1000000, 84516.5652
```

### kafka-manager
```shell script
# 滴滴开源工具https://github.com/didi/LogiKM(推荐)
# github官网只提供了kafka-manage的源码,需要手动编译
# 修改配置文件
[root@cdh1 ~]$ vim conf/application.conf
kafka-manager.zkhosts="localhost:2181"  # 配置kafka集群zk地址
akka {
   loggers = ["akka.event.slf4j.Slf4jLogger"]
   loglevel = "INFO"
   logger-startup-timeout = 30s  # 添加超时时间不然启动报错
 }
# 给命令添加可执行权限
[root@cdh1 ~]$ chmod +x kafka-manager
# 启动脚本
[root@cdh1 ~]$ vim km.sh & chmod +x km.sh
#!/bin/bash
case $1 in
"start"){
    echo "-------- 启动KafkaManager -------"
    nohup bin/kafka-manager -Dconfig.file=conf/application.conf -Dhttp.port=7456 > start.log 2>&1 &
};;
"stop"){
    echo "-------- 停止KafkaManager -------"
    ps -ef | grep ProdServerStart | grep -v grep | awk '{print $2}' | xargs kill 
};;
esac
# 查看日志,如果启不来就把application.home_IS_UNDEFINED目录删掉
[root@cdh1 ~]$ tail -f start.log | tail -f application.home_IS_UNDEFINED/logs/application.log
# 页面监控,可以在页面添加多个kafka集群
http://localhost:7456/
```