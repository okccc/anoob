#!/bin/bash

source /etc/profile

# 监控上一分钟的日志,因为当前分钟的日志还没生成
current_time=$(date -d "1 minute ago" "+%Y-%m-%d %H:%M")

files=$(find /data -type f -name 'example*.log')

for file in ${files}
do
    echo "${current_time}"
    echo "${file}"

    res=$(cat ${file} | grep "${current_time}" | grep 'CanalParseException' | wc -l)

    echo "${res}"

    if [ ${res} -gt 0 ]; then
        /usr/bin/python3 /data/alarm.py "${file}"

        # 加字段报错column size is not match for table:user.user_info,15 vs 14,是因为canal本地缓存的元数据没刷新,先删除h2.mv.db
        dir1=$(dirname "${file}")
        h2file=${dir1/logs/conf}/h2.mv.db
        echo "${h2file}"
        rm -rf ${h2file}

        # 再重启该canal实例,会重新去information_schema获取元数据信息
        dir2=$(echo ${file} | cut -d'/' -f1-3)
        echo "${dir2}"
        ${dir2}/bin/restart.sh
    fi
done