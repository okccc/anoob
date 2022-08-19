#!/bin/bash

# 待监控的任务
tables='ghs_user node_flow_record lesson_flow_record student_record ghs_wechat_account lesson'

for table in ${tables}
do
    # Use $(...) notation instead of legacy backticked `...`. See SC2006
    # Double quote to prevent globbing and word splitting. See SC2086
    res=$(yarn application -list | awk '{print $2}' | grep -x ${table})
done