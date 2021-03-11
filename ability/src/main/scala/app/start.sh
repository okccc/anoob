#!/bin/bash

project=RealTimeEvent

if [ $1 == "token" ];then
    className=RealTimeMongoTokenSync
elif [ $1 == "lessonbuy" ];then
    className=RealTimeMongoLessonBuySync
elif [ $1 == "redeem" ];then
    className=RedeemRealTimeMongoSync
elif [ $1 == "babies" ];then
    className=BabiesRealTimeMongoSync
elif [ $1 == "tutor_bind" ];then
    className=TuorBindRealTimeMongoSync
fi
class=app.$className
yarn application --list | grep $class | awk '{system(" yarn application --kill "$1)}'

# 将配置信息放到工程外面的独立文件,这样代码可以复用,可以往dev/test等不同环境写数据,只需更改配置文件不用重新编译代码
nohup /opt/spark/bin/spark-submit \
--class $class \
--files /data1/projects-app/MongoStream/$project/conf/config.properties,\
/data1/projects-app/MongoStream/$project/conf/log4j.properties,\
/data1/projects-app/MongoStream/$project/conf/hive-site.xml \
--master yarn \
--deploy-mode cluster \
--driver-memory 1g \
--executor-memory 1g \
--conf "spark.executor.cores=2" \
--conf "spark.yarn.maxAppAttempts=0" \
--conf "spark.task.maxFailures=1" \
--conf "spark.dynamicAllocation.enabled=false" \
--conf "spark.yarn.max.executor.failures=3" \
--conf "spark.executor.instances=3" \
--conf "spark.yarn.executor.memoryOverhead=1024" \
--conf spark.eventLog.enabled=true \
--conf spark.eventLog.dir=hdfs://jiliguala/sparklogs \
--conf "spark.executor.extraJavaOptions=-Dlog4j.configuration=log4j.properties -XX:+UseG1GC -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps" \
--conf "spark.streaming.kafka.maxRatePerPartition=1000000" \
/data1/projects-app/MongoStream/$project/lib/$project-1.0-SNAPSHOT.jar 10 > /data1/projects-app/MongoStream/$project/log/$project.log &