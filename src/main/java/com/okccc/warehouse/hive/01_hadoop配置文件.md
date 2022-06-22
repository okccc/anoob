### core-site.xml
```xml
<configuration>
    <!-- 指定hdfs的nameservice为ns1 -->
    <property>
        <name>fs.defaultFS</name>
        <value>hdfs://ns1/</value>
    </property>
    <!-- 指定hadoop临时目录 -->
    <property>
        <name>hadoop.tmp.dir</name>
        <value>/opt/module/hadoop-2.7.2/tmp</value>
    </property>
    <!-- 指定zookeeper地址 -->
    <property>
        <name>ha.zookeeper.quorum</name>
        <value>cdh1:2181,cdh2:2181,cdh3:2181</value>
    </property>
    <!-- 文件删除后保留时长(分钟),默认0表示关闭回收站,安全起见还是打开防止误删数据 -->
    <property>
        <name>fs.trash.interval</name>
        <value>1440</value>
    </property>
    <!-- 支持lzo压缩,将hadoop-lzo-0.4.20.jar拷贝到/opt/module/hadoop-3.1.3/share/hadoop/common -->
    <property>
        <name>io.compression.codecs</name>
        <value>org.apache.hadoop.io.compress.GzipCodec,
            org.apache.hadoop.io.compress.DefaultCodec,
            org.apache.hadoop.io.compress.BZip2Codec,
            org.apache.hadoop.io.compress.SnappyCodec,
            com.hadoop.compression.lzo.LzoCodec,
            com.hadoop.compression.lzo.LzopCodec
        </value>
    </property>
    <property>
        <name>io.compression.codec.lzo.class</name>
        <value>com.hadoop.compression.lzo.LzoCodec</value>
    </property>
    <!-- 配置代理用户操作hadoop主机、用户、用户组 -->
    <property>
        <name>hadoop.proxyuser.okc.hosts</name>
        <value>*</value>
    </property>
    <property>
        <name>hadoop.proxyuser.okc.groups</name>
        <value>*</value>
    </property>
</configuration>
```

### hdfs-site.xml
```xml
<configuration>
    <!--指定hdfs的nameservice为ns1,需要和core-site.xml中的保持一致 -->
    <property>
        <name>dfs.nameservices</name>
        <value>ns1</value>
    </property>
    <!-- ns1下面有两个NameNode,分别是nn1,nn2 -->
    <property>
        <name>dfs.ha.namenodes.ns1</name>
        <value>nn1,nn2</value>
    </property>
    <!-- nn1的RPC通信地址 -->
    <property>
        <name>dfs.namenode.rpc-address.ns1.nn1</name>
        <value>cdh1:9000</value>
    </property>
    <!-- nn1的http通信地址 -->
    <property>
        <name>dfs.namenode.http-address.ns1.nn1</name>
        <value>cdh1:50070</value>
    </property>
    <!-- nn2的RPC通信地址 -->
    <property>
        <name>dfs.namenode.rpc-address.ns1.nn2</name>
        <value>cdh2:9000</value>
    </property>
    <!-- nn2的http通信地址 -->
    <property>
        <name>dfs.namenode.http-address.ns1.nn2</name>
        <value>cdh2:50070</value>
    </property>
    <!-- 指定NameNode的元数据在JournalNode上的存放位置 -->
    <property>
        <name>dfs.namenode.shared.edits.dir</name>
        <value>qjournal://cdh1:8485;cdh2:8485;cdh3:8485/ns1</value>
    </property>
    <!-- 指定JournalNode在本地磁盘存放数据的位置 -->
    <property>
        <name>dfs.journalnode.edits.dir</name>
        <value>/opt/module/hadoop-2.7.2/journaldata</value>
    </property>
    <!-- 开启NameNode失败自动切换 -->
    <property>
        <name>dfs.ha.automatic-failover.enabled</name>
        <value>true</value>
    </property>
    <!-- 配置失败自动切换实现方式 -->
    <property>
        <name>dfs.client.failover.proxy.provider.ns1</name>
        <value>org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider</value>
    </property>
    <!-- 配置隔离机制方法,防止脑裂SB(split-brain)-->
    <property>
        <name>dfs.ha.fencing.methods</name>
        <value>
            sshfence
            shell(/bin/true)
        </value>
    </property> 
    <!-- 使用sshfence隔离机制时需要ssh免登陆 -->  
    <property>
        <name>dfs.ha.fencing.ssh.private-key-files</name>
        <value>/opt/module/hadoop-2.7.2/.ssh/id_rsa</value>
    </property>
    <!-- 配置sshfence隔离机制超时时间 -->  
    <property>
        <name>dfs.ha.fencing.ssh.connect-timeout</name>
        <value>30000</value>  
    </property>
    <!-- 指定hdfs副本数量 -->
    <property>
        <name>dfs.replication</name>
        <value>3</value>
    </property>
</configuration>
```

