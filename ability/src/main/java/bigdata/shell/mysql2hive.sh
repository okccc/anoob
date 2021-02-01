#!/bin/bash
export JAVA_HOME=/usr/java/jdk1.7.0_67-cloudera/

YESTERDAY=`date -d yesterday +%Y%m%d`  # `date -d '-1 day' +%Y%m%d` 或者 `date +%Y%m%d --date '-1 day'`
TODAY=`date -d today +%Y%m%d`  # `date +%Y%m%d`
CURRENT=`date +'%Y-%m-%d %H:%M:%S'`

ip=$1
port=$2
mysql_db=$3
table=$4
column=$5

username=eadmin
password='EWQTB512Oikf;'  # 密码字符串最好加''防止有$等特殊字符导致access denied

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
--connect jdbc:mysql://${ip}:${port}/${mysql_db}?tinyInt1isBit=false  # 数据库
--username ${username}                                                # 用户名
--password ${password}                                                # 密码
--table ${table}                                                      # mysql表
--fields-terminated-by '\001'                                         # 字段分隔符,注意区分'\001'和'\t'
--where "create_time=${YESTERDAY} or update_time=${YESTERDAY}"        # 筛选数据(增量选项)
--hive-drop-import-delims                                             # 过滤掉分隔符\n, \r, \01
--hive-import                                                         # 导入hive,不设置就使用hive默认分隔符
--hive-table base.${table}                                            # hive表
-m 4                                                                  # -m表示map数量,sqoop任务没有reduce阶段
--delete-target-dir                                                   # 删除已存在的目录
--hive-overwrite                                                      # 覆盖数据
--hive-partition-key dt                                               # 分区字段dt(增量选项)
--hive-partition-value ${YESTERDAY}                                   # 分区值(增量选项)
--null-string '\\N'                                                   # 将字符串空值写成\N
--null-non-string '\\N' &>> ${log_path}/${table}.log                  # 将非字符串空值写成\N

if [[ $? -eq 0 ]] ; then
     echo ${CURRENT} >> ${watch_path/$table}
     echo "sqoop import data succeed!" >> ${log_path}/${table}.log
else
     echo "sqoop import data failed!" >> ${log_path}/${table}.log
     exit 1
fi



# sqoop增量导入两种方式
# 基于递增列(Append方式)
--incremental append
--check-column id
--last-value 0  # 每次执行job后,sqoop会自动记录该last-value值,下次执行时无需手动更改
# 基于时间列(LastModified方式)
--incremental lastmodified
--check-column update_time
--last-value "2019-05-14 15:17:23"  # 时间戳可以通过参数配置
--merge-key update_time
