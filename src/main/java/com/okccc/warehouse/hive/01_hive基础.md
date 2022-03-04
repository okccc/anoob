- [cdh各组件端口](https://docs.cloudera.com/documentation/enterprise/6/6.2/topics/cdh_ports.html)
### HDFS
```shell script
# Client：客户端
1.将文件按block块切分
2.与NameNode交互,获取文件的位置信息
3.与DataNode交互,读写数据

# NameNode：管理节点
1.管理HDFS命名空间
2.管理元数据信息,即文件与数据块的映射关系
3.配置数据块副本 
4.处理客户端读写请求

# DataNode：工作节点,执行NameNode下达的命令
1.存储实际的数据块
2.执行数据块的读写操作

# SecondaryNameNode：为了避免edits不断变大,会定期合并fsimage和edits,该操作挺耗时,在NameNode操作会影响性能导致卡顿
fsimage是某一时刻HDFS的快照
edits会记录HDFS的各种更新操作
        
# 写数据
# block大小 dfs.block.size=128m
# packet大小 dfs.write.packet.size=128k
1.客户端请求NameNode上传文件,NameNode返回是否可以上传
2.客户端请求上传第一个block的DataNode服务器,NameNode返回dn1/dn2/dn3数据节点
3.客户端请求向dn1上传数据,dn1收到请求会继续调用dn2,然后dn2调用dn3,建立通信管道pipline
4.客户端开始往dn1上传第一个block,以packet为单位,dn1收到一个packet就会传给dn2,dn2传给dn3 
5.当一个block传输完成之后,客户端再次请求NameNode上传第二个block的服务器

# 读数据
1.客户端请求NameNode下载文件
2.NameNode查询元数据找到文件块存放地址,就近挑选一台dn节点读取数据
3.DataNode开始传输数据给客户端

# 为什么HDFS是3个副本？
HDFS采用机架感知策略,保证数据可靠性,并且mr会根据机架距离获取离自己最近的数据从而减少网络传输
一个副本存放在本地机架节点,另一个副本存放在本地机架的另一个节点,第三个副本存放在不同机架的节点

# HDFS小文件(<<dfs.block.size的文件)有什么影响？
存储层面：影响NameNode寿命,因为文件的元数据信息存放在NameNode的内存中
计算层面：浪费计算资源,每个小文件都会占用一个Map任务
解决：使用SequenceFile和CombineFileInputFormat合并小文件代替hadoop默认的TextFile,使用fsimage排查小文件ka

# HDFS安全模式？
启动hadoop集群时,启动了namenode未启动datanode,此时文件系统处于安全模式,只能读不能写,只能查看有哪些文件而不能查看文件内容,因为datanode还未启动
namenode会等待datanode向它发送块报告,接收到的datanode blocks和total blocks占比达到99.9%表示块数量一致,文件系统会在等待30秒之后退出安全模式
```

### HIVE
```shell script
# 安装
[root@cdh1 ~]$ tar -xvf apache-hive-3.1.2-bin.tar.gz -C /opt/module
# 修改配置文件
[root@cdh1 ~]$ vim hive-env.sh
export HADOOP_HOME=/opt/module/hadoop-3.1.3
[root@cdh1 ~]$ vim hive-site.xml
<configuration>
	<property>
	  <name>javax.jdo.option.ConnectionURL</name>
	  <value>jdbc:mysql://localhost:3306/metastore?createDatabaseIfNotExist=true</value>
	</property>
	<property>
	  <name>javax.jdo.option.ConnectionDriverName</name>
	  <value>com.mysql.jdbc.Driver</value>
	</property>
	<property>
	  <name>javax.jdo.option.ConnectionUserName</name>
	  <value>root</value>
	</property>
	<property>
	  <name>javax.jdo.option.ConnectionPassword</name>
	  <value>root@123</value>
	</property>
	<property>
      <name>hive.server2.thrift.port</name>
      <value>10000</value>
    </property>
</configuration>
# 拷贝jdbc驱动
[root@cdh1 ~]$ cp mysql-connector-java-5.1.48.jar /opt/module/hive-3.1.2-bin/lib/
# 创建hive元数据库
mysql> create database metastore;
# 初始化hive元数据库
[root@cdh1 ~]$ schematool -initSchema -dbType mysql -verbose
Initialization script completed
# 先启动hdfs
[root@cdh1 ~]$ start-dfs.sh
# 再启动hive
[root@cdh1 ~]$ hive

# hive、hiveserver2、beeline
hive和beeline都是hive客户端,hiveserver和hivesrver2都是hive服务端
hiveserver是本地模式,只能处理单个请求,已废弃
hiveserver2是远程连接模式,可以在任意机器使用hive或beeline(hue/象数)连接
# gateway
由于hive服务没有worker角色,需要另一种机制使客户端的配置传播到集群中其它主机
# metastore
存储hive元数据,一般存放在关系型数据库mysql中,内置derby只支持一个会话连接
# driver
解析器(SQL Parser)：将sql字符串解析成抽象语法树AST并进行语法分析
编译器(Compiler)：将AST编译成logical plan
优化器(Optimizer)：优化logical plan
执行器(Executor)：将logical plan转换成可执行的physical plan,调用MR/Spark计算框架

# 数据库以行列二维表形式展现数据,以一维字符串方式存储数据
# record-oriented
把一行的数据值串在一起存储,然后再存储下一行(1,zhangsan,18 2,lisi,19 3,wangwu,20 ...)
读数据会读取一整行有些冗余,写数据会一次性写入速度很快,适合写多读少场景,面向OLTP系统(mysql)
# column-oriented
把一列的数据值串在一起存储,然后再存储下一列(1,2,3 zhangsan,lisi,wangwu 18,19,20 ...)
查询时只读取相关列避免全表扫描且不用维护高成本的索引,写数据则要把行拆成列多次写入消耗很大,适合读多写少场景,面向OLAP系统(hive/hbase)
同一列的重复数据和空值可以提高压缩率,查询时扫描的行数更少
# 存储和压缩
textfile(hadoop默认)和sequencefile基于行存储不常用,orc和parquet基于列存储并且可以手动安装snappy和lzo进行压缩
orc读取行数更少查询更快,但是parquet支持hadoop所有项目,snappy压缩效率更高但是不支持文件切割,如果压缩文件很大可能会出现map端读取文件时的数据倾斜
所以实际生产中parquet存储lzo压缩更常见,但是没有超大文件时orc存储snappy压缩效率还是非常高的
mysql是行存储,所以sqoop导入ods层的表只能是textfile格式,dwd层表可以是orc/parquet格式
```

### tables
```sql
-- hive是把除了类似select * 这种简单查询以外的sql都翻译成mr在yarn集群里跑
{
	"id": 1,
	"names": ["aa", "bb"],   -- Array<String>
	"info": {"grubby": 19},  -- Map<String: int>
	"address": {             -- Struct<String: String, String: String>
		"city": "上海",
		"district": "闵行"
	}
}
[root@master1 hive]# cat log.txt
1    aa,bb    grubby:19    上海,闵行
2    cc,dd    moon:20      苏州,园区

-- 内部表：数据由hive自己管理,删表会同时删除metadata和hdfs文件,默认路径hive.metastore.warehouse.dir=/user/hive/warehouse
-- 外部表(推荐)：external修饰,数据由hdfs管理,删表只会删除metadata而hdfs文件还在,可以指定location,不指定就默认/user/hive/warehouse
create external table if not exists dw.dw_log_info(
id               int,
names            array<string>,
info             map<string, int>,
address          struct<city: string, district: string>
) comment '日志表'
partitioned by (dt string)  -- 分区表可以提高数据检索效率,dt不存放实际内容,仅仅是作为分区标识存在于表结构中,内部表和外部表都可以设置分区
row format delimited  
fields terminated by '\001'         -- 列分隔符,默认'\001'
collection items terminated by '&'  -- 集合(array/map/struct)元素之间的分隔符
map keys terminated by ':'          -- map中key和value的分隔符
lines terminated by '\n'            -- 行分隔符
-- orc将数据按行分块按列存储,保证同一条记录在一个块上,snappy压缩率能达到1:10左右
stored as orc tblproperties ("orc.compress"="snappy")
location 'hdfs://dev-cdh/user/flume/nginx_log';

hive> load data local inpath '/home/hive/log.txt' overwrite into table t1 partition(dt=20200101);
hive> select * from t1;
1    ["aa","bb"]    {"grubby":19}    {"city":"上海","district":"闵行"}    20200101
2    ["cc","dd"]    {"moon":20}      {"city":"苏州","district":"园区"}    20200101
hive> select id,name from t1 lateral view explode(names) t1 as name;
1    aa
1    bb
2    cc
2    dd

-- 分区和分桶
分区表在hdfs上表现形式是目录,分桶是一个单独的文件
分区是为了查询时减少mr扫描的数据量,分桶是为了提高join效率用分桶字段连接

-- 动态分区
-- 业务需求：mysql表很大,现在要抽到hive按天分区,保留2016年后的数据,2016年以前的数据都放到20151231这个分区里
-- 解决方法：先将全量数据导入到temp的临时表(不分区),然后使用动态分区插入到ods层的分区表中
-- 注意：动态分区的字段一定位于其他各个字段的最后
set hive.exec.dynamic.partition=true;                  -- 开启动态分区
set hive.exec.dynamic.partition.mode=nonstrict;        -- 默认strict(严格模式,必须至少包含一个静态分区)
set hive.exec.max.dynamic.partitions=10000;            -- 能生成的动态分区最大总数
set hive.exec.max.dynamic.partitions.pernode=1000;     -- 每个节点能生成的最大分区数
insert overwrite table ods.tickets_order partition(dt)
select *,
       case when create_time >= '2016-01-01' then regexp_replace(substr(create_time,0,10),'-','') else 20151231 end
from temp.tickets_order;

-- hive中文乱码
-- 修改存放hive元数据信息的数据库表字符集
alter table COLUMNS_V2 modify column COMMENT varchar(256) character set utf8;
alter table TABLE_PARAMS modify column PARAM_VALUE varchar(4000) character set utf8;
alter table PARTITION_KEYS modify column PKEY_COMMENT varchar(4000) character set utf8;
```

### IMPALA
```shell script
# d(daemon)表示守护进程,是运行在linux后台的一种服务程序,周期性地执行某种任务或等待处理某些事件,linux的大多数服务就是用守护进程实现的
# impala是基于hive的实时分析查询引擎,直接使用hive的元数据库metadata
# hive和impala对比
hive适合长时间的批处理,有大量读写磁盘的过程 map->shuffle->reduce->map->shuffle->reduce
impala适合实时查询,中间结果放内存通过网络传输没有磁盘读写所以速度更快

# impala daemon(核心组件,多实例,物理进程impalad)
负责读写数据,接收从impala-shell/hue/jdbc等接口发送的查询sql,并行查询并在集群中分配任务,将中间结果集发送给协调器
impala daemon通常和DataNode部署在相同节点,接收查询的节点作为协调器coordinator,其它节点会传输中间结果集给协调器并由协调器构建最终结果集
impala daemon会不间断地和statestore通信以确认哪些节点是健康的能接收新的任务,同时接收catalog传来的广播消息更新元数据

# impala statestore(单实例,物理进程statestored)
检查集群各节点impala daemon的健康状态

# impala catalog(单实例,物理进程catalogd)
当metadata更新时会通知任意impala daemon刷新元数据信息,因为会与statestore交互所以安装在同一节点
```

### shell
```shell script
# hive shell
-d,--define<key=value>         # 定义变量 -d num=10 
-e,<quoted-query-string>       # 执行一段sql
-f,--filename                  # 执行保存sql的文件           
-h,--hostname                  # 连接远程hive server           
-p,--port                      # 连接远程hive server端口号      
-hiveconf,<property=value>     # 设置配置参数                   
-hivevar,<key=value>           # 类似define                    
-s,--silent                    # 安静模式,只显示结果不显示进度

# impala-shell
-b,--delimited                 # 去格式化输出,大数据量查询可以提高效率
-i,--impalad                   # 连接指定impalad,impala-shell端口21000,hue/jdbc端口21050
-d,--database                  # 指定数据库
-q,--query                     # 执行一段sql
-f,--filename                  # 执行保存sql的文件
-o,--output                    # 输出结果到指定文件
-v/-v,--version/--verbose      # 查看版本信息,开启详细输出
--quiet                        # 安静模式,只显示结果不显示进度
```

### cmd
```sql
-- 查看系统当前用户
hive> set system:user.name;
system:user.name=hdfs
-- 查看默认mr数量
hive> set mapred.reduce.tasks;
mapred.reduce.tasks=-1
-- 查看执行引擎,包括mr/tez/spark
hive> set hive.execution.engine=tez;
hive> set tez.am.tez-ui.webservice.enable=false;
-- 查看本地模式
hive> set hive.exec.mode.local.auto;
hive.exec.mode.local.auto=false
-- 在输出结果最上面一行打印列名
hive> set hive.cli.print.header=true;
-- 查看数据库信息
hive> desc database ods;
ods    hdfs://dev-cdh/user/hive/warehouse/ods.db deploy USER
-- 删除库(加cascade可以删除含有表的数据库)
hive> drop database test cascade;
-- 模糊搜索表
hive> show tables like '*name*';
-- 查看hive表最近一次读写时间
hive> show table extended in db_name like tbl_name;
-- 删除表
hive> drop table test;
-- 清空表数据
hive> truncate table test;
-- 添加字段(注意：添加新字段后要将原来已经存在的分区先删掉,不然数据加载不进去,如果要调整新字段顺序,可以再用change)
hive> alter table test add columns(order_id int comment '订单id') cascade;
-- 修改字段
hive> alter table test change column column1 column2 string comment '...' first|after column3;
-- 删除字段(只保留需要的列,不需要的列删掉,同时也可以更换列的顺序)
hive> alter table test replace columns(id int, name string);
-- 删除表分区,如果直接删除HDFS数据目录,表分区还在但没有数据
hive> alter table test drop partition (dt=20160101);               # 删除单个分区
hive> alter table test drop partition (dt>=20160101,dt<20170101);  # 删除多个分区
-- 重命名表
hive> alter table t1 rename to table2;
-- temporary表示临时表,仅在本次hive session期间有效,关闭hive后会自动删除,不加该关键字则会存储下来
-- create table like 复制表结构(没有数据)
hive> create temporary table t1_tmp like t1 stored as textfile;
-- create table as 生成新表并插入数据,表结构取决于select的内容
hive> create temporary table t1_tmp as select * from t1;
-- 查看分区信息
hive> show partitions test;
-- 查看最小分区
hive> select min(dt) from test;
-- hdfs文件存在且有数据但是hive查不到？
-- 如果hive表的数据不是使用insert语句插入而是通过hdfs命令行或api写入的话,hive表的分区信息在metastore是没有的,可通过该命令修复
hive> set hive.msck.path.validation=ignore;
hive> msck repair table db.table
-- 查找所有函数
hive> show functions;
-- 查看某个函数使用案例
hive> desc function extended parse_url;
-- 视图
hive> create view v01 as select * from debit_info where dt=regexp_replace(date_sub(current_date,1),'-','');
-- 注册udf,将开发的udf打成jar包上传到HDFS指定目录,然后创建函数
-- udf使用场景：1.加密、解密、解析IP地址这种系统函数处理不了的 2.专门处理json数据的fastjson这种方便通过程序debug定位问题的
-- 专门处理json数据的udf地址：https://github.com/klout/brickhouse/tree/master/src/main/java/brickhouse/udf/json
hive> create function url_decode as 'com.qbao.udf.decodeurl' using jar 'hdfs:///lib/decodeurl.jar';
-- 删除函数
hive> drop function url_decode;

-- hive查看表统计信息
hive> desc formatted table_name;
-- hive计算表和字段的统计信息,分区表必须指定分区(不可靠且难用,建议用impala的compute stats)
hive> analyze table table_name [partition(dt=20200612)] compute statistics;  -- 表
hive> analyze table table_name [partition(dt=20200612)] compute statistics for columns;  -- 列

-- impala连接指定主机
[master2:21000] > connect cdh2:21000;
-- 刷新指定表
[master2:21000] > refresh table_name;
-- 刷新元数据所有表
[master2:21000] > invalidate metadata;
-- 查看执行计划
[master2:21000] > explain select;
-- 设置查询计划显示级别
[master2:21000] > set explain_level=0/1/2/3;  -- 等级越高越详细
-- 执行查询sql后再执行summary或profile可以查看详细查询分析
[master2:21000] > summary/profile;
-- impala优化器：hive数据更新 - refresh - compute stats 会统计一些聚合信息并存储在元数据中
-- impala关联查询优化：compute stats收集统计信息后,impala会基于每个表的大小、每一列的不同值个数等信息优化查询计划
-- 建议最大表放首位,因为这个表是直接从磁盘读取,它的大小不影响内存使用,后续join的表作为中间结果都是放在内存
[master2:21000] > compute/drop stats table_name;  -- 全量
[master2:21000] > compute/drop incremental stats table_name partition(dt='20200612' | dt>'20200101' | dt<'20200612');  -- 增量
-- impala查看表和字段统计信息
[master2:21000] > show column stats table_name;   -- 查看字段聚合信息
[master2:21000] > show table stats table_name;  -- 查看表聚合信息,包括文件行数/大小/类型/路径等

-- mysql数据导入
mysql> show variables like 'secure_file_priv';
mysql> load data local infile '...' [replace] into table test;  # 覆盖/追加 
-- mysql数据导出
mysql> select * from test into outfile '...' fields terminated by ',' enclosed by '"' lines terminated by '\n';  
-- hive数据导入
hive> load data [local] inpath '...' [overwrite] into table t1 [partition(dt='..')]  # 本地(复制)/HDFS(剪切)  覆盖/追加    
hive> insert overwrite/into table t1 [partition(dt=20200101)] select * from t2 where ...
hive> create table t2 as select * from t1 where ...
-- hive数据导出
hive -e "select * from test;" > /opt/aaa.txt  # insert overwrite慎用,会覆盖整个目录!

-- 排序方式
hive> select * from test;
5 3 6 2 9 8 1
-- order by：全局排序,最后会用一个reduce task来完成
hive> select * from test order by id;
1 2 3 5 6 8 9
-- sort by：分区内排序,在每个reduce内部排序,如果reduce.task=1,等价于order by
-- distribute by：对map输出数据按指定字段划分到不同reduce中,常和sort by一起使用
hive> set mapred.reduce.tasks=2;
hive> select * from test distribute by id sort by id;
2 5 6 9 1 3 8
-- cluster by：当distribute by和sorts by字段相同时可使用cluster by代替,但是只能升序排序
```

### optimize
```sql
-- 1.尽量避免使用select *,如果大表字段很多会扫描很多数据
-- 2.慎用count distinct,可以用group by代替
-- 3.sql语句采用谓词下推技术,提早过滤数据减少数据传输
-- 4.提高并行执行的任务数,set hive.exec.parallel=true; set hive.exec.parallel.thread.number=16;
-- 5.选择tez作为计算引擎,mr会将迭代任务的中间结果多次写入hdfs,而tez可以将有多个依赖的job转换为一个job,这样只需写一次hdfs从而大大提升计算性能
-- 6.本地模式,适用于处理数据量很小的情况,mr启动过程消耗的时间可能比实际计算时间还长,单节点完全够用,set hive.exec.mode.local.auto=true
-- 7.小文件处理,使用SequenceFile(流计算场景)/CombineFileInputFormat代替TextFile、减少reduce数量、shell脚本合并小文件
-- 8.jvm重用,适用于小文件和task过多的场景,大多数执行时间很短,jvm启动消耗很大,jvm重用可以使jvm实例在一个job中重复使用多次,修改mapred-site.xml
-- 9.设置合理的map/reduce数量
-- 10.数据倾斜问题
```

### map/reduce tasks
```sql
-- map和reduce数量不是越多越好,启动和初始化很消耗时间和资源,并且每个reduce都会产生一个output文件,迭代过程中大量output文件又会成为下个任务的input
-- job会通过input文件产生map任务,map数和文件大小,文件个数,文件块大小(默认128m,set dfs.block.size)有关  
-- 原则就是处理大规模数据时使用合适的map/reduce数,使单个map/reduce任务处理合适的数据量

-- 1.减少map数
-- /user/hive/warehouse/test/dt=20170101 共194个文件总大小9g,其中很多<<128m的,正常执行会占用194个map任务,消耗计算资源：slots_millis_maps=623020  
select count(1) from test where dt=20170101;
-- 合并小文件,再次执行只占用74个map任务,消耗计算资源：slots_millis_maps=333500
set hive.input.format=org.apache.hive.ql.io.combinehiveinputformat;

-- 2.增加map数
-- 如果a表只有一个文件大小是120m,但只有两三个字段却包含几千万条数据,如果处理逻辑很复杂1个map显然不够用
-- 增加map数,将a表数据随机分散到包含10个文件的a1表,占用10个map,每个map任务处理大于12m(几百万条)的数据提高效率
set mapred.map.tasks=10;  
create table a1 as select * from a distribute by rand(123);  

-- 3.调整reduce数
-- 只有一个reduce的情况：数据量小于1g/没有group by/有order by/count distinct/笛卡尔积,这些都属于全局操作,hadoop不得不用一个reduce去完成
set hive.exec.reducers.bytes.per.reducer=500M;  -- 修改每个reduce处理的数据量,默认1g
set mapred.reduce.tasks=15;                     -- 设定reduce个数,reducer数 = min(数据总量/参数1,参数2)
```

### data skew
```sql
-- 数据倾斜本质：shuffle过程中map端的输出结果按照key hash分配不均匀,导致reduce端分到的数据量差异过大
-- 1.join导致
-- a)hive默认reduce join,大表和很小表关联时可以使用map join,hive会自动检查小表并读入内存,在map端做join省掉shuffle过程
set hive.auto.convert.join=true;           -- 开启map join
set hive.mapjoin.smalltable.filesize=25M;  -- 当小表不超过指定值时使用
-- b)大表和大表关联时可以使用skew join,会将倾斜的key认定为特殊值,先不在reduce端处理,而是先写入HDFS然后启动map join专门处理这特殊值
set hive.optimize.skewjoin=true;  -- 开启skew join
set hive.skewjoin.key=100000;     -- 当key记录数超过100000时认定为特殊值
-- c)关联字段存在null值,有如下两种方法,上面需要2个job,下面只要1个job
select * from users a left join orders b on a.uid is not null and a.uid = b.uid union all select * from users where uid is null;
select * from users a left join orders b on case when a.uid is null then cast(rand()*100000 as int) else a.uid end = b.uid;
-- d)关联字段类型不一致,用户表uid都是int,订单表uid有string也有int,那么所有string类型的id都会分配到一个reduce
select * from users a left join orders b on a.uid = cast(b.uid as string);

-- 2.group by导致
set hive.map.aggr=true;  -- 在map端先做部分聚合操作,如果map端数据基本不一样那么预聚合没有意义,要视下面两个参数结果而定
set hive.groupby.mapaggr.checkinterval=100000;
set hive.map.aggr.hash.min.reduction=0.5;  -- 先预聚合100000条数据,如果聚合后的记录数/100000>0.5则不再聚合
set hive.groupby.skewindata=true;  -- 只针对单列有效,会生成两个mrjob,第一个job中map输出结果会随机分配到reduce预处理,相同key也可能分发到不同reduce,第二个job再按key分组聚合  

-- 3.distinct存在数据量很大的特殊值,可以先group by去重,虽然会多一个job但是数据量很大时是值得的
select count(id) from (select id from users group by id) a;
```