### hive-site.xml
```xml
<configuration>
	<property>
	  <name>javax.jdo.option.ConnectionURL</name>
	  <value>jdbc:mysql://cdh1:3306/metastore?createDatabaseIfNotExist=true</value>
	  <description>JDBC connect string for a JDBC metastore</description>
	</property>
	<property>
	  <name>javax.jdo.option.ConnectionDriverName</name>
	  <value>com.mysql.jdbc.Driver</value>
	  <description>Driver class name for a JDBC metastore</description>
	</property>
	<property>
	  <name>javax.jdo.option.ConnectionUserName</name>
	  <value>root</value>
	  <description>username to use against metastore database</description>
	</property>
	<property>
	  <name>javax.jdo.option.ConnectionPassword</name>
	  <value>root</value>
	  <description>password to use against metastore database</description>
	</property>
	<property>
      <name>hive.server2.thrift.port</name>
      <value>10000</value>
      <description>HiveServer2 listen port</description>
    </property>
    <property>
        <name>spark.yarn.jars</name>
        <value>hdfs://cdh1:9000/spark-jars/*</value>
    </property>
    <property>
        <name>hive.execution.engine</name>
        <value>spark</value>
    </property>
</configuration>
```

### mapred-site.xml
```xml
<configuration>
    <!-- 指定mr框架为yarn方式 -->  
    <property>
        <name>mapreduce.framework.name</name>
        <value>yarn</value>
    </property>
    <!-- mr历史服务器端地址 -->
    <property>
        <name>mapreduce.jobhistory.address</name>
        <value>cdh1:10020</value>
    </property>
    <!-- mr历史服务器web端地址 -->
    <property>
        <name>mapreduce.jobhistory.webapp.address</name>
        <value>cdh1:19888</value>
    </property>
    <!-- 开启JVM重用 -->
    <property>
        <name>mapred.job.reuse.jvm.num.tasks</name>
        <value>10</value>
    </property>
</configuration>
```

### yarn-site.xml
```xml
<configuration>
    <!-- 开启RM高可用 -->  
    <property>
       <name>yarn.resourcemanager.ha.enabled</name>
       <value>true</value>
    </property>
    <!-- 指定RM的cluster id -->  
    <property>
       <name>yarn.resourcemanager.cluster-id</name>
       <value>yrc</value>
    </property>
    <!-- 指定RM的名字 -->  
    <property>
       <name>yarn.resourcemanager.ha.rm-ids</name>
       <value>rm1,rm2</value>
    </property>
    <!-- 分别指定RM的地址 -->  
    <property>
       <name>yarn.resourcemanager.hostname.rm1</name>
       <value>cdh1</value>
    </property>
    <property>
       <name>yarn.resourcemanager.hostname.rm2</name>
       <value>cdh2</value>
    </property>
    <!-- 指定zk集群地址 -->  
    <property>
       <name>yarn.resourcemanager.zk-address</name>
       <value>cdh1:2181,cdh2:2181,cdh3:2181</value>
    </property>
    <!-- Reducer获取数据的方式 -->
    <property>
       <name>yarn.nodemanager.aux-services</name>
       <value>mapreduce_shuffle</value>
    </property>
    <!-- 使用日志聚集功能 -->
    <property>
        <name>yarn.log-aggregation-enable</name>
        <value>true</value>
    </property>
    <!-- 日志保留时间设置7天 -->
    <property>
        <name>yarn.log-aggregation.retain-seconds</name>
        <value>604800</value>
    </property>
    <!-- spark和mr历史日志路径 -->
    <property>
        <name>yarn.log.server.url</name>
        <value>http://cdh1:19888/jobhistory/logs</value>
    </property>
    
    <!-- 下面两项配置是防止spark运行时内存不足导致报错-->
    <!--是否启动一个线程检查每个任务正使用的物理内存量,如果任务超出分配值则直接将其杀掉,默认true -->
    <property>
        <name>yarn.nodemanager.pmem-check-enabled</name>
        <value>false</value>
    </property>
    <!--是否启动一个线程检查每个任务正使用的虚拟内存量,如果任务超出分配值则直接将其杀掉,默认true -->
    <property>
        <name>yarn.nodemanager.vmem-check-enabled</name>
        <value>false</value>
    </property>
</configuration>
```

### capacity-scheduler.xml
```xml
<configuration>
    <!-- 每个资源队列Application Master最多可使用的资源比例,防止占用过多导致任务无法执行 -->
    <property>
        <name>yarn.scheduler.capacity.maximum-am-resource-percent</name>
        <value>0.8</value>
    </property>
</configuration>
```