#!/bin/bash
export JAVA_HOME=/usr/java/jdk1.7.0_67-cloudera/

YESTERDAY=`date -d yesterday +%Y%m%d`
CURRENT=`date +'%Y-%m-%d %H:%M:%S'`

ip=$1
port=$2
mysql_db=$3
table=$4
hive_db=$5

username=eadmin
password='EWQTB512Oikf;'

log_path=/home/hive/gds/logs/sqoop/output/${YESTERDAY}
watch_path=/home/hive/gds/filewatch/sqoop/output/${YESTERDAY}

if [ ! -d ${log_path} ] ; then
    mkdir ${log_path}
fi

if [ ! -d ${watch_path} ] ; then
    mkdir ${watch_path}
fi

echo "${CURRENT} start" >> ${log_path}/${table}.log

# 先清理mysql已存在数据(如果有必要)
/usr/bin/mysql -u${username} -p${password} -h${ip} -P${port} --default-character-set=utf8 -D${mysql_db}
-e "delete from ${table} where stat_date = str_to_date(${YESTERDAY},'%Y%m%d');" & >> ${log_path}/${table}.log

# 方式一：sqoop增量抽hive表数据到mysql
sqoop export
--connect jdbc:mysql://${ip}:${port}/${mysql_db}                                # 数据库
--username ${username}                                                          # 用户名
--password ${password}                                                          # 密码
--table "${table}"                                                              # mysql表
--columns "name,age,pv,uv,date..."                                              # 指定列(可选项)
--input-fields-terminated-by '\001'                                             # 字段分隔符
--export-dir /user/hive/warehouse/"${hive_db}".db/"${table}"/dt="${YESTERDAY}"  # 数据存储路径
--staging-table "${table}"_tmp                                                  # 使用临时表解决多map任务可能出现的数据一致性问题
--clear-staging-table                                                           # 清除临时表
--input-null-string '\N'                                                        # 将字符串空值写成\N
--input-null-non-string '\N' &>> "${log_path}"/"${table}".log                   # 将非字符串空值写成\N

# 方式二：先将hive数据加载到文件,然后load文件到mysql(有时候sqoop运行的java程序无法识别特殊字符)
hive -e "select * from test;" > /opt/test/aaa.txt
# 合并小文件(如果有必要)
cd /home/dw/hive/flume/log_site/data & cat * > aaa.txt
# 往mysql插入数据
/usr/bin/mysql -u${username} -p${password} -h${ip} -P${port} --default-character-set=utf8 -D${mysql_db}
-e "LOAD DATA LOCAL INFILE '/opt/test/aaa.txt' REPLACE INTO TABLE ${mysql_db}.${table};" & >> ${log_path}/${table}.log

echo "${CURRENT} end" >> ${log_path}/${table}.log

if [ $? -eq 0 ];then
    echo "sqoop export data succeed!"
    echo "${CURRENT}" >> ${watch_path}/${hive_db}.$table
else
    echo "sqoop export data failed!"
    exit 1

fi