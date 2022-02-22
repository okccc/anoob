### hbase
```shell script
# hbase是分布式、可扩展、支持海量数据存储的K-V类型NoSQL数据库
[root@cdh1 ~]$ wget http://apache.claz.org/hbase/2.3.7/hbase-2.3.7-bin.tar.gz
# 安装
[root@cdh1 ~]$ tar -xvf hbase-2.0.5-bin.tar.gz -C /opt/module
# 修改配置文件
[root@cdh1 ~]$ vim hbase-env.sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_211.jdk/Contents/Home
export HBASE_MANAGES_ZK=false  # 用集群的zk而不是hbase自带的zk
[root@cdh1 ~]$ vim hbase-site.xml
<configuration>
    <property>
        <name>hbase.rootdir</name>
        <! 端口号要和core-site.xml保持一致,不然HMaster进程会挂掉 >
        <value>hdfs://localhost:9000/hbase</value>
    </property>
    <property>
        <name>hbase.cluster.distributed</name>
        <value>true</value>
    </property>
    <property>
        <name>hbase.zookeeper.quorum</name>
        <value>localhost</value>
    </property>
    <property>
        <name>hbase.unsafe.stream.capability.enforce</name>
        <value>false</value>
    </property>
    <property>
        <name>hbase.wal.provider</name>
        <value>filesystem</value>
    </property>
    <! phoenix不能直接创建schema,需要在hbase-site.xml开启权限 >
    <property>
        <name>phoenix.schema.isNamespaceMappingEnabled</name>
        <value>true</value>
    </property>
    <property>
        <name>phoenix.schema.mapSystemTablesToNamespace</name>
        <value>true</value>
    </property>
</configuration>
[root@cdh1 ~]$ vim regionservers
localhost
# 启动zk
[root@cdh1 ~]$ zkServer.sh start
# 启动hdfs(单机版即可)
[root@cdh1 ~]$ start-dfs.sh
# 启动hbase
[root@cdh1 ~]$ start-hbase.sh
# 启动命令行
[root@cdh1 ~]$ hbase shell
# web页面
http://localhost:16010

# namespace操作
hbase(main):001:0> list_namespace  # 查看所有数据库
hbase(main):001:0> list_namespace_tables 'test'  # 查看test库所有表
hbase(main):001:0> create_namespace 'test'  # 创建数据库
hbase(main):001:0> describe_namespace 'test'  # 查看数据库
hbase(main):001:0> alter_namespace 'test', {METHOD => 'set', 'author' => 'okc'}  # 添加/修改属性
hbase(main):001:0> alter_namespace 'test', {METHOD => 'unset', NAME => 'author'}  # 删除属性
hbase(main):001:0> drop_namespace 'test'  # 删除数据库
# table操作
hbase(main):001:0> list  # 查看所有表
hbase(main):001:0> list_snapshots  # 查看所有快照
hbase(main):001:0> create 'student','info'  # 创建表,指定表名和列族
hbase(main):001:0> describe 'student'  # 查看表信息
hbase(main):001:0> put 'student','1001','info:name','grubby'  # 插入数据
hbase(main):001:0> put 'student','1001','info:age','18'
hbase(main):001:0> put 'student','1001','info:sex','male'
hbase(main):001:0> scan 'student'  # 扫描数据
hbase(main):001:0> scan 'student',{LIMIT => 3}}  # 扫描前3条数据
hbase(main):001:0> scan 'student',{COLUMNS => 'info'}  # 扫描指定列族所有数据
hbase(main):001:0> scan 'student',{COLUMNS => 'info:name'}  # 扫描指定列族:列所有数据
hbase(main):001:0> scan 'student',{TIMERANGE=>[1561910400000,1561996799000]}  # 扫描该时间段内插入数据
hbase(main):001:0> scan 'student',{STARTROW => 'aaa',ENDROW => 'bbb'}  # 扫描rowkey处于aaa和bbb之间的数据
hbase(main):001:0> put 'student','1001','info:name','moon'  # 更新指定字段值
hbase(main):001:0> get 'student','1001'  # 查看指定行
hbase(main):001:0> get 'student','1001','info:name'  # 查看指定列族:列
hbase(main):001:0> count 'student'  # 统计行数
hbase(main):001:0> delete 'student','1001','info:sex'  # 删除rowkey指定列
hbase(main):001:0> deleteall 'student','1001'  # 删除rowkey全部数据
hbase(main):001:0> truncate 'student'  # 清空表
hbase(main):001:0> disable 'student' & drop 'student'  # 删除表
```

### phoenix
```shell script
# phoenix是hbase的开源sql皮肤,可以使用标准jdbc-api操作hbase
# 安装
[root@cdh1 ~]$ tar -xvf apache-phoenix-5.0.0-HBase-2.0-bin.tar.gz -C /opt/module/
# 将phoenix-server.jar拷贝到hbase/lib
[root@cdh1 ~]$ cp /phoenix/phoenix-5.0.0-HBase-2.0-server.jar /hbase/lib/
# 将hbase-site.xml拷贝到phoenix/bin
[root@cdh1 ~]$ cp /hbase/conf/hbase-site.xml /phoenix/bin
# 重启hbase(可能需要把hdfs和zk的/hbase目录删掉,不然phoenix启动报错)
[root@cdh1 ~]$ stop-hbase.sh && start-hbase.sh
# 启动phoenix
[root@cdh1 ~]$ sqlline.py localhost:2181

# 常用操作
# 创建schema,phoenix中的schema/table/field会自动转换为大写,若要小写需使用双引号"student"
jdbc:phoenix:localhost:2181> create schema if not exists dim
# 查看所有表
jdbc:phoenix:localhost:2181> !table
# 查看表结构
jdbc:phoenix:localhost:2181> !desc realtime.dim_sku_info
# 创建表
jdbc:phoenix:localhost:2181> CREATE TABLE IF NOT EXISTS dim.student(
  id VARCHAR primary key,  # 指定单列作为RowKey
  name VARCHAR,
  addr VARCHAR);
# 更新数据
jdbc:phoenix:localhost:2181> upsert into dim.student values('1001','grubby','上海');
# 查询数据
jdbc:phoenix:localhost:2181> select * from dim.student;
# 删除数据
jdbc:phoenix:localhost:2181> delete from dim.student where id = '1002';
# 退出phoenix
jdbc:phoenix:localhost:2181> !quit
```