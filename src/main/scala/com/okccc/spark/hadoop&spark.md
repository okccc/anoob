### hadoop2.7
```shell script
# 解压安装包
[root@cdh1 opt]$ tar -xvf hadoop-2.7.2.tar.gz -C /opt/module
[root@cdh1 opt]$ tar -xvf apache-hive-1.2.1-bin.tar.gz -C /opt/module
# bin目录是一些原始命令  hadoop/hdfs/yarn/mapred/run-eample/spark-shell/spark-submit/spark-sql
# sbin目录是一些服务(启动/停止)命令  start/stop
# 添加到环境变量
[root@cdh1 ~]$ vim /etc/profile
export HADOOP_HOME=/opt/module/hadoop-2.7.2
export PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin
export HIVE_HOME=/opt/module/hive-1.2.1
export PATH=$PATH:$HIVE_HOME/bin:$HIVE_HOME/sbin
# 修改hadoop-env
[root@cdh1 hadoop]$ vim hadoop-env.sh
export JAVA_HOME=/usr/java/jdk1.8.0_181-cloudera
# 修改slaves
[root@cdh1 hadoop]$ vim slaves
cdh1
cdh2
cdh3
# 修改hive-env
[root@cdh1 conf]$ vim hive-env.sh
export HADOOP_HOME=/opt/module/hadoop-2.7.2
export HIVE_CONF_DIR=/opt/module/hive-1.2.1/conf
# 配置hive元数据到mysql,在hive的conf目录新增hive-site.xml
[root@cdh1 ~]$ cp /usr/share/java/mysql-connector-java-5.1.46.jar /opt/module/hive-1.2.1/lib/
# 修改完全部配置文件后拷贝到其它节点
[root@cdh1 module]$ scp -r hadoop-2.7.2/ cdh2:/opt/module/
[root@cdh1 module]$ scp -r hadoop-2.7.2/ cdh3:/opt/module/
# 一键分发脚本
[root@cdh1 ~]$ cd /usr/bin & vim xsync & chmod +x xsync
#!/bin/bash
# 获取参数
if [ $# == 1 ]; then
    path=$1  # 文件要写全路径
else
    echo "Usage: <file>"
    exit
fi
# 获取文件(夹)绝对路径
file=$(basename ${path})
# 获取文件(夹)所在目录
dir=$(dirname ${path})
# 获取当前用户
user=$(whoami)
# 遍历节点分发
for ((i=2;i<=10;i++)); 
do
    echo "=============== cdh${i} ==============="
    rsync -rv ${dir}/${file} ${user}@cdh${i}:${dir}
done
```

