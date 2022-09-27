#!/bin/bash

if [ $# -eq 1 ]; then
   stat_date="$1"
elif [ $# -eq 0 ]; then
   stat_date=$(date -d yesterday +%Y%m%d)
else
   echo "wrong args[] number"
   exit 1
fi

flume_date=$(date -d "${stat_date}" +%Y-%m-%d)
error_log=/home/flume/error.log

run() {
  # 方式1:加载数据到hive内部表(将某个目录下的数据剪切到/user/hive/warehouse/ods.db/**.table目录)
  hive -e "load data inpath '/flume/access_log/dt=${flume_date}/*' overwrite into table ods.access_log partition(dt=${stat_date});"

  # 方式2:加载数据到hive外部表(建外部表时指定location就不用再load数据了,直接add partition即可)
  hive -e "alter table ods.access_log add if not exists partition(dt=${stat_date}) location '/flume/access_log/dt=${flume_date}';"

  if [ $? -eq 0 ] ; then
    echo "load data succeed! "
  else
    echo "load data failed! "
    exit 1
  fi
}

run 1 >>${error_log} 2>&1