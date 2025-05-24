- [centos7安装mysql5.7](https://juejin.im/post/5d07cf13f265da1bd522cfb6)
### install
```shell script
# 查看现有版本
[root@cdh1 ~]$ rpm -qa | grep -i mysql
# 删掉一切(没有就跳过)
[root@cdh1 ~]$ rpm -ev --nodeps mysql-libs-5.1.71-1.el6.x86_64
# 下载rpm包
[root@cdh1 ~]$ wget https://dev.mysql.com/get/mysql57-community-release-el7-11.noarch.rpm
# 安装rpm包,执行成功后会在/etc/yum.repos.d/目录下生成两个repo文件mysql-community.repo及mysql-community-source.repo
[root@cdh1 ~]$ rpm -ivh mysql57-community-release-el7-11.noarch.rpm
# 确认mysql仓库添加成功
[root@cdh1 ~]$ yum repolist enabled | grep mysql
mysql-connectors-community/x86_64       MySQL Connectors Community           153
mysql-tools-community/x86_64            MySQL Tools Community                110
mysql57-community/x86_64                MySQL 5.7 Community Server           424
# 切换mysql版本(如有必要)
[root@cdh1 ~]$ vim /etc/yum.repos.d/mysql-community.repo
enabled=1
# 安装mysql服务器及所有依赖(包括mysql-community-client、mysql-community-common、mysql-community-libs)
[root@cdh1 ~]$ yum -y install mysql-community-server
# 安装mysql驱动
[root@cdh1 ~]$ wget https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-5.1.46.tar.gz
[root@cdh1 ~]$ tar -xvf mysql-connector-java-5.1.46.tar.gz
[root@cdh1 ~]$ mkdir -p /usr/share/java/
[root@cdh1 ~]$ cp mysql-connector-java-5.1.46-bin.jar /usr/share/java/mysql-connector-java.jar
# 启动mysql
[root@cdh1 ~]$ systemctl start mysqld && systemctl enable mysqld
# 第一次启动会创建超级管理员账号root@localhost,初始密码存储在日志文件中
[root@cdh1 ~]$ grep -i 'temporary password' /var/log/mysqld.log
# 首次登陆先修改密码
[root@cdh1 ~]$ mysql -u root -p
# mysql5.6.6版本后增加了密码强度验证插件validate_password
mysql> show variables like 'validate_password%';
# 降低密码强度验证等级(和hive一样set设置是暂时的只对本次连接有效,修改/etc/my.cnf才能永久生效)
mysql> set global validate_password_policy=0;
# 修改密码(包含数字、字母、特殊字符)
mysql> alter user 'root'@'localhost' identified by 'root@123';
# 允许root远程访问(*.*:db.table  'root':账户名  @:赋权  '%':远程访问的服务器ip,不是mysql地址,'10.2.35.%'表示10.2.35集群上的节点)
mysql> grant all privileges on *.* to 'root'@'%' identified by 'root@123' with grant option;
# 查看root用户权限
mysql> show grants for 'root'@'%';
# 查看all privileges都包括哪些权限,先撤销insert看还剩下哪些再加上insert就是所有权限
mysql> revoke insert on *.* from root@'%';
# 刷新权限
mysql> flush privileges;
# 查看编码
mysql> show variables like 'character%';
# 修改数据库编码
[root@cdh1 ~]$ vim /etc/my.cnf && systemctl restart mysqld  # 修改配置文件后要重启mysqld服务
[mysqld]
character-set-server=utf8
init-connect='SET NAMES utf8'
validate_password_policy=0
# 开启日志监控
mysql> show variables like 'general%';
mysql> set global general_log='on';
mysql> set global general_log_file='/var/log/mysqld.log';
tail -f /var/log/mysqld.log
# 查看时区
mysql> show variables like '%time_zone%';
+------------------+--------+
| Variable_name    | Value  |
+------------------+--------+
| system_time_zone | CST    |
| time_zone        | +08:00 |  # +00:00 +08:00 SYSTEM
+------------------+--------+
# 查看mysql连接数
mysql> show variables like 'max_connections' / select @@max_connections / set global max_connections=1000
+-----------------+-------+
| Variable_name   | Value |
+-----------------+-------+
| max_connections | 4500  |
+-----------------+-------+
# 查看mysql连接等待的超时时间
mysql> show variables like 'wait_timeout' / select @@wait_timeout / set global wait_timeout=28800
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| wait_timeout  | 7200  |
+---------------+-------+
# 查看mysql线程状态created/cached/connected/running
mysql> show status like 'Thread%';
+-------------------------+-------+
| Variable_name           | Value |
+-------------------------+-------+
| Threads_cached          | 13    |  # 当前缓存的线程数,将客户端断开连接后的线程缓存起来,有新的请求连接时可以快速响应无需创建新线程
| Threads_connected       | 8     |  # 当前连接的线程数,即正在使用的线程
| Threads_created         | 21    |  # 已经创建的线程数,Threads_created = Threads_cached + Threads_connected
| Threads_running         | 1     |  # 当前激活的线程数,Threads_running << Threads_connected,因为有些连接的线程处于sleep状态
+-------------------------+-------+
# 查看mysql的QPS
mysql> show status like 'Questions%';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| Questions     | 32    |
+---------------+-------+
# 查看mysql线程池缓存大小
mysql> show variables like 'thread_cache_size';
+-------------------+-------+
| Variable_name     | Value |
+-------------------+-------+
| thread_cache_size | 512   |
+-------------------+-------+
# 查看顺序读缓冲区大小
mysql> show variables like 'read_buffer_size';
+------------------+--------+
| Variable_name    | Value  |
+------------------+--------+
| read_buffer_size | 131072 |
+------------------+--------+
# 查看随机读缓冲区大小
mysql> show variables like 'read_rnd_buffer_size';
+----------------------+--------+
| Variable_name        | Value  |
+----------------------+--------+
| read_rnd_buffer_size | 262144 |
+----------------------+--------+
# 查看排序缓冲区大小
mysql> show variables like 'sort_buffer_size';
+------------------+--------+
| Variable_name    | Value  |
+------------------+--------+
| sort_buffer_size | 262144 |
+------------------+--------+
# 查看连接缓冲区大小
mysql> show variables like 'join_buffer_size';
+------------------+--------+
| Variable_name    | Value  |
+------------------+--------+
| join_buffer_size | 262144 |
+------------------+--------+
# 查看临时表大小
mysql> show variables like 'tmp_table_size';
+----------------+----------+
| Variable_name  | Value    |
+----------------+----------+
| tmp_table_size | 16777216 |
+----------------+----------+
# 查看内存表大小
mysql> show variables like 'max_heap_table_size';
+---------------------+----------+
| Variable_name       | Value    |
+---------------------+----------+
| max_heap_table_size | 16777216 |
+---------------------+----------+
# 查看最大数据包大小
mysql> show variables like 'max_allowed_packet';
+--------------------+----------+
| Variable_name      | Value    |
+--------------------+----------+
| max_allowed_packet | 67108864 |
+--------------------+----------+
# 查看处理sql语句的模式
mysql> SHOW VARIABLES LIKE 'sql_mode';
+---------------+-----------------------------------------------------------------------------------------------------------------------+
| Variable_name | Value                                                                                                                 |
+---------------+-----------------------------------------------------------------------------------------------------------------------+
| sql_mode      | ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION |
+---------------+-----------------------------------------------------------------------------------------------------------------------+
# 查看sql语句读写文件的权限
mysql> SHOW VARIABLES LIKE 'secure_file_priv';
+------------------+-------+
| Variable_name    | Value |
+------------------+-------+
| secure_file_priv | NULL  |
+------------------+-------+
# 查看是否允许客户端从本地文件加载数据
mysql> SHOW VARIABLES LIKE 'local_infile';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| local_infile  | OFF   |
+---------------+-------+
# 批量插入数据
mysql> source area.sql;
```

### basic
```sql
/*
e-r模型：当前物理数据库都是按照e-r模型(entry-relationship)进行设计的,关系包括一对一/一对多/多对多
数据库：按照数据结构存储和管理数据的仓库
RDBMS：关系型数据库管理系统
表：按列和行排列的一组数据,列表示特征行表示条目
三大范式：列不可拆分(比如地址)、不能有部分函数依赖、不能有传递函数依赖
五大约束：primary key、unique、not null、default、foreign key
逻辑删除：对于重要数据并不希望物理删除,删除后无法恢复,可以设置is_delete列,类型为bit默认值0,要逻辑删除的写1,查询的时候查值为0的即可
sql：structured query language
DDL(数据定义语言)：create/alter/drop/truncate/rename  -- 针对表
DML(数据操作语言)：insert/delete/update/select        -- 针对数据
DCL(数据控制语言)：commit/rollback/grant/revoke/savepoint

sql和nosql区别？
存储：sql必须先定义表和字段结构才能添加数据,nosql更加灵活和可扩展
关联：sql可以做join操作,nosql不存在
事务：sql支持事务操作,nosql没有事务概念,每个数据集的操作都是原子级的
性能：nosql不需要维护复杂的表关系,性能更好
*/

-- 查看当前用户/当前数据库/数据库版本
select user(),database(),version();
-- 查看所有数据库
show databases;
-- 创建数据库
-- mysql8使用utf8mb4字符集,4个字节存储1个字符,覆盖范围更广包括表情符号等,mysql5使用utf8字符集,3个字节存储1个字符,无法全部覆盖unicode编码,可能会乱码
-- mysql8使用utf8mb4_0900_ai_ci排序方式,不区分大小写
create database java character set utf8mb4 collate utf8mb4_0900_ai_ci;
show create database java;
-- 查看默认字符集和排序方式
show variables like 'character_set_database';
show variables like 'collation_database';
-- 选择数据库
use java;
-- 删除数据库
drop database java;
-- 修改数据库名(不能直接修改,可以先备份再删除原先的)
/*
数据备份
    使用超级管理员权限: sudo -s
    进入mysql库目录: cd /var/lib/mysql
    运行mysqldump命令: mysqldump –uroot –p 要备份的数据库 > ~/desktop/bac.sql;(其实就是在新的文件里create和insert)
    比如导出hive元数据：mysqldump -h localhsot -uroot -p --single-transaction hivemetastore > hivemetastore-src.sql 
数据恢复
    连接mysql,先创建一个新的数据库,然后往这个新数据库里恢复数据
    退出重新连接: mysql -uroot –p 新创建的数据库 < ~/desktop/bac.sql
*/

-- 整数类型
-- unsigned表示无符号(负号),即没有负值,数值范围会将负值部分的绝对值加入到正值部分,常用于年龄、商品库存等字段
-- tinyint：占1个字节,无符号0 ~ 255,有符号-128 ~ 127
-- int：    占4个字节,无符号0 ~ 2^32 - 1,有符号-2^31 ~ 2^31 - 1
-- bigint： 占8个字节,无符号0 ~ 2^64 - 1,有符号-2^63 ~ 2^63 - 1

-- 浮点类型
-- float(m,d)：  占4个字节,单精度
-- double(m,d)： 占8个字节,双精度
-- decimal(m,d)：精度要求很高时使用,比如工资、价格等,m是长度,d是小数点位数

-- 字符类型
-- char：   固定长度,性能较好,不足则以空格补全,char(5) 'abc' -> 'abc  ',当然读取的时候会自动去掉右侧空格,char = char(1),最大值char(255)
-- varchar：可变长度,性能一般,会自动伸缩,占有的空间不能超过一行数据的最大限制65535字节,varchar类型默认会使用1个字节标识是否为null,所以是65534个字节,utf8mb4字符集就是65534 / 4 = 16383个字符
-- text：   大文本类型,无需指定长度,有固定的大小限制,text[65535],不占有一行数据的最大限制,一般也不会真的用text,这样读起来很费劲,通常是存放大文件的地址,比如avator_url

-- 时间类型
-- date：     YYYY-MM-DD,占3个字节,范围是'1000-01-01' ~ '9999-12-31'
-- datetime： YYYY-MM-DD HH:MM:SS,占8个字节,范围是'1000-01-01 00:00:00' ~ '9999-12-31 23:59:59'
-- timestamp：YYYY-MM-DD HH:MM:SS,占4个字节,范围是'1970-01-01 00:00:00' ~ '2037-12-31 23:59:59',好处是insert/update时会以系统当前时间CURRENT_TIMESTAMP填充
SELECT NOW(),CURDATE(),CURTIME(),UTC_DATE(),UTC_TIME(),YEAR(NOW()),MONTH(NOW()),WEEK(NOW()),WEEKDAY(NOW()),DAYOFWEEK(NOW()),DAY(NOW()),DAYOFMONTH(NOW());
SELECT ADDDATE(NOW(),INTERVAL 1 DAY),ADDDATE(NOW(),INTERVAL -1 WEEK),ABS(DATEDIFF(CURDATE(),'2024-11-11')),ADDTIME('10:10:10',20),TIMEDIFF('12:00:00','10:00:00');
SELECT DATE_FORMAT(NOW(),'%Y年%m月%d日'),TIME_FORMAT(NOW(),'%H:%i:%s');
SELECT STR_TO_DATE('2024年04月20日','%Y年%m月%d日');  -- 将前端输入时间转换成标准时间
SELECT * FROM user_info WHERE MONTH(birthday) = MONTH(NOW());  -- 当月过生日
SELECT * FROM user_info WHERE DATE_FORMAT(birthday,'%m-%d') = DATE_FORMAT(NOW(),'%m-%d');  -- 当日过生日

-- 创建表
CREATE TABLE IF NOT EXISTS `user_info` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
`username` varchar(50) DEFAULT NULL COMMENT '用户名',
`password` varchar(500) DEFAULT NULL COMMENT '密码',
`age` tinyint DEFAULT NULL COMMENT '年龄',
`sex` tinyint DEFAULT NULL COMMENT '性别',
`birthday` date DEFAULT NULL COMMENT '生日',
`amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '金额',
`pay_time` datetime DEFAULT NULL COMMENT '支付时间',
`create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '删除标记',
PRIMARY KEY (`id`),                      -- 主键索引
unique `idx_name` (`username`),          -- 唯一索引
key `idx_age` (`age`),                   -- 单列索引
key `idx_sex_birth` (`sex`, `birthday`)  -- 联合索引
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- 删除表
drop table emp;
-- 重命名表
alter table emp rename to emp1;
-- 添加字段
alter table emp add column job varchar(20) after name;
-- 修改字段,modify只能修改属性,change既能修改属性也能修改名称
alter table emp modify column job varchar(60);
alter table emp change column job job1 varchar(60);
-- 删除字段
alter table emp drop column job1;

-- 插入数据,自增主键给null值
insert into emp values (null, 'grubby', 19, 'orc@163.com', '1990-01-01');
-- 修改字段值
update emp set name='sky',email='hum@123.com' where id = 2;
-- 关闭自动提交事务
set autocommit = false;
-- delete删除数据,在commit之前可以rollback
delete from emp; -- 不加where条件会删除所有数据(慎用!)
-- truncate清空表,相当于自动commit无法rollback,如果表有外键约束就不能使用
truncate table emp;
-- 事务一旦提交就无法回滚,所以回滚数据只能回滚到最近一次提交后的位置
commit;
rollback;
-- drop/truncate/delete区别？
-- drop直接删除表,truncate和delete删除数据表还在,有外键约束的表不能使用truncate
-- drop和truncate是ddl操作不涉及事务无法回滚,delete是dml操作涉及事务可以回滚
-- truncate是释放存储表数据使用的数据页,delete是逐行删除数据binlog会记录该操作,速度 drop > truncate > delete

-- select语句书写规则
select - from - (join) - where - group by - having - order by - limit
-- mysql数据库解析顺序
from - (join) - where - group by - having - select - order by - limit
-- where是分组前过滤,having是分组后过滤
select gender, avg(age) age_avg from emp where age > 19 group by gender;
-- where在select之前解析无法识别别名,having在select之后解析可以识别别名
select gender, avg(age) age_avg from emp group by gender having age_avg > 19;
-- 显示最近几次查询
show profiles;

-- 视图：将复杂的查询sql封装成虚拟表
-- 优点：sql语句重用,简化复杂sql(解耦),定制用户数据,安全(read-only)
create view view_name as select * from emp where email is not null;
-- 查看视图
select * from view_name;
-- 更新视图
create or replace view view_name as select * from emp where email is not null;
-- 删除视图
drop view view_name;

-- 元数据信息监控
-- 查询数据库有多少张表
select table_schema,count(*) as tables from information_schema.tables group by table_schema;
-- 查询表的行数、数据容量、索引容量
select table_schema,table_name,table_rows,data_length,index_length from information_schema.tables order by data_length desc,index_length desc;
-- 查询表中有多少字段
select count(*) from information_schema.columns where table_schema = '数据库名' and table_name = '表名';
-- 查询数据库中有多少字段
select count(column_name) from information_schema.columns where table_schema = '数据库名';
-- 查询数据库中所有表、字段、类型和注释
select table_name,column_name,data_type,column_comment from information_schema.columns where table_schema = '数据库名';

-- 添加外键约束(在一对多的多方添加),外键是另一个表的主键,用于关联操作,一个表可以有多个外键
alter table scores add constraint stu_sco foreign key(stuid) references students(id);
-- 也可以在创建表时直接外键约束
create table scores(
id int primary key auto_increment not null,
stuid int,
subid int,
score decimal(5,2),
foreign key(stuid) references students(id),
foreign key(subid) references subjects(id)
);
-- 此时插入或者修改数据时,如果stuid的值在students表中不存在则会报错
-- 外键的级联操作：在删除students表的数据时,如果这个id值在scores中已经存在会抛异常
-- 级联操作类型包括：
-- restrict(限制)：默认值,抛异常
-- cascade(级联)：如果主表的记录删掉,则从表中相关联的记录都将被删除
-- set null：将外键设置为空
-- no action：什么都不做
-- 删除外键约束
alter table scores drop foreign key stuid;
alter table scores add constraint stu_sco foreign key(stuid) references students(id) on delete cascade;
```

### engine
```sql
-- 查看所有存储引擎
mysql> show engines;
+------------+---------+------------------------------------------------------------+-----+-----+------------+
| Engine     | Support | Comment                                                    | TX  | XA  | Savepoints |
+------------+---------+------------------------------------------------------------+-----+-----+------------+
| InnoDB     | DEFAULT | Supports transactions, row-level locking, and foreign keys | YES | YES | YES |
| MyISAM     | YES     | MyISAM storage engine                                      | NO  | NO  | NO  |
| CSV        | YES     | CSV storage engine                                         | NO  | NO  | NO  |
| ARCHIVE    | YES     | Archive storage engine                                     | NO  | NO  | NO  |
+------------+---------+----------------------------------------------------------------+----+------+--------+
-- 查看当前默认存储引擎
mysql> show variables like '%engine%';
+----------------------------------+--------+
| Variable_name                    | Value  |
+----------------------------------+--------+
| default_storage_engine           | InnoDB |
| default_tmp_storage_engine       | InnoDB |
| disabled_storage_engines         |        |
| internal_tmp_disk_storage_engine | InnoDB |
+----------------------------------+--------+
# 查看innodb缓冲池大小
mysql> show variables like 'innodb_buffer_pool_size';
+-------------------------+-----------+
| Variable_name           | Value     |
+-------------------------+-----------+
| innodb_buffer_pool_size | 134217728 |
+-------------------------+-----------+
# 查看innodb日志文件大小
mysql> show variables like 'innodb_log_file_size';
+----------------------+----------+
| Variable_name        | Value    |
+----------------------+----------+
| innodb_log_file_size | 50331648 |
+----------------------+----------+
# 查看innodb日志缓冲区大小
mysql> show variables like 'innodb_log_buffer_size';
+------------------------+----------+
| Variable_name          | Value    |
+------------------------+----------+
| innodb_log_buffer_size | 16777216 |
+------------------------+----------+
# 查看innodb事务提交日志刷新策略
mysql> show variables like 'innodb_flush_log_at_trx_commit';
+--------------------------------+-------+
| Variable_name                  | Value |
+--------------------------------+-------+
| innodb_flush_log_at_trx_commit | 1     |
+--------------------------------+-------+
-- 修改表引擎
mysql> alter table test engine=innodb;
-- 存储引擎对比
-- Myisam：1.不支持事务和外键 2.表级锁,即使操作一条记录也会锁住整个表 3.只缓存索引不缓存数据
-- Innodb：1.支持事务和外键 2.行级锁,只锁定操作的行,适合高并发操作 3.既缓存索引也缓存数据,对内存要求较高
-- 行级锁
-- 共享锁(读锁)：事务A加共享锁后,其它事务也只能加共享锁,可以并发读取数据但不能做任何修改
-- 排它锁(写锁)：事务A加排它锁后,其它事务不能加任何锁,只能等排它锁释放,innodb会在insert/update/delete数据时加排它锁,select不会加任何锁
```

### tx
```sql
-- 事务就是对表的更新操作(insert/delete/update),使数据从一种状态变换到另一种状态,有acid四大特性
-- 原子性(atomicity)：一组事务中的所有操作要么全部成功(commit),要么全部失败(rollback),并且一旦提交就无法回滚
-- 一致性(consistency)：几个并行执行的事务其执行结果和执行顺序无关
-- 隔离性(isolation)：事务的执行不受其他事务的干扰
-- 持久性(durability)：已提交的事务对数据库的改变是永久生效的

-- 什么时候会提交数据？
-- a.执行DML操作,默认情况下一旦执行完就会自动提交数据 -> set autocommit = false
-- b.一旦断开数据库连接,也会提交数据 -> 将获取conn步骤从update方法中剥离出来单独关闭

-- 事务并发问题
-- 脏读：读到了别的事务还未提交的数据,这些数据可能会回滚也就是最终不一定存在的数据
-- 不可重复读：在一个事务内,最开始读到的数据和事务结束前任意时刻读到的同一批数据不一致,针对update
-- 幻读：事务A更新了数据但还未提交,此时事务B插入了与事务A修改之前相同的行数据且提交了,然后在事务A中查询发现刚才的修改好像没起作用,针对insert

-- 事务隔离级别：由于事务隔离是通过加锁实现的,所以隔离强度递增性能递减
-- 读未提交：不加锁,性能最好,但是相当于裸奔,连脏读都无法解决(不考虑)
-- 读已提交：事务A只能读到事务B已提交的的数据,解决脏读,但是做不到可重复读,也无法解决幻读(oracle默认)
-- 可重复读：事务A读不到事务B已提交的数据,事务A开始时数据啥样在事务A提交前都不会变,解决脏读和不可重复读但可能会幻读(mysql默认)
-- 序列化：加锁,将事务变成顺序执行,相当于单线程,性能最差(不考虑)
-- 查看事务隔离级别
mysql> show variables like 'tx_isolation' / select @@tx_isolation
+-----------------------+-----------------+
| Variable_name         | Value           |
+-----------------------+-----------------+
| transaction_isolation | REPEATABLE-READ |
+-----------------------+-----------------+
-- 修改事务隔离级别,重启生效
mysql> set global transaction isolation level read committed;
-- 查看当前正在运行的事务
mysql> select * from information_schema.innodb_trx;
# 查看是否启用全局事务标识符
mysql> show variables like 'gtid_mode';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| gtid_mode     | OFF   |
+---------------+-------+
```

### join
```sql
-- a表和b表公共数据
select * from t1 a inner join t2 b on a.id=b.id;
-- a表全集
select * from t1 a left join t2 b on a.id=b.id;
-- b表全集
select * from t1 a right join t2 b on a.id=b.id;
-- a表独有(在a表不在b表,hive可以写成left semi join)
select * from t1 a left join t2 b on a.id=b.id where b.id is null;
-- b表独有
select * from t1 a right join t2 b on a.id=b.id where a.id is null;
-- a表和b表全部数据
select * from t1 a full join t2 b on a.id=b.id;  -- mysql不支持full join,使用如下方式替代
select * from t1 a left join t2 b on a.id=b.id union select * from t1 a right join t2 b on a.id=b.id;
-- a表独有 + b表独有
select * from t1 a full join t2 b on a.id=b.id where a.id is null or b.id is null;
select * from t1 a left join t2 b on a.id=b.id where b.id is null union all select * from t1 a right join t2 b on a.id=b.id where a.id is null;
-- 笛卡尔积,不写on条件时join/inner join/cross join是等价的
select * from t1 a join t2 b;

-- left join数据量一定和左表相等吗？
-- 不是的,数据量>=左表,取决于关联的key在表中的数据是否唯一,比如a.name=b.name但是b表中name不止一条记录结果集就会大于a表

-- 表关联时on条件和where条件的区别？
-- 如果是inner join效果一样,如果是left join会有区别,数据库表关联时会生成一个临时表,然后将临时表返回给用户
mysql> select * from a left join b on a.id=b.id and a.name='李四' and b.age=18;  -- on是生成临时表时使用的条件,左连接会返回左表所有记录,右表匹配不上就写null
+----+------+------+------+
| id | name | id   | age  |
+----+------+------+------+
|  1 |  张三 | null | null |
|  2 |  李四 |  2   | 18   |
|  3 |  王五 | null | null |
+----+------+------+------+
mysql> select * from a left join b on a.id=b.id where a.name='李四' and b.age=18;  -- where是临时表生成之后再对数据过滤,其实跟left join已经没关系了
+----+------+------+------+
| id | name | id   | age  |
+----+------+------+------+
|  2 |  李四 |  2   | 18   |
+----+------+------+------+
```

### index
```sql
-- 索引：除了数据以外,数据库还维护着满足特定查找算法的数据结构,以某种方式指向物理数据,从而实现高级查找算法,这种数据结构就是索引
-- 优点：索引是一种排好序的快速查找数据结构,B+树(多路平衡查找树)存储,类似字典目录,可以提高数据检索效率降低IO成本和数据排序成本
-- 缺点：在insert/update/delete数据时要连带索引一起更新,会降低表的更新速度
-- 为啥使用B+树：查询时访问磁盘的次数由树的层数决定,二叉树只有左右两个子节点而B+树可以有多个子节点,减少树的高度

-- 适合建立索引的场景：主键/频繁查询字段/外键(join)/过滤字段(where)/分组字段(group)/排序字段(order),通过索引访问将大大提高检索效率
-- 不适合建立索引场景：表记录数很少(mysql优化器会忽略索引直接全表扫描)/频繁更新字段
-- 索引失效场景：在索引列做计算/函数/类型转换等任何操作 price/100 = 3 -> price = 100 * 3 | substr(name,1,3) = 'orc' -> name like 'orc%'
-- or两边列必须都有索引 | 使用!=或<> | like '..%'可以,like '%..'不行 | is null 可以,is not null 不行
-- 创建索引
alter table emp add primary key emp (id);      -- 主键索引,设定为主键后数据库会自动创建索引且索引列的值唯一非空
create unique index idx_name on emp (name);    -- 唯一索引,索引列的值必须唯一但允许有空值
create index idx_name on emp (name);           -- 单值索引,索引只包含一个列
create index idx_name_age on emp (name, age);  -- 联合索引,索引包含多个列
create index idx_name on emp (name desc);      -- 倒叙索引
-- 查看索引
show index from emp;
-- 删除索引
drop index idx_name on emp;

-- 主键索引(聚簇索引)：叶子结点存放整行数据,如果没有主键mysql会创建rowid来组织这棵B+树,从而将数据有规律的存储起来
-- 非主键索引(二级索引)：叶子结点存放主键值,查询时先搜索二级索引树找到主键值,再回到主键索引树搜索整行数据的其它列,该过程叫回表
-- 覆盖索引：从二级索引就能找到所有查询列,避免回表,减少索引树的搜索次数,explain输出结果Extra=Using index表示使用了覆盖索引
select id,name from emp where name='grubby';  -- create index idx_name on emp (name)
select age from emp where name='grubby';      -- create index idx_name_age on emp (name,age)
-- 最左匹配原则：联合索引的查询是从最左前列开始,如果跳过中间列或使用非等值查询会导致后边列索引失效,所以尽量将过滤性好的列放前面
create index idx_a_b_c on emp (a,b,c);        -- 联合索引,树上每个节点均同时包含a,b,c字段且a全局有序b局部有序(当a相同时),以此类推
select * from emp where a=3 and b=4 and c=5;  -- Y a,b,c都使用了索引
select * from emp where b=4 and c=5;          -- N 跳过a,后面都断了
select * from emp where a=3 and c=5;          -- Y 只使用了a,跳过b,后面c断了
select * from emp where a=3 and b>4 and c=5;  -- Y 只使用了a,b,联合索引只能保证局部有序,非等值查询的后续字段无法直接通过索引树确定范围,需要回表
-- 索引下推：mysql5.6将与索引有关的条件判断由mysql服务器向下传递至存储引擎,减少IO次数,主要用来优化联合索引中索引失效的情况
-- explain输出结果Extra=Using index condition(ICP)表示使用了索引下推
select * from emp where name like '陈%' and age=20;
-- 未开启ICP,存储引擎只会搜索idx_name_age这棵树上的name列,age列需要回表再过滤,如果有10个姓陈的就需要回表10次
-- 开启ICP后,存储引擎在索引内部就过滤了age=20这个条件,减少回表次数,其实就是充分利用索引,尽量在查询出整行数据之前先过滤掉无效数据

-- 关联查询优化：left join左表是主表(驱动表),右表是从表(被驱动表),左表数据会全表扫描,关联条件用来确定右表搜索的行,所以尽量将小表放左边,
-- 由于驱动表会全表扫描,即使添加索引虽然能用上但扫描行数不变,应当给被驱动表的关联字段建索引,如果是inner join mysql会自动将小表作为驱动表
select * from a left join b on a.id=b.id;  -- create index idx_id on b (id)

-- 排序分组优化：尽量避免出现Extra=Using filesort,但是当排序之前有过滤操作时优先给过滤字段加索引
create index idx_a_b_c on emp (a,b,c);    -- 是否出现filesort
select * from emp order by d;             -- Y 排序字段没有索引
select * from emp order by a;             -- Y 排序字段有索引但不是覆盖索引,所以尽量减少使用select *
select a,b,c from emp order by a,b;       -- N 排序字段有索引且是覆盖索引,很合理
select a,b,c from emp order by a,c;       -- Y 排序字段有索引但跳过了中间列
select a,b,c from emp order by a,b desc;  -- Y 排序字段中同时存在升序(默认)和降序,而索引都是升序的
select a,b,c from emp order by b,a;       -- Y 排序字段顺序和索引顺序不一致,此处mysql优化器也不能调换b和a的位置,会改变排序逻辑
select a,b,c from emp where a<100 order by b;  -- Y 过滤条件是范围查询,但这样是值得的,过滤掉大量数据提升的性能要远远超过使用索引
-- in和exists：主查询和子查询的执行顺序不一样,in先执行子查询适合内表小外表大的情况,exists先执行主查询适合外表小内表大的情况
select * from t1 where id in (select id from t2);
select * from t1 where exists (select 1 from t2 where t1.id=t2.id);
-- not in无法使用索引且子查询结果集不能有null否则直接返回null,而not exists会使用索引性能更高,所以应尽量避免使用not in
select * from a where id not in (select id from b)  -- 改进为select * from a left join b on a.id=b.id where b.id is null
-- mysql优化器会改变sql语句中select和where字段的顺序,但是group和order字段的顺序是不能变的,否则业务逻辑就变了
```

### explain
```sql
-- 执行计划：可以查看表的读取顺序,索引使用情况,扫描行数等
-- 结合type/key/key_len/rows这些指标判断是否要建索引,如果涉及排序则主要分析Extra指标的Using filesort
mysql> explain select * from order_detail a join commodity b on a.spu_id = b.id where a.order_id = '15483062' and a.spu_id > 0;
+----+-------------+-------+------------+--------+---------------+---------------+---------+----------+------+----------+------------------------------------+
| id | select_type | table | partitions | type   | possible_keys | key           | key_len | ref      | rows | filtered | Extra                              |
+----+-------------+-------+------------+--------+---------------+---------------+---------+----------+------+----------+------------------------------------+
|  1 | SIMPLE      | a     | p997       | ref    | idex_order_id | idex_order_id | 8       | const    |    4 |    33.33 | Using index condition; Using where |
|  1 | SIMPLE      | b     | NULL       | eq_ref | PRIMARY       | PRIMARY       | 4       | a.spu_id |    1 |   100.00 | NULL                               |
+----+-------------+-------+------------+--------+---------------+---------------+---------+----------+------+----------+------------------------------------+
-- id表示select执行顺序,id相同时从上往下,id不同时子查询id序号会递增且id越大执行优先级越高,每个id都是一次独立查询
-- select_type表示查询类型,主要用于区别普通查询、关联查询、子查询等
SIMPLE 简单查询, select * from t1 where id = 3;
PRIMARY 包含子查询或union的最外层查询, select * from t1 union select * from t2;  -- t1是PRIMARY,t2是UNION
DERIVED from列表中包含的子查询, select * from (select id,count(1) c from t1 group by id) t2 where c > 10;  -- t1是DERIVED,t2是PRIMARY
SUBQUERY where列表中包含的第一个子查询, select * from t1 where id in (select id from t2);  -- t1是PRIMARY,t2是SUBQUERY
DEPENDENT SUBQUERY where列表中包含的第一个依赖外部查询的子查询, select * from t1 where exists (select 1 from t2 where t1.id = t2.id);  -- t2是DEPENDENT SUBQUERY 
-- table表示查询的表
-- type表示查询类型,性能system > const > eq_ref > ref > range > index > ALL,一般至少要达到range级别(重点)
system 表中只有一行数据,是const特例
const 通过索引一次就找到了,常见于primary key和unique, select * from t1 where id = 1;
eq_ref 唯一性索引扫描,返回匹配的唯一记录,常见于primary key和unique, select * from t1 left join t2 on t1.id = t2.id;
ref 非唯一性索引扫描,返回匹配的所有记录, select * from t1 left join t2 on t1.job = t2.job;
ref_or_null 某个字段既需要指定值也要null值, select * from t1 where t1.name = 'grubby' or t1.name is null;
index_merge 需要多个索引组合使用,常见于and和or语句, select * from t1 where t1.id = 10 or t1.name = 'grubby';
index/unique_subquery 子查询中使用了(唯一)索引, select * from t1 where t1.id in (select t2.id from t2);
range 只检索给定范围的行,key列会显示使用了哪个索引,常见于where语句, select * from t1 where t1.id > 10;
index 使用了索引但是没有通过索引进行过滤,会扫描索引树效率比ALL高,因为索引树比数据文件小很多, select * from t1;
ALL 全表扫描,必须优化
-- possible_keys表示可能用到的索引,某个字段存在索引就会被列出来,但不一定被查询实际使用
-- key表示实际使用的索引(重点)
-- key_len表示索引中使用的字节数,可以帮助检查是否充分利用了索引(重点)
先看索引列的字段类型+长度, int=4(int占4个字节,最大值2^31 - 1,所以是int(11)) | varchar(32)=32 | char(32)=32  
varchar和char要视字符集乘以不同的值(utf-8 * 3 | gbk * 2),varchar是动态字符串要加2个字节,允许为空的字段要加1个字节
-- ref表示索引的哪一列被使用了
-- rows表示查询时检索的行数,越少越好(重点)
-- Extra(针对排序操作,尽量把Using filesort变成Using index)
Using where 表示使用了条件过滤
Using index 表示使用了覆盖索引
Using filesort(重点) 表示排序字段没有通过索引访问,mysql中无法利用索引完成的排序操作称为"文件排序"
Using temporary 表示对查询结果排序或分组时使用了临时表
```

### log
```sql
-- 查看mysql文件存储位置
mysql> show variables like 'datadir';
+---------------+------------------------+
| Variable_name | Value                  |
+---------------+------------------------+
| datadir       | /usr/local/mysql/data/ |
+---------------+------------------------+
-- 查看mysql错误日志
mysql> show variables like 'log_error';
+---------------+----------------------------------------+
| Variable_name | Value                                  |
+---------------+----------------------------------------+
| log_error     | /usr/local/mysql/data/mysqld.local.err |
+---------------+----------------------------------------+
-- 查看mysql慢查询日志
mysql> show variables like 'slow_query_log%';
+---------------------+--------------------------------+
| slow_query_log      | OFF                            |
| slow_query_log_file | /usr/local/mysql/data/slow.log | # 可以监控该文件优化速度慢的sql,但是手工查找不方便可借助工具
+---------------------+--------------------------------+
-- 查询时间超过几秒就记录到慢查询日志
mysql> show variables like 'long_query_time';
+-----------------+-----------+
| long_query_time | 10.000000 |
+-----------------+-----------+
mysql> set global slow_query_log = 1;
mysql> set global long_query_time = 5;
-- 修改配置文件后重启mysqld服务
[root@cdh1 ~]$ vim /etc/my.cnf && systemctl restart mysqld
[mysqld]
slow_query_log=1
slow_query_log_file=/var/lib/mysql/cdh1-slow.log
long_query_time=5

-- mysqldumpslow日志分析工具
-s, --sort  # 排序方式, c 访问次数 | r 返回记录数 | t 查询时间 | l 锁定时间
-t, --top   # 返回前多少条记录
-g, --grep  # 匹配字符串
mysqldumpslow -s c -t 10 /var/lib/mysql/cdh1-slow.log | more                 # 获取访问次数最多的前10条sql,结合more使用防止爆屏
mysqldumpslow -s r -t 10 /var/lib/mysql/cdh1-slow.log | more                 # 获取返回记录最多的前10条sql
mysqldumpslow -s t -t 10 -g "left join" /var/lib/mysql/cdh1-slow.log | more  # 获取耗时最长且包含左连接的前10条sql

-- 查询所有用户正在干什么
mysql> show processlist;
+----+------+-----------+------+---------+------+----------+------------------+
| Id | User | Host      | db   | Command | Time | State    | Info             |
+----+------+-----------+------+---------+------+----------+------------------+
| 11 | root | localhost | test | Query   |    0 | starting | show processlist |
+----+------+-----------+------+---------+------+----------+------------------+
-- 杀掉进程,重新连接Id会递增
mysql> kill 11;
ERROR 1317 (70100): Query execution was interrupted
```

### binlog
```shell script
# binlog以事件形式记录除select和show以外的所有DDL和DML语句,binlog日志是事务安全的,常用于mysql的主从复制和数据恢复
# mysql主从复制原理：master主库将更新记录写进binary log中,slave从库拷贝binary log并重做其中的事件,canal和maxwell就是伪装成slave
# 开启binlog
[root@cdh1 ~]$ vim /etc/my.cnf && systemctl restart mysqld
[mysqld]
server_id=1        # 配置mysql replication时定义,不能和canal的slaveId重复
log-bin=mysql-bin  # binlog日志前缀
binlog_format=row  # binlog格式为row,只记录行记录变化后的结果,保证数据绝对一致性,canal和maxwell的配置也只针对row格式才生效
# 查看服务器唯一id
mysql> show variables like 'server_id';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| server_id     | 1     |
+---------------+-------+
# 查看是否开启
mysql> show variables like '%log_bin%' \g  # sql语句结尾加上\g表示界定符相当于分号,加上\G表示将查询结果按列打印输出内容过多时使用
+---------------------------------+---------------------------------------+
| Variable_name                   | Value                                 |
+---------------------------------+---------------------------------------+
| log_bin                         | ON                                    |
| log_bin_basename                | /usr/local/mysql/data/mysql-bin       |
| log_bin_index                   | /usr/local/mysql/data/mysql-bin.index |
| log_bin_trust_function_creators | OFF                                   |
| log_bin_use_v1_row_events       | OFF                                   |
| sql_log_bin                     | ON                                    |
+---------------------------------+---------------------------------------+
# 查看二进制日志格式
mysql> show variables like 'binlog_format';
+---------------+-------+
| Variable_name | Value |
+---------------+-------+
| binlog_format | ROW   |
+---------------+-------+
# 查看binlog日志的工具,如果data目录进不去就 chmod -R a+rwx /usr/local/mysql/data 
[root@cdh1 ~]$ cd /usr/local/mysql/data && mysqlbinlog mysql-bin.000001
# 查看binlog日志列表
mysql> show binary logs;
+------------------+-----------+
| Log_name         | File_size |
+------------------+-----------+
| mysql-bin.000001 |       201 |
+------------------+-----------+
# 查看当前正在写入的binlog日志状态,初始大小是154,此时还没有记录更新
mysql> show master status;
+------------------+----------+--------------+------------------+-------------------+
| File             | Position | Binlog_Do_DB | Binlog_Ignore_DB | Executed_Gtid_Set |
+------------------+----------+--------------+------------------+-------------------+
| mysql-bin.000001 |      154 |              |                  |                   |
+------------------+----------+--------------+------------------+-------------------+
# 刷新日志,会生成新的binlog文件
mysql> flush logs;
# 查看binlog文件内容,默认第一个也可以手动指定
mysql> show binlog events [in 'mysql-bin.000002'] \G
+------------------+-----+----------------+-----------+-------------+----------------------------------------------+
| Log_name         | Pos | Event_type     | Server_id | End_log_pos | Info                                         |
+------------------+-----+----------------+-----------+-------------+----------------------------------------------+
| mysql-bin.000013 |   4 | Format_desc    |         2 |         125 | Server ver: 8.0.20, Binlog ver: 4            |
| mysql-bin.000013 | 125 | Previous_gtids |         2 |         196 | 4bb5251c-f391-11ec-b5da-525400667343:1-19273 |
+------------------+-----+----------------+-----------+-------------+----------------------------------------------+
# 清空binlog
mysql> reset master;
# 先导入初始测试数据,不然canal启动时读不到数据
mysql> create database canal/maxwell charset=utf8;
mysql> source mock.sql
# 模拟更新数据
[root@cdh1 ~]$ vim application.properties
[root@cdh1 ~]$ java -jar mock-db.jar
# 注意：要给canal/maxwell账号读取binlog的权限REPLICATION CLIENT & SLAVE
mysql> grant all on *.* to 'canal'@'%' identified by 'canal';
mysql> grant all on *.* to 'maxwell'@'%' identified by 'maxwell';
```

### canal
```shell script
# 安装(单机版,canal很少宕机且单节点足够用所以不需要HA)
# 集群：多台服务器干相同的活(两个厨师炒菜) | 分布式：多台服务器干不同的活(一个厨师炒菜一个小二传菜) | 高可用：多台服务器一个干活别的备份
# https://github.com/alibaba/canal/releases
[root@cdh1 ~]$ tar -xvf canal.deployer-1.1.6.tar -C /Users/okc/modules
# canal服务端配置(修改后先stop再startup,不然bin/canal.pid一直存在,example/meta.dat会记录mysql-bin.xxx的position,所以不会丢数据)
[root@cdh1 ~]$ vim conf/canal.properties
canal.port = 11111                       # canal默认端口11111,当启动多个实例时会显示端口被占用"java.net.BindException: Address already in use",可通过lsof -i :port查看端口占用情况,此时需要将端口往后顺延
canal.serverMode = kafka                 # 将canal输出到kafka,默认是tcp输出到canal客户端通过java代码处理
canal.mq.servers = cdh1:9092,cdh2:9092   # kafka地址,逗号分隔
canal.destinations = example1,example2   # canal默认单实例,可以拷贝conf/example配置多实例,通常一个ip对应一个instance
canal.instance.filter.query.ddl = true   # canal默认抓所有binlog,可以过滤ddl语句,防止CREATE TABLE等语句解析异常
canal.instance.filter.dml.delete = true  # 过滤delete数据,比如要删历史数据时会有巨量数据瞬间涌入kafka
canal.instance.filter.transaction.entry = true  # 过滤事务操作,不然会抓到"type":"XACOMMIT"

# instance实例配置(修改后直接生效不用重启)
[root@cdh1 ~]$ vim conf/example/instance.properties
canal.instance.master.address={ip:port}  # mysql地址
canal.instance.dbUsername=canal          # 连接mysql的用户名和密码,需事先创建并授权
canal.instance.dbPassword=canal
canal.instance.defaultDatabaseName=ods   # 指定库(这个配置貌似无效,要看白名单和黑名单的配置)
canal.instance.filter.regex=.*\\..*      # 白名单表 .*\\..* 所有表 | ods\\..* ods库下表 | ods\\.ods.* ods库下ods开头表 | ods.order指定表
canal.instance.filter.black.regex=       # 黑名单表
canal.mq.topic=canal                     # 指定kafka的topic
# binlog是有序的,如何保证写入mq的消息也有序？
# 方案1.将消息发往一个partition,避免因为网络延迟导致分区间消息无序,但是吞吐量低性能较差(默认)
canal.mq.partition=0
# 方案2.将消息发往多个partition,按照主键进行hash保证相同id的更新记录进入同一个partition(推荐)
canal.mq.partitionsNum=3
canal.mq.partitionHash=.*\\..*:id  # 设置regex匹配到的表的hash字段 .*\\..*:id | .*\\..*:$pk$ | ${db}.${table}:${pk}

# 启动/关闭canal
[root@cdh1 ~]$ startup.sh/stop.sh  # jps出现CanalLauncher进程说明启动成功,同时会创建instance.properties中配置的kafka主题canal
# 查看日志
[root@cdh1 ~]$ cat logs/canal/canal.log
the canal server is running now ......
[root@cdh1 ~]$ cat logs/example/example.log
find start position successfully, the next step is binlog dump

# 启动kafka消费者
[root@cdh1 ~]$ kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ods_base_db
# 往mysql插入数据,或者运行mock-db.jar生成模拟数据,kafka消费者能接收到说明ok
mysql> INSERT INTO z_user_info VALUES(9,'aaa'),(10,'bbb');

# mysql加字段导致canal报错 CanalParseException: column size is not match for table:user.user_info,15 vs 14
# 进入/data/canal-label/logs/example1日志目录,发现meta.log不刷新了说明程序已中断,再去查看example1.log分析具体原因
# canal缓存的表结构信息存放在h2.mv.db文件,直接删除后重启,如果想在指定位置断点续传可以修改meta.dat的position和timestamp

# canal数据格式
# "id"：生成的消息id,对应一次事务操作 | "es"：binlog时间戳 | "ts"：connector时间戳 | "old"：变更前的字段信息
# canal/maxwell输出数据的"old"字段会显示update前的数据,业务处理非常有用,FlinkCdc也可以比较"before"和"after"判断具体更新情况
```

### maxwell
- [maxwell官网](https://maxwells-daemon.io/)
```shell script
# 优点：1.能抓历史数据 2.数据格式更加轻量级,canal返回的是包含多条记录的数组,maxwell返回的是一条记录
# 缺点：默认抓所有库,每次都要修改/etc/my.cnf添加binlog-do-db=xxx并重启才能生效,对数据库侵入性太强
# 安装(单机版),查看Changelog发现从1.30.0版本开始不支持jdk8
[root@cdh1 ~]$ tar -xvf maxwell-1.29.2.tar.gz -C /User/okc/modules
# 修改配置文件
[root@cdh1 ~]$ vim config.properties
host=localhost                              # mysql地址
user=maxwell                                # 连接mysql的用户名和密码,需事先创建并授权
password=maxwell
filter= exclude: *.*, include: db1.*        # 指定过滤规则,include: db1./lesson_\d+/  
jdbc_options=useSSL=false&serverTimezone=Asia/Shanghai
client_id=m01
producer=kafka
kafka.bootstrap.servers=localhost:9092      # kafka地址
kafka_topic=ods_base_db                     # 指定kafka的topic
producer_partition_by=database              # 指定分区规则[database/table/primary_key/column],不然都会发到topic的0号分区
# 初始化maxwell元数据库
mysql> create database maxwell;
# 启动maxwell,默认增量同步数据
[root@cdh1 ~]$ bin/maxwell --config config.properties > maxwell.log 2>&1 &
# 此时元数据库会生成几张表bootstrap,columns,databases,heartbeats,positions,schemas
mysql> select * from positions;
+-----------+------------------+-----------------+----------+-----------+--------------+---------------------+
| server_id | binlog_file      | binlog_position | gtid_set | client_id | heartbeat_at | last_heartbeat_read |
+-----------+------------------+-----------------+----------+-----------+--------------+---------------------+
|         1 | mysql-bin.000029 |        27789920 | NULL     | maxwell   |         NULL |       1678763426269 |
+-----------+------------------+-----------------+----------+-----------+--------------+---------------------+
# maxwell支持全量同步历史数据,必须先开启增量才能使用
[root@cdh1 ~]$ bin/maxwell-bootstrap --database mock --table base_trademark --config config.properties
connecting to jdbc:mysql://localhost:3306/maxwell?allowPublicKeyRetrieval=true&connectTimeout=5000&serverTimezone=Asia%2FShanghai&zeroDateTimeBehavior=convertToNull&useSSL=false
# maxwell日志
11:15:52,014 INFO  SynchronousBootstrapper - bootstrapping started for mock.base_trademark
11:15:52,135 INFO  SynchronousBootstrapper - bootstrapping ended for #1 mock.base_trademark  # 1是bootstrap表的主键id
# 此时元数据库的bootstrap表会生成全量同步的记录
mysql> select * from bootstrap;
+----+---------------+---------------+--------------+-------------+---------------+------------+------------+---------------------+---------------------+-------------+-----------------+-----------+---------+
| id | database_name | table_name    | where_clause | is_complete | inserted_rows | total_rows | created_at | started_at          | completed_at        | binlog_file | binlog_position | client_id | comment |
+----+---------------+---------------+--------------+-------------+---------------+------------+------------+---------------------+---------------------+-------------+-----------------+-----------+---------+
|  1 | mock          | base_trademark | NULL        |           1 |            11 |         11 | NULL       | 2023-04-10 11:22:52 | 2023-04-10 11:22:52 | NULL        |               0 | maxwell   | NULL    |
+----+---------------+---------------+--------------+-------------+---------------+------------+------------+---------------------+---------------------+-------------+-----------------+-----------+---------+
# kafka会收到一条元数据库bootstrap表的数据,然后bootstrap-start/complete是开始和结束的标志,bootstrap-insert才是数据更新记录
{"database":"maxwell","table":"bootstrap","type":"insert","ts":1681096551,"xid":1168952,"commit":true,"data":{"id":5,"database_name":"mock","table_name":"base_trademark","where_clause":null,"is_complete":0,"inserted_rows":0,"total_rows":11,"created_at":null,"started_at":null,"completed_at":null,"binlog_file":null,"binlog_position":0,"client_id":"maxwell","comment":null}}
{"database":"mock","table":"base_trademark","type":"bootstrap-start","ts":1681096552,"data":{}}
{"database":"mock","table":"base_trademark","type":"bootstrap-insert","ts":1681096552,"data":{"id":1,"tm_name":"苹果","logo_url":"/static/default.jpg"}}
{"database":"mock","table":"base_trademark","type":"bootstrap-complete","ts":1681096552,"data":{}}
# 关闭maxwell
[root@cdh1 ~]$ ps -ef | grep maxwell | grep -v grep | awk '{print $2}' | xargs kill -9
```