### spark2.1
```shell script
# 解压安装包
[root@cdh1 ~]$ tar -xvf spark-2.1.1-bin-hadoop2.7.tgz -C /opt/module
# 添加到环境变量
[root@cdh1 ~]$ vim /etc/profile
export SPARK_HOME=/opt/module/spark-2.1.1-bin-hadoop2.7
export PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin

# This file is sourced when running various Spark programs.
[root@cdh1 ~]$ vim spark-env.sh
# java环境
JAVA_HOME=/usr/java/jdk1.8.0_181-cloudera
# on yarn模式,只能从hdfs读数据,通过yarn管理资源和任务监控(8088端口)
HADOOP_CONF_DIR=/opt/module/hadoop-2.7.2/etc/hadoop
SPARK_DRIVER_MEMORY=512M
SPARK_EXECUTOR_CORES=1
SPARK_EXECUTOR_INSTANCES=2
SPARK_EXECUTOR_MEMORY=512M
SPARK_HISTORY_OPTS="-Dspark.history.fs.logDirectory=hdfs://cdh1:9000/user/spark/history"
# standalone模式,只能从本地读数据,spark自己管理资源和任务监控(8080端口)
SPARK_MASTER_HOST=cdh1
SPARK_MASTER_PORT=7077
SPARK_WORKER_CORES=1
SPARK_WORKER_INSTANCES=1
SPARK_WORKER_MEMORY=512M
SPARK_DAEMON_JAVA_OPTS="
-Dspark.deploy.recoveryMode=ZOOKEEPER
-Dspark.deploy.zookeeper.url=cdh1,cdh2,cdh3
-Dspark.deploy.zookeeper.dir=/spark"
SPARK_HISTORY_OPTS="-Dspark.history.fs.logDirectory=hdfs://cdh1:9000/user/spark/history"

# Default system properties included when running spark-submit.
[root@cdh1 ~]$ vim spark-defaults.conf
spark.eventLog.enabled     true
# 日志目录需提前创建好
spark.eventLog.dir         hdfs://cdh1:9000/user/spark/history

# 拷贝到其它节点
[root@cdh1 opt]$ scp -r spark-2.1.1/ cdh2:/opt/module  
[root@cdh1 opt]$ scp -r spark-2.1.1/ cdh3:/opt/module

# 一键启动/关闭集群
[root@cdh1 ~]$ start-cluster

# 本地测试(日志格式：local-timestamp)
[root@cdh1 ~]$ run-example org.apache.spark.examples.SparkPi
[root@cdh1 ~]$ run-example org.apache.spark.examples.streaming.NetworkWordCount localhost 9999

# 提交任务到yarn(日志格式：application_timestamp_0001)
[root@cdh1 ~]$ spark-submit --class org.apache.spark.examples.SparkPi --master yarn --deploy-mode client ./examples/jars/spark-examples_2.11-2.1.1.jar
[root@cdh1 ~]$ spark-submit --class org.apache.spark.examples.mllib.LinearRegression --master yarn --deploy-mode cluster --jars ./examples/jars/*  hdfs://cdh1:9000/data/sample_linear_regression_data.txt
# 参数解析
--class <main-class>           # application的启动类
--master <master-url>          # master地址,local/yarn
--deploy-mode <deploy-mode>    # 部署模式,client(默认)/cluster
--file                         # 配置文件
--conf <key>=<value>           # 配置属性
--verbose                      # 输出额外的debug信息
<application-jar>              # 包含application和dependencies的jar包路径
[application-arguments]        # 传给main方法的参数
--driver-memory 512M           # driver可用内存,默认1G
--executor-memory 512M         # executor可用内存,默认1G
```

