#!/bin/bash

# 待修复的表
tables='
ods.ods_mysql_aaa_realtime
ods.ods_mysql_bbb_realtime
ods.ods_mysql_ccc_realtime
ods.ods_mysql_ddd_realtime
ods.ods_mysql_eee_realtime
ods.ods_mongo_aaa_realtime
ods.ods_mongo_bbb_realtime
ods.ods_mongo_ccc_realtime
ods.ods_mongo_ddd_realtime
ods.ods_mongo_eee_realtime
'
# 尽量避免在for循环大量执行hive -e,频繁启动hive会消耗很多资源,容易影响系统性能,可以将sql语句写入文件使用hive -f只执行一次即可
for table in ${tables}
do
    hive -e "set hive.msck.path.validation=ignore;msck repair table ${table}"
done

hive -f /data/projects-app/flink2hdfs/table.sql