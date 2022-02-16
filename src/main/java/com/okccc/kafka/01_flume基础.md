### nginx
```shell script
# nginx三大功能：反向代理、负载均衡、动静分离
# 安装依赖
[root@cdh1 ~]$ yum -y install gcc pcre-devel zlib zlib-devel openssl openssl-devel net-tools
# 下载
[root@cdh1 ~]$ wget http://nginx.org/download/nginx-1.12.2.tar.gz
# 解压
[root@cdh1 ~]$ tar -xvf nginx-1.21.2.tar.gz -C /usr/local
# 切换到nginx目录
[root@cdh1 ~]$ cd /usr/local/nginx-1.21.2
# 编译安装
[root@cdh1 ~]$ ./configure
[root@cdh1 ~]$ make && make install  # 安装完后/nginx/sbin目录多了nginx执行命令
# 测试配置文件
[root@cdh1 ~]$ /usr/local/nginx/sbin/nginx -t
# 启动/停止/重启
[root@cdh1 ~]$ /usr/local/nginx/sbin/nginx
[root@cdh1 ~]$ /usr/local/nginx/sbin/nginx -s stop
[root@cdh1 ~]$ /usr/local/nginx/sbin/nginx -s reload
# 查看nginx进程,jps显示的是java进程,nginx是c++写的
[root@cdh1 ~]$ ps -ef | grep nginx
# 浏览器访问(默认80端口)
http://192.168.152.11
Welcome to nginx!
```