### cluster
```shell script
# 手动启动集群
# 启动zk
[root@cdh1 ~]$ zkServer.sh start
# 格式化namenode
[root@cdh1 ~]$ hdfs namenode -format
# 把tmp拷到nn2下面
[root@cdh1 hadoop-2.7.2]$ scp -r hadoop-2.7.2/tmp cdh2:/opt/module/hadoop-2.7.2
# 格式化ZKFC
[root@cdh1 ~]$ hdfs zkfc -formatZK
# 启动hdfs
[root@cdh1 ~]$ start-dfs.sh
# 启动yarn
[root@cdh1 ~]$ start-yarn.sh
# cdh2要手动启
[root@cdh2 ~]$ yarn-daemon.sh start resourcemanager
# 启动mr历史日志
[root@cdh1 ~]$ mr-jobhistory-daemon.sh start historyserver

# 一键启动集群
[root@cdh1 ~]$ cd /usr/bin & vim start-cluster & chmod +x start-cluster
#!/bin/bash
# 启动zookeeper
for i in cdh1 cdh2 cdh3
do
    echo ==================== ${i} ====================
    # ssh后面的命令是未登录执行,需要先刷新系统环境变量
    ssh ${i} "source /etc/profile && zkServer.sh start"
    echo ${?}
done
# 启动hdfs
start-dfs.sh
# 启动yarn
start-yarn.sh
# cdh2要手动启动
ssh cdh2 "source /etc/profile && yarn-daemon.sh start resourcemanager"
# 开启mr的jobhistory
mr-jobhistory-daemon.sh start historyserver
# 启动spark
/opt/module/spark-2.1.1-bin-hadoop2.7/sbin/start-all.sh
# 开启spark的history-server
/opt/module/spark-2.1.1-bin-hadoop2.7/sbin/start-history-server.sh

# 在所有节点执行某个命令
[root@cdh1 ~]$ cd /usr/bin & vim xcall.sh & chmod +x xcall.sh
[root@cdh1 ~]$ xcall.sh jps | xcall.sh "cd /tmp/logs && rm -rf *"
#!/bin/bash
for i in cdh1 cdh2 cdh3
do
    echo ==================== ${i} ====================
    # ssh后面的命令是未登录执行,需要先刷新系统环境变量,$*表示输入的所有参数
    ssh ${i} "source /etc/profile && $*"
done

# 一键关闭集群
[root@cdh1 ~]$ cd /usr/bin & vim stop-cluster & chmod +x stop-cluster
#!/bin/bash
# 关闭kafka
for i in cdh1 cdh2 cdh3
do
    echo ==================== ${i} ====================
    # ssh后面的命令是未登录执行,需要先刷新系统环境变量
    ssh ${i} "source /etc/profile && cd /opt/module/kafka_2.11-0.11.0.2/bin && kafka-server-stop.sh"
    echo ${?}
done
# 关闭hdfs
stop-dfs.sh
# 关闭yarn
stop-yarn.sh
# cdh2要手动关闭
ssh cdh2 "source /etc/profile && yarn-daemon.sh stop resourcemanager"
# 关闭mr的jobhistory
mr-jobhistory-daemon.sh stop historyserver
# 关闭spark
/opt/module/spark-2.1.1-bin-hadoop2.7/sbin/stop-all.sh
# 开启spark的history-server
/opt/module/spark-2.1.1-bin-hadoop2.7/sbin/stop-history-server.sh
# 关闭zookeeper
for i in cdh1 cdh2 cdh3
do
    echo ==================== ${i} ====================
    # ssh后面的命令是未登录执行,需要先刷新系统环境变量
    ssh ${i} "source /etc/profile && zkServer.sh stop"
    echo ${?}
done
```

