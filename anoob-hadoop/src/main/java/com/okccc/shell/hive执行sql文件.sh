#!/bin/bash

# 判断参数个数
if [ $# -eq 1 ]; then
    sql_file=$1
    stat_date=$(date -d yesterday +%Y%m%d)
elif [ $# -eq 2 ]; then
    sql_file=$1
    stat_date=$2
else
    echo "wrong arg[] numbers"
    exit 1
fi

# 当前时间
CURRENT=$(date +'%Y-%m-%d %H:%M:%S')
# 文件路径
log_path=/data/logs/${stat_date}
test ! -d "${log_path}" && mkdir "${log_path}"
error_log=${log_path}/${sql_file}_${stat_date}.log

# hive shell -> sql对应的dt=${hivevar:dt}
hive -hivevar dt="${stat_date}" -f /data/hql/"${sql_file}".sql &>> "${error_log}"
# impala-shell -> sql对应的dt='${var:dt}'
impala-shell -i master2 --var=dt="${stat_date}" -f /data/hql/"${sql_file}".sql &>> "${error_log}"

if [ $? -eq 0 ];then
    echo "call succeed at ${CURRENT}" >> "${error_log}"
else
    echo "call failed at ${CURRENT}" >>  "${error_log}"
    exit 1
fi
