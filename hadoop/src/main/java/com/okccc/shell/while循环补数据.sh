#!/bin/bash

# 判断参数个数
if [ $# -eq 2 ]; then
    table=$1
    days=$2
else
    echo "wrong arg[] numbers"
    exit 1
fi

num=1

while ${num} -lt "${days}"
do
    stat_date=$(date -d "-${num} day" +%Y%m%d)
    echo "${stat_date}"
    hive -hivevar data_date="${stat_date}" -f /data/hive/hqls/"${table}".sql
    # SC2219: Instead of let expr, prefer (( expr )) .
#    let num+=1
    ((num+=1))
done