### hdfs
```shell script
# 集群命令
[root@cdh1 ~]$ /opt/cloudera/parcels/CDH/bin目录存放了hadoop/hdfs/yarn/zookeeper等一系列集群命令
# 查看hadoop版本
[root@cdh1 ~]$ hadoop version
# 查看hdfs的各节点状态信息
[root@cdh1 ~]$ hdfs dfsadmin -report
# 刷新节点
[root@cdh1 ~]$ hdfs dfsadmin -refreshNodes
# 查看hdfs是否是安全模式(read-only)
[root@cdh1 ~]$ hdfs dfsadmin -safemode [enter/leave]
# 获取namenode节点的HA状态
[root@cdh1 ~]$ hdfs haadmin -getServiceState nn1
# 切换状态(将nn2变为active)
[root@cdh1 ~]$ hdfs haadmin -failover nn1 nn2
# 检测节点健康状况
[root@cdh1 ~]$ hdfs haadmin -checkHealth nn1
# 单独启动一个zkfc进程
[root@cdh1 ~]$ hadoop-daemon.sh start zkfc

# hdfs命令
# 修改目录所属用户(组)
[root@cdh1 ~]$ hdfs dfs -chown deploy /user/flume
[root@cdh1 ~]$ hdfs dfs -chown deploy:supergroup /user/flume
# 查看文件列表以时间倒序排序
[root@cdh1 ~]$ hdfs dfs -ls -t -r /
# 查看文件内容
[root@cdh1 ~]$ hdfs dfs -cat <hdfs文件>
# 上传下载
[root@cdh1 ~]$ hdfs dfs -put <local> <hdfs>
[root@cdh1 ~]$ hdfs dfs -get <hdfs> <local>
# 合并小文件(所有文件加起来不到128M那种,先合并到本地再传回hdfs)
[root@cdh1 ~]$ hdfs dfs -getmerge <hdfs> <local> & hdfs dfs -put <local> <hdfs>
# 创建文件夹(级联)
[root@cdh1 ~]$ hdfs dfs -mkdir -p <path>  
# 设置/取消该文件夹上传文件数限制
[root@cdh1 ~]$ hdfs dfsadmin -setQuota | -clrQuota 3 /test
# 设置/取消该文件夹大小
[root@cdh1 ~]$ hdfs dfsadmin -setSpaceQuota | -clrSpaceQuota 1g /test
# 在hdfs上复制cp/移动mv文件
[root@cdh1 ~]$ hdfs dfs -mv /aaa/* /bbb/  
# 删除hdfs文件
[root@cdh1 ~]$ hdfs dfs -rm /aaa/angela.txt  
# 删除hdfs文件夹
[root@cdh1 ~]$ hdfs dfs -rm -r /aaa  
# hdfs总空间大小
[root@cdh1 ~]$ hdfs dfs -df -h /
Filesystem    Size   Used  Available  Use%
hdfs://ns1  93.5 T  66.2 T   20.4 T    71%
# hdfs某个目录大小
[root@cdh1 ~]$ hdfs dfs -du -s -h /user/hive/warehouse
31.6T  64.7T  /user/hive/warehouse  # 说明是2个副本,小文件也会被当成128m切块大小,所以不是刚刚好2倍关系
# hdfs某个目录下所有文件大小
[root@cdh1 ~]$ hdfs dfs -du -h /user/hive/warehouse
5.2 G    12.7 G   /user/hive/warehouse/dm.db
104.5 G  240.2 G  /user/hive/warehouse/dw.db
320.7 G  671.3 G  /user/hive/warehouse/ods.db
# hdfs文件大小排序
[root@cdh1 ~]$ hdfs dfs -du /user/hive/warehouse/*.db | awk '{print int($1/1024/1024/1024) "G",int($2/1024/1024/1024) "G",$3}' OFS="\t" | awk '$1 > 1G' | sort -nr
72G  160G  /user/hive/warehouse/ods.db/debit_detail
54G  110G  /user/hive/warehouse/dw.db/urge_record
51G  105G  /user/hive/warehouse/dm.db/pay_repayment_detail

# yarn命令
# 查看yarn应用列表
[root@cdh1 ~]$ yarn application -list
# 查看yarn应用状态
[root@cdh1 ~]$ yarn application -status application_id
# 杀掉单个yarn应用
[root@cdh1 ~]$ yarn application -kill application_id
# 杀掉全部yarn应用
[root@cdh1 ~]$ for i in `yarn application -list | awk '{print $1}' | grep application_`; do yarn application -kill $i; done
# 批量杀掉yarn应用
[root@cdh1 ~]$ for i in `yarn application -list | grep ... | awk '{print $1}' | grep application_`; do yarn application -kill $i; done
# linux批量杀进程
[root@cdh1 ~]$ ps -aux | grep ... | awk '{print $2}' | xargs kill -9

# trash
# 类似linux系统回收站,hdfs会为每个用户创建一个回收站目录/user/当前用户/.Trash/
# hdfs内部实现是在namenode开启一个后台线程Emptier专门管理和监控系统回收站,将超过生命周期的数据删除并释放关联的数据块
# 防止误删数据,删除的文件不会立即清除而是转移到/user/当前用户/.Trash/Current并保留一段时间(fs.trash.interval),所以hdfs磁盘空间不会立即增加
# 手动清空回收站
[root@cdh1 ~]$ hdfs dfs -expunge  # 会将当前目录/user/用户名/.Trash/current重命名为/user/用户名/.Trash/yyMMddHHmmSS,再次执行就彻底删除了

# web页面监控
http://cdh1:50070    # active/standby
http://cdh1:10002    # hive
http://cdh1:8088     # yarn  
http://cdh1:19888    # mr job history
http://cdh1:8080     # master(如果8080端口被占用,MasterUI会尝试8081端口,WorkerUI会顺延到8082端口,可通过spark启动日志查看)
http://cdh1:4040     # spark-shell
http://cdh1:18080    # spark history server
```

