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

# 同步策略：考虑数据量大小及数据是否变化分为全量表、增量表、新增及变化表、特殊表
# shell中单引号、双引号、反引号区别：''和'""'不执行$ | ""和"''"会执行$取变量值,不想执行就加\转义符 | ``执行一行命令获得结果
bin/sqoop import \  # 反斜杠是换行符,最终会拼接成一行字符串执行,不然linux会以为是多行命令
--connect jdbc:mysql://${ip}:${port}/${mysql_db} \  # 数据库
--username ${username} \                            # 用户名
--password ${password} \                            # 密码
--table ${table} \                                  # 表名
--columns id,name \                                 # 筛选列,逗号分隔
--where "create_time=${dt} or update_time=${dt}" \  # 筛选行,中间有空格必须加引号,不然会被当成多个参数,如果要解析变量必须使用""
# 可以替换上面三个参数,结尾必须写$CONDITIONS占位符,用来根据map任务数将过滤条件拆分成1<=id<=10 & 11<=id<=20(貌似有点问题)
--query "select * from ${table} where xxx and \$CONDITIONS" \  # 全量/增量/新增及变化由过滤条件体现
--target-dir /user/hive/warehouse/ods.db/${table}   # hdfs路径
--delete-target-dir \                               # 执行mr之前先删掉已存在目录,这样任务可以多次重复执行
--fields-terminated-by '\001' \                     # mysql是结构化数据而hdfs是文件,列之间要指定分隔符,注意区分'\001'和'\t'
--num-mappers 2 \                                   # sqoop并行度,也就是map任务个数,因为没有reduce所以hdfs文件个数=map任务个数
--split-by id \                                     # sqoop切片策略,map端按照id将数据切片,如果只有1个map就不需要切片
--null-string '\\N' \                               # 转换字符串空值,mysql中的null对应hive中的\N
--null-non-string '\\N' \                           # 转换非字符串空值
--compress \                                        # 压缩数据(可选)
--compression-codec lzop \                          # 指定压缩格式为lzo,hdfs会生成.lzo结尾的数据文件和.lzo.index结尾的索引文件
```

### mysql2hive.sh
```shell
#!/bin/bash

