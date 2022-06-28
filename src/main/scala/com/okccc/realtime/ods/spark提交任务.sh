#!/bin/bash

if [ "$1" == "event" ];then
    className=RealTimeEvent
elif [ "$1" == "lesson" ];then
    className=RealTimeLesson
elif [ "$1" == "token" ];then
    className=RealTimeToken
fi

project=RealTimeEvent
class=com.okccc.realtime.ods.${className}
path=/data1/projects-app/realtime/${project}

# 先杀掉原进程
yarn application -list | grep ${class} | awk '{print $1}' | xargs yarn application -kill

# 启动脚本,将配置信息放到工程外的独立文件,这样往prod/dev等不同环境写数据只需修改配置文件不用重新编译代码
nohup /opt/spark/bin/spark-submit \
--class ${class} \
--name ${project} \  # yarn监控页面spark任务名称
--files ${path}/conf/config.properties,${path}/conf/log4j.properties,${path}/conf/hive-site.xml \
--master yarn \
--deploy-mode cluster \
--queue root.default \
--driver-memory 4g \  # 如果yarn监控页面State一直处于ACCEPTED状态可以适当调大驱动内存和运行内存
--executor-memory 6g \  # total=(executor-memory + max(executor-memory*0.1, memoryOverhead)) * num-executors + driver-memory = 104G
--num-executors 10 \  # container数=num-executors + 1(AM占一个,每个executor占一个)
--executor-cores 2 \
--conf "spark.yarn.executor.memoryOverhead=4096" \
--conf spark.eventLog.enabled=true \
--conf spark.eventLog.dir=hdfs://cdh1/sparklogs \
${path}/lib/${project}-1.0-SNAPSHOT.jar 10 >> ${path}/log/${project}.log &

# 常用配置
--conf "spark.sql.warehouse.dir=hdfs://company/data/hive/warehouse"  \
--conf "spark.default.parallelism=200"  \  # 每个stage默认task数=num-executors * executor-cores,推荐设置为CPU总core数的2~3倍
--conf "spark.sql.auto.repartition=true"  \  # 自动重新分区,避免分区数过多导致单分区数据过少,每个task运算分区时要频繁调度消耗过多时间
--conf "spark.sql.shuffle.partitions=400"  \  # 提高shuffle并行度,默认200,增加shuffle read task数量让每个task处理数据变少时间缩短
# 开启动态资源分配
--conf "spark.dynamicAllocation.enabled=true"  \
--conf "spark.dynamicAllocation.maxExecutors=200"  \
--conf "spark.dynamicAllocation.minExecutors=50"  \
# shuffle调优
--conf "spark.shuffle.file.buffer=64" \
--conf "spark.reducer.maxSizeInFlight=96" \
--conf "spark.shuffle.io.maxRetries=6" \
--conf "spark.shuffle.io.retryWait=60s" \
--conf "spark.shuffle.sort.bypassMergeThreshold=400" \
# jvm调优
--conf "spark.storage.memoryFraction=0.4" \
--conf "spark.yarn.executor.memoryOverhead=2048" \
--conf "spark.core.connection.ack.wait.timeout=300" \
# 开启日志
--conf spark.eventLog.enabled=true \
--conf spark.eventLog.dir=hdfs://test/sparklogs \
--conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.properties -XX:+UseG1GC -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps" \
--conf "spark.streaming.kafka.maxRatePerPartition=1000000" \