### hue
```shell script
# 解决hue10万行下载限制
# 以管理员账号admin登录查看配置信息
Hue Administration - Configuration - beeswax - download_cell_limit(默认100000行*100列=10000000)
[root@master1 ~]$ find / -name beeswax
/opt/cloudera/parcels/CDH-5.14.2-1.cdh5.14.2.p0.3/lib/hue/apps/beeswax
[root@master1 ~]$ vim /opt/cloudera/parcels/CDH-5.14.2-1.cdh5.14.2.p0.3/lib/hue/apps/beeswax/src/beeswax/conf.py
DOWNLOAD_CELL_LIMIT = Config(
  key='download_cell_limit',
  default=100000000,
# hue查询结果字段带有表名
CM - Hive - 配置 - hiveserver2 - hive-site.xml的HiveServer2高级配置代码段(安全阀) - hive.resultset.use.unique.column.names=false
```

### spark-submit
```shell script
#!/bin/bash

if [ "$1" == "event" ];then
    className=RealTimeEvent
elif [ "$1" == "lesson" ];then
    className=RealTimeLesson
elif [ "$1" == "token" ];then
    className=RealTimeToken
fi

project=RealTimeEvent
class=com.okccc.realtime.ods.${className}
path=/data1/projects-app/realtime/${project}

# 先杀掉原进程
yarn application -list | grep ${class} | awk '{system(" yarn application -kill "$1)}'

# 启动脚本,将配置信息放到工程外的独立文件,这样往prod/dev等不同环境写数据只需修改配置文件不用重新编译代码
nohup /opt/spark/bin/spark-submit \
--class ${class} \
--files ${path}/conf/config.properties,${path}/conf/log4j.properties,${path}/conf/hive-site.xml \
--master yarn \
--deploy-mode cluster \
--driver-memory 6g \
--num-executors 10 \
--executor-cores 2 \
--executor-memory 4g \
--queue root.default \
${path}/lib/${project}-1.0-SNAPSHOT.jar 10 >> ${path}/log/${project}.log &

# 常用配置
--conf "spark.sql.warehouse.dir=hdfs://company/data/hive/warehouse"  \
--conf "spark.default.parallelism=200"  \  # 每个stage默认task数=num-executors * executor-cores,推荐设置为CPU总core数的2~3倍
--conf "spark.sql.auto.repartition=true"  \  # 自动重新分区,避免分区数过多导致单分区数据过少,每个task运算分区时要频繁调度消耗过多时间
--conf "spark.sql.shuffle.partitions=400"  \  # 提高shuffle并行度,默认200,增加shuffle read task数量让每个task处理数据变少时间缩短
# 开启动态资源分配
--conf "spark.dynamicAllocation.enabled=true"  \
--conf "spark.dynamicAllocation.maxExecutors=200"  \
--conf "spark.dynamicAllocation.minExecutors=50"  \
# shuffle调优
--conf "spark.shuffle.file.buffer=64" \
--conf "spark.reducer.maxSizeInFlight=96" \
--conf "spark.shuffle.io.maxRetries=6" \
--conf "spark.shuffle.io.retryWait=60s" \
--conf "spark.shuffle.sort.bypassMergeThreshold=400" \
# jvm调优
--conf "spark.storage.memoryFraction=0.4" \
--conf "spark.yarn.executor.memoryOverhead=2048" \
--conf "spark.core.connection.ack.wait.timeout=300" \
# 开启日志
--conf spark.eventLog.enabled=true \
--conf spark.eventLog.dir=hdfs://test/sparklogs \
--conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.properties -XX:+UseG1GC -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps" \
--conf "spark.streaming.kafka.maxRatePerPartition=1000000" \
```