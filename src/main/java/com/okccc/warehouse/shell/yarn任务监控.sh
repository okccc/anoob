#!/bin/bash

# crontab执行结果和手动执行结果不一样,是因为两者环境变量不一样,脚本开头添加
source /etc/profile

# 待监控的任务
tables='ghs_user node_flow_record lesson_flow_record student_record ghs_wechat_account lesson'

for table in ${tables}
do
    # Use $(...) notation instead of legacy backticked `...`. See SC2006
    # Double quote to prevent globbing and word splitting. See SC2086.
#    res=`yarn application -list | awk '{print $2}' | grep -x ${table}`
    res=$(yarn application -list | awk '{print $2}' | grep -x ${table})
    echo "${res}"
    # 求字符串长度str='hello' 1.${#str} 2.expr length ${str} 3.echo ${str}|wc -L 4.echo ${str}|awk '{print length($0)}'
    if [ ${#res} -eq 0 ]; then
        python3 /data/projects-app/flink2hdfs/alarm.py "${table}"
    fi
done