# 判断参数个数
if [ $# -eq 1 ]; then
    table=$1
    dt=`date -d yesterday +%Y%m%d`  # `date -d '-1 day' +%Y%m%d`
elif [ $# -eq 2 ]; then
    table=$1
    dt=$2
else
    echo "wrong args[] numbers"
    exit 1
fi

username=eadmin
password='EWQTB512Oikf;'  # 密码字符串最好加''防止有$等特殊字符导致access denied
# 日志路径
log_path=/home/hive/gds/logs/sqoop/input/${YESTERDAY}
watch_path=/home/hive/gds/filewatch/sqoop/input/${YESTERDAY}

if [[ ! -d ${log_path} ]] ; then
    mkdir ${log_path}
fi
if [[ ! -d ${watch_path} ]] ; then
    mkdir ${watch_path}
fi

# 数据导入
sqoop import
--connect jdbc:mysql://${ip}:${port}/${db}?tinyInt1isBit=false    # 数据库
--username ${username}                                            # 用户名
--password ${password}                                            # 密码
--table ${table}                                                  # mysql表
--columns id,name                                                 # 筛选字段(一般不写默认取所有字段)
--where "create_time=${dt} or update_time=${dt}"                  # 筛选数据(增量表、新增及变化表,不写就是全量表)
--fields-terminated-by '\001'                                     # 字段分隔符,注意区分'\001'和'\t'
--hive-drop-import-delims                                         # 过滤掉分隔符\n, \r, \01
--hive-import                                                     # 导入hive,不设置就使用hive默认分隔符
--hive-table base.${table}                                        # hive表
-m 4                                                              # -m表示map数量,sqoop任务没有reduce阶段
--delete-target-dir                                               # 删除已存在的目录
--hive-overwrite                                                  # 覆盖数据
--hive-partition-key dt                                           # 分区字段dt(增量选项)
--hive-partition-value ${dt}                                      # 分区值(增量选项)
--null-string '\\N'                                               # 将字符串空值写成\N
--null-non-string '\\N' &>> ${log_path}/${table}.log              # 将非字符串空值写成\N

# 注意：判断上一个指令的返回值要写在if/fi作用域里面,写外面取不到
if [[ $? -eq 0 ]] ; then
     echo `date +'%Y-%m-%d %H:%M:%S'` >> ${watch_path}/$table
     echo "sqoop import data succeed!" >> ${log_path}/${table}.log
else
     echo "sqoop import data failed!" >> ${log_path}/${table}.log
     exit 1
fi
```

### hive2mysql.sh
```shell
#!/bin/bash

# 判断参数个数
if [ $# -eq 1 ]; then
    table=$1
    dt=`date -d yesterday +%Y%m%d`  # `date -d '-1 day' +%Y%m%d`
elif [ $# -eq 2 ]; then
    table=$1
    dt=$2
else
    echo "wrong args[] numbers"
    exit 1
fi

username=eadmin
password='EWQTB512Oikf;'

log_path=/home/hive/gds/logs/sqoop/output/${dt}
watch_path=/home/hive/gds/filewatch/sqoop/output/${dt}

if [ ! -d ${log_path} ] ; then
    mkdir ${log_path}
fi

if [ ! -d ${watch_path} ] ; then
    mkdir ${watch_path}
fi

# 先清理mysql已存在数据(如果有必要)
/usr/bin/mysql -u${username} -p${password} -h${ip} -P${port} --default-character-set=utf8 -D${mysql_db}
-e "delete from ${table} where stat_date = str_to_date(${dt},'%Y%m%d');" & >> ${log_path}/${table}.log

# 方式一：sqoop增量抽hive表数据到mysql
sqoop export
--connect jdbc:mysql://${ip}:${port}/${mysql_db}                # 数据库
--username ${username}                                          # 用户名
--password ${password}                                          # 密码
--table ${table}                                                # mysql表
--columns "name,age,pv,uv,date..."                              # 指定列(可选项)
--input-fields-terminated-by '\001'                             # 字段分隔符
--export-dir /user/hive/warehouse/ads.db/${table}/dt="${dt}"    # hdfs路径
--staging-table ${table}_tmp                                    # 使用临时表解决多map任务可能出现的数据一致性问题
--clear-staging-table                                           # 清除临时表
--input-null-string '\N'                                        # 将字符串空值写成\N
--input-null-non-string '\N' &>> ${log_path}/${table}.log       # 将非字符串空值写成\N

# 方式二：先将hive数据加载到文件,然后load文件到mysql(有时候sqoop运行的java程序无法识别特殊字符)
hive -e "select * from test;" > /opt/test/aaa.txt
# 合并小文件(如果有必要)
cd /home/dw/hive/flume/log_site/data & cat * > aaa.txt
# 往mysql插入数据
/usr/bin/mysql -u${username} -p${password} -h${ip} -P${port} --default-character-set=utf8 -D${mysql_db}
-e "LOAD DATA LOCAL INFILE '/opt/test/aaa.txt' REPLACE INTO TABLE ${mysql_db}.${table};" & >> ${log_path}/${table}.log

if [ $? -eq 0 ];then
    echo "sqoop export data succeed!"
    echo `date +'%Y-%m-%d %H:%M:%S'` >> ${watch_path}/${hive_db}.$table
else
    echo "sqoop export data failed!"
    exit 1
fi
```

### demo(有问题)
```shell
#!/bin/bash

# 判断参数个数
if [ $# -eq 2 ]; then
    table=$1
    sync_type=$2
    dt=`date -d yesterday +%Y%m%d`  # `date -d '-1 day' +%Y%m%d`
elif [ $# -eq 3 ]; then
    table=$1
    sync_type=$2
    dt=$3
else
    echo "wrong args[] numbers"
    exit 1
fi

# 判断同步策略
if [ ${sync_type} == "all" ]; then
    # 全量表
    query = "select * from ${table} where 1=1"
elif [ ${sync_type} == "add" ]; then
    # 增量表
    query = "select * from ${table} where DATE_FORMAT(create_time,'%Y%m%d')='${dt}'"
elif [ ${sync_type} == "both" ]; then
    # 新增及变化表
    query = "select * from ${table} where (DATE_FORMAT(create_time,'%Y%m%d')='${dt}' or DATE_FORMAT(operate_time,'%Y%m%d')='${dt}')"
else
    echo "wrong sync_type!"
    exit 1
fi

# sqoop路径
sqoop_path=/Users/okc/modules/sqoop-1.4.7/bin
# 数据路径
ods_path=/user/hive/warehouse/ods.db
# 日志路径
log_path=./logs/input/${dt}/${table}.log
# 使用lzo压缩数据时,为了支持切片要为上传的lzo文件创建索引(可选)
lzo_jar=/opt/module/hadoop-3.1.3/share/hadoop/common/hadoop-lzo-0.4.20.jar

# 同步数据
${sqoop_path}/sqoop import \
--connect jdbc:mysql://localhost:3306/mock \
--username root \
--password root@123 \
--target-dir ${ods_path}/ods_"$table"/dt="${dt}" \
--delete-target-dir \
--query "${query} and \$CONDITIONS" \
--fields-terminated-by '\001' \
--num-mappers 1 \
--compress \
--compression-codec lzop \
--null-string '\\N' \
--null-non-string '\\N' &>> "${log_path}"
hadoop jar ${lzo_jar} com.hadoop.compression.lzo.DistributedLzoIndexer ${ods_path}/ods_"$1"/dt="${dt}"
```