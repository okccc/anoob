### sqoop
```shell
# 下载
[root@cdh1 ~]$ wget http://archive.apache.org/dist/sqoop/1.4.7/sqoop-1.4.7.bin__hadoop-2.6.0.tar.gz
# 安装
[root@cdh1 ~]$ tar -xvf sqoop-1.4.7.bin__hadoop-2.6.0.tar.gz -C /opt/module
# 修改配置文件
[root@cdh1 ~]$ vim sqoop-env.sh
export HADOOP_COMMON_HOME=/opt/module/hadoop-3.1.3
export HADOOP_MAPRED_HOME=/opt/module/hadoop-3.1.3
export HIVE_HOME=/opt/module/hive
export ZOOKEEPER_HOME=/opt/module/zookeeper-3.5.7
export ZOOCFGDIR=/opt/module/zookeeper-3.5.7/conf
# 拷贝jdbc驱动
[root@cdh1 ~]$ cp mysql-connector-java-5.1.48.jar /opt/module/sqoop/lib/
# 测试连接
[root@cdh1 ~]$ bin/sqoop list-databases --connect jdbc:mysql://localhost:3306/ --username root --password root@123
```

### import
```shell
bin/sqoop import \  # 反斜杠是换行符,最终会拼接成一行字符串执行,不然linux会以为是多行命令
--connect jdbc:mysql://${ip}:${port}/${mysql_db} \  # 数据库
--username ${username} \                            # 用户名
--password ${password} \                            # 密码
--table ${table} \                                  # 表名
--columns id,name \                                 # 筛选列,逗号分隔
--where 'id >= 1 and id <= 20' \                    # 筛选行,中间有空格必须加引号,不然会被当成多个参数,如果要解析变量必须使用""
# 上面三个参数可以用--query替换,结尾要加上$CONDITIONS占位符,用来根据map任务数拆分过滤条件,1<=id<=10 & 11<=id<=20
--query 'select id,name from user_info where id >= 1 and id <= 20 and $CONDITIONS' \  # 全量/增量具体由过滤条件体现
--target-dir ${path} \                              # hdfs路径
--delete-target-dir \                               # 执行mr之前先删掉已存在目录,这样任务可以多次重复执行
--fields-terminated-by '\001' \                     # mysql是结构化数据而hdfs是文件,列之间要指定分隔符,注意区分'\001'和'\t'
--num-mappers 2 \                                   # sqoop并行度,也就是map任务个数,因为没有reduce所以hdfs文件个数=map任务个数
--split-by id \                                     # sqoop切片策略,map端按照id将数据切片,如果只有1个map就不需要切片
--compress \                                        # 压缩数据(可选)
--compression-codec lzop \                          # 指定压缩格式为lzo,hdfs会生成.lzo结尾的数据文件和.lzo.index结尾的索引文件
--null-string '\\N' \                               # 将字符串空值转换成\N,因为hdfs数据最终会被加载进hive表,hive中的空值就是\N
--null-non-string '\\N' \                           # 将非字符串空值转换成\N
```