### flume
- [flume官方文档](http://flume.apache.org/releases/content/1.9.0/FlumeUserGuide.html)
```shell script
# 下载
[root@cdh1 ~]$ wget https://mirror.bit.edu.cn/apache/flume/1.9.0/apache-flume-1.9.0-bin.tar.gz
# 安装
[root@cdh1 ~]$ tar -xvf apache-flume-1.9.0-bin.tar.gz
# 修改配置文件
[root@cdh1 ~]$ vim flume-env.sh
export JAVA_HOME=/usr/java/jdk1.8.0_181-cloudera
# flume内存优化,将JVM heap设置为4g防止OOM,-Xms和-Xmx尽量保持一致减少内存抖动带来的性能影响
export JAVA_OPTS="-Xms4096m -Xmx4096m -Dcom.sun.management.jmxremote"
# 创建logs目录
[root@cdh1 ~]$ mkdir logs

# 集群生成日志启动脚本
# 解压jar包: tar -xvf mock.jar -C mock(目标目录需提前创建),jar包本质上也是压缩包,直接用tar命令解压缩即可
# java -jar/-cp区别: 打包时mainClass已指定类名java -jar a.jar,未指定类名java -cp a.jar 包名.类名,可在解压jar包的META-INF文件中查看
[root@cdh1 ~]$ vim log.sh
#!/bin/bash
for i in cdh1 cdh2 cdh3
do
    ssh $i "source /etc/profile && cd /opt/module && java -cp mock-1.0-SNAPSHOT-jar-with-dependencies.jar app.AppMain > a.log &"
done

# 启动flume(每个nginx节点都单独装flume,单个nginx节点配置是8核16g,4个节点峰值流量100M/S,稳定流量10M/S)
# 运维每天凌晨会定时切割nginx日志,不然日志文件会无限增大,切割后监控的日志文件inode变了,所以flume进程也要重启,position从0开始
[{"inode":70,"pos":10743524421,"file":"/data/logs/access_sdk.log"}]
[root@cdh1 ~]$ vim flume.sh
#!/bin/bash
case $1 in
"start"){
    echo "================= 启动flume,重定向日志使用追加模式便于追溯历史排查问题 ================"
    nohup flume-ng agent -c conf -f conf/nginx-kafka.conf -n a1 -C lib/Interceptor.jar -Dflume.root.logger=info,console >> logs/flume.log 2>&1 &
};;
"stop"){
    echo "================= 停止flume ================"
    ps -ef | grep flume | grep -v grep | awk '{print \$2}' | xargs kill  # 这里的$2要加\转义,不然会被当成脚本的第二个参数
};;
esac

# event
flume传输数据的基本单元,由headers和body组成 Event: {headers:{} body: 61 61 61  aaa}
headers是Map<String, String>集合,可以根据key来区分不同event并将其分流,headers并不会被传输,body是byte[]数组,是真正传输的数据
# agent
jvm运行flume的最小单元,由source-channel-sink组成
# source
flume1.7版本使用TailDir可以监控多目录,且会记录日志文件读取位置,故障重启后就从该位置开始,解决断点续传问题
# channel
file channel：数据存到磁盘,速度慢,可靠性高,默认100万个event,适用于涉及钱的数据
memory channel：数据存到内存,速度快,可靠性低,默认100个event,适用于普通日志
kafka channel：数据存到kafka也是磁盘,可靠性高,且省去sink阶段速度更快,kafka channel > memory channel + sink
channel selectors：replicating将events发往所有channel,multiplexing将events发往指定channel
# sink
不断轮询channel中的事件并将其移除到存储系统或下一个agent,目的地通常是hdfs/logger/kafka

# flume调优
a1.sources.r1.batchSize = 1000  # 控制往channel发送数据的批次大小,可以适当调大提高吞吐量,但是不能超过capacity和transactionCapacity
a1.sources.ri.maxBatchCount = 1000  # 控制连续读取同一文件的最大批次,防止某个文件写入速度远快于其他文件,导致其他文件无法被读取
a1.sources.r1.writePosInterval = 1000  # 控制往position.json写入inode和pos的频率,可以减少Agent故障重启时从position重复读取的数据量
a1.sources.r1.ServerConnector.idleTimeout = 300  # 超过该时间没有新增行就关闭文件防止文件资源一直占用,有新的行写入会自动重新打开该文件
a1.channels.c1.transactionCapacity = 5000  # batchSize <= transactionCapacity <= capacity
a1.channels.c1.capacity = 10000  # 可以适当调大提高吞吐量,还能避免The channel is full or unexpected failure异常
a1.channels.c1.keep-alive = 15  # put/take事务的超时时间,适当调大防止channel处于时满时空状态
```

### problems
```shell script
1.java.io.FileNotFoundException: /opt/cloudera/parcels/CDH/lib/flume-ng/position/log_position.json (Permission denied)
# 显示没有positionFile文件的写入权限,可以先将该文件所属目录读写权限改成777,然后看是哪个用户在读写该文件(这里是flume),再修改目录所属用户即可

2.Caused by: java.lang.ClassNotFoundException: com.okccc.interceptor.InterceptorDemo$Builder
# java找不到类要么是打jar包类没加载进去,要么是jar包冲突,要么是启动命令没找到lib/Interceptor.jar,可以在flume-ng命令行里-C手动指定jar包

3.Producer clientId=producer-1 Connection to node 0 could not be established. Broker may not be available.
# flume往kafka写数据时,下游kafka挂了导致flume作为生产者一直连不上broker,重启kafka之后flume也要重启然后继续之前的position采集和发送数据
# nginx-flume-kafka采集通道正常时flume日志的ClusterID和zookeeper的/cluster/id以及kafka日志的meta.properties的cluster.id应该相同

4.RecordTooLargeException: The request included a message 2262864 bytes which is larger than the max message size the server will accept.
# flume发送消息大小超过了kafka生产者最大请求字节数(默认1M),agent添加配置a1.channels.c1.kafka.producer.max.request.size = 5242880
# flume作为kafka生产者的配置信息在其运行日志flume.log通过ProducerConfig values可以找到
# kafka消息大小有限制 max.request.size(producer端) < message.max.bytes(broker端) < max.partition.fetch.bytes(consumer端)
# flume数据传输失败position位置是不更新的,直到修复问题重启脚本后继续之前的位置采集,Last read was never committed - resetting position
```

#### nginx-kafka.conf
```shell script
# 注意：生产环境上编写conf文件时不要在行的后面加#注释,会被当成类名
# 命名agent组件
a1.sources = r1
a1.channels = c1 c2

# 配置source
a1.sources.r1.type = TAILDIR  # exec方式flume宕机会丢数据
# File in JSON format to record the inode, the absolute path and the last position of each tailing file
a1.sources.r1.positionFile = ${flume}/taildir_position.json  # 如果不存在会自动创建,并且从头读取所有文件,记录每个文件的末尾位置
a1.sources.r1.filegroups = f1                  # 监控的是一组文件
a1.sources.r1.filegroups.f1 = /tmp/logs/app.+  # 一组文件以空格分隔,也支持正则表达式,目录必须存在不然报错
a1.sources.r1.channels = c1 c2
# 拦截器(jar包放到flume的lib目录)
a1.sources.r1.interceptors = i1 i2
a1.sources.r1.interceptors.i1.type = com.okccc.interceptor.ETLInterceptor$Builder
a1.sources.r1.interceptors.i2.type = com.okccc.interceptor.TypeInterceptor$Builder
# 选择器(配合拦截器使用)
a1.sources.r1.selector.type = multiplexing     # 根据日志类型指定channel
a1.sources.r1.selector.header = type           # headers的key,通过headers对event分流
a1.sources.r1.selector.mapping.start = c1      # headers的value=start发往c1
a1.sources.r1.selector.mapping.event = c2      # headers的value=event发往c2

# 配置channel,可以将数据发往多个channel,比如集群内的多个topic,或者不同kafka集群
a1.channels.c1.type = org.apache.flume.channel.kafka.KafkaChannel       # 使用KafkaChannel省去sink阶段
a1.channels.c1.kafka.bootstrap.servers = cdh1:9092,cdh2:9092,cdh3:9092  # kafka地址
a1.channels.c1.kafka.topic = start                                      # 指定channel对应的topic(需提前创建)
a1.channels.c1.parseAsFlumeEvent = false    # 将消息封装成ProducerRecord发送时是否按照flume的event结构解析(即是否保留header)

a1.channels.c2.type = org.apache.flume.channel.kafka.KafkaChannel
a1.channels.c2.kafka.bootstrap.servers = cdh1:9092,cdh2:9092,cdh3:9092
a1.channels.c2.kafka.topic = event
a1.channels.c2.parseAsFlumeEvent = false

# 先启动kafka
[root@cdh1 ~]$ kafka-server-start.sh -daemon ../config/server.properties
[root@cdh1 ~]$ kafka-topics.sh --create --zookeeper cdh1:2181 --topic start --partitions 1 --replication-factor 1
[root@cdh1 ~]$ kafka-console-consumer.sh --bootstrap-server cdh1:9092 --from-beginning --topic start
# 再启动flume-ng
[root@cdh1 ~]$ nohup flume-ng agent -c conf/ -f conf/nginx-kafka.conf -n a1 -Dflume.root.logger=info,console > logs/flume.log 2>&1 &
# 然后启动log,消费者能收到数据说明ok
[root@cdh1 ~]$ nohup java -cp mock-1.0-SNAPSHOT-jar-with-dependencies.jar app.AppMain > /dev/null 2>&1 &
# kafka挂了flume如何补数据？
# nginx落地的日志文件还在,从文件中grep时间戳截取丢失数据写到临时文件,flume再写一个conf单独监听这个临时文件写入topic就行
```

#### nginx-hdfs.conf
```shell script
# 命名agent组件
a1.sources = r1
a1.channels = c1
a1.sinks = k1

# 配置source
a1.sources.r1.type = TAILDIR
a1.sources.r1.positionFile = ${flume}/position/offline_position.json  # 记录采集位置的json文件
a1.sources.r1.filegroups = f1 
a1.sources.r1.filegroups.f1 = /data1/logstash/logs/.*.txt  # 监控的文件,可以是单个文件,也可以是正则匹配多个文件
# 拦截器(可选)
a1.sources.r1.interceptors = regex
a1.sources.r1.interceptors.regex.type=REGEX_FILTER
a1.sources.r1.interceptors.regex.regex=^.+uid=.+&uname=.+spuId=.+$
a1.sources.r1.interceptors.regex.excludeEvents=false
# 自定义拦截器(可选)
a1.sources.r1.interceptors = i1
a1.sources.r1.interceptors.i1.type = com.okccc.interceptor.InterceptorDemo$Builder

# memory channel
a1.channels.c1.type = memory
a1.channels.c1.capacity = 1000             # channel最多存储1000个event
a1.channels.c1.transactionCapacity = 100   # channel收集到100个event才会提交事务
# file channel
a1.channels.c1.type = file
a1.channels.c1.checkpointDir = ${flume}/cp     # 存储checkpoint的文件
a1.channels.c1.dataDirs = ${flume}/data        # 存储日志的目录列表,逗号分隔,优化：指向不同硬盘的多个路径提高flume吞吐量
a1.channels.c1.maxFileSize = 2146435071        # 单个log文件的最大字节数
a1.channels.c1.capacity = 1000000              # channel的最大容量
a1.channels.c1.keep-alive = 6                  # 等待put操作的超时时间(秒)

# 配置sink
a1.sinks.k1.type = hdfs
a1.sinks.k1.hdfs.path = hdfs://bigdata/user/flume/%Y-%m-%d  # hdfs路径
a1.sinks.k1.hdfs.filePrefix = log              # 指定文件前缀
a1.sinks.k1.hdfs.useLocalTimeStamp = true      # 是否使用本地时间戳代替event header的时间戳
a1.sinks.k1.hdfs.batchSize = 1000              # 有1000个event写入文件就flush到hdfs
# 数据压缩(可选)
a1.sinks.k1.hdfs.fileType = CompressedStream   # 文件类型,SequenceFile(默认)/DataStream(常用)/CompressedStream(压缩)
a1.sinks.k1.hdfs.codeC = lzop                  # 指定压缩方式
# 控制hdfs文件大小,默认参数会生成大量小文件
a1.sinks.k1.hdfs.rollInterval = 3600           # tmp文件达到3600秒会滚动生成正式文件
a1.sinks.k1.hdfs.rollSize = 10737418420        # tmp文件达到10G会滚动生成正式文件
a1.sinks.k1.hdfs.rollCount = 0                 # tmp文件的滚动与写入的event数量无关
a1.sinks.k1.hdfs.roundUnit = second            # 滚动时间单位
a1.sinks.k1.hdfs.roundValue = 60               # 60秒滚动一次tmp文件

# 给source和sink绑定channel
a1.sources.r1.channels = c1  # 一个source可以接多个channel
a1.sinks.k1.channel = c1     # 一个sink只能接一个channel

# 启动flume-ng
[root@cdh1 ~]$ flume-ng agent -c conf -f conf/nginx-hdfs.conf -n a1 -Dflume.root.logger=info,console
# 往监测文件写数据
[root@cdh1 ~]$ for i in {1..10000}; do echo "hello spark ${i}" >> test.log; echo ${i}; sleep 0.01; done
```

#### netcat-console.conf
```shell script
# 命名agent组件
a1.sources = r1
a1.sinks = k1
a1.channels = c1
# 配置source
a1.sources.r1.type = netcat
a1.sources.r1.bind = localhost
a1.sources.r1.port = 44444
# 配置sink
a1.sinks.k1.type = logger
# 配置channel
a1.channels.c1.type = memory
a1.channels.c1.capacity = 1000
a1.channels.c1.transactionCapacity = 100
# 将source和sink绑定到channel
a1.sources.r1.channels = c1
a1.sinks.k1.channel = c1

# 启动flume-ng
[root@cdh1 ~]$ flume-ng agent -c conf -f conf/netcat-console.conf -n a1 -Dflume.root.logger=info,console  # 输出到控制台
Event: { headers:{} body: 6A 61 76 61    java }
# 往监听端口写数据
[root@cdh1 ~]$ nc localhost 44444
java
```

### ganglia
```shell script
# flume可以在启动时添加http监控端口 -Dflume.monitoring.type=http -Dflume.monitoring.port=9999,也可以借助专业监控工具ganglia
http://localhost:9999/metrics
# 安装http服务于php
[root@cdh1 ~]$ sudo yum -y install httpd php
# 安装依赖
[root@cdh1 ~]$ sudo yum -y install rrdtool perl-rrdtool rrdtool-devel apr-devel
# 安装ganglia
[root@cdh1 ~]$ sudo rpm -Uvh http://dl.fedoraproject.org/pub/epel/6/x86_64/epel-release-6-8.noarch.rpm
[root@cdh1 ~]$ sudo yum -y install ganglia-gmetad ganglia-web ganglia-gmond
# ganglia由三个部分组成
# gmond(ganglia monitoring daemon): 是一种轻量级服务,安装在每个收集数据的节点,可以收集CPU/内存/磁盘/网络/活跃进程等系统指标
# gmetad(ganglia meta daemon): 整合所有信息,并将其以RRD格式存储至磁盘
# gweb(ganglia web): 可视化工具,以图表方式展现集群的运行状态下收集的多种不同指标数据
# 修改配置文件
[root@cdh1 ~]$ sudo vim /etc/httpd/conf.d/ganglia.conf
# Ganglia monitoring system php web frontend
Alias /ganglia /usr/share/ganglia
<Location /ganglia>
  Order deny,allow
  #Deny from all
  Allow from all
  # Allow from 127.0.0.1
  # Allow from ::1
  # Allow from .example.com
</Location>
[root@cdh1 ~]$ sudo vim /etc/ganglia/gmetad.conf
data_source "cdh01" localhost
[root@cdh1 ~]$ sudo vim /etc/ganglia/gmond.conf
cluster {
  name = "cdh01"
  owner = "unspecified"
  latlong = "unspecified"
  url = "unspecified"
}
udp_send_channel {
  #bind_hostname = yes # Highly recommended, soon to be default.
                       # This option tells gmond to use a source address
                       # that resolves to the machine's hostname.  Without
                       # this, the metrics may appear to come from any
                       # interface and the DNS names associated with
                       # those IPs will be used to create the RRDs.
  # mcast_join = 239.2.11.71
  host = localhost
  port = 8649
  ttl = 1
}
udp_recv_channel {
  # mcast_join = 239.2.11.71
  port = 8649
  bind = localhost
  retry_bind = true
  # Size of the UDP buffer. If you are handling lots of metrics you really
  # should bump it up to e.g. 10MB or even higher.
  # buffer = 10485760
}
[root@cdh1 ~]$ sudo vim /etc/selinux/config
SELINUX=disabled  # 需重启生效,也可以临时生效sudo setenforce 0
# 启动ganglia
[root@cdh1 ~]$ sudo service httpd start
[root@cdh1 ~]$ sudo service gmetad start
[root@cdh1 ~]$ sudo service gmond start
# web页面
http://localhost/ganglia
# 如果出现权限不足错误
[root@cdh1 ~]$ sudo chmod -R 777 /var/lib/ganglia
# 将ganglia添加到flume的配置
[root@cdh1 ~]$ vim flume-env.sh
JAVA_OPTS="-Dflume.monitoring.type=ganglia -Dflume.monitoring.hosts=localhost:8649 -Xms100m -Xmx200m"
# 启动flume时添加ganglia
[root@cdh1 ~]$ flume-ng ... -Dflume.monitoring.type=ganglia -Dflume.monitoring.hosts=localhost:8649
```

|          字段           |          解释           |     |     |
|:---------------------:|:-----------------------:|:---:|:---:|
| EventPutAttemptCount  | source尝试写入channel的事件总数量 |     |     |
| EventPutSuccessCount  |  成功写入channel且提交的事件总数量   |     |     |
| EventTakeAttemptCount | sink尝试从channel拉取事件的总数量  |     |     |
| EventTakeSuccessCount |     sink成功读取的事件的总数量     |     |     |
|       StartTime       |    channel启动的时间(ms)     |     |     |
|       StopTime        |    channel停止的时间(ms)     |     |     |
|      ChannelSize      |    目前channel中事件的总数量     |     |     |
| ChannelFillPercentage |      channel占用百分比       |     |     |
|    ChannelCapacity    |       channel的容量        |     |     |
