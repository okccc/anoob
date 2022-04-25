### flink run
```shell
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

# 启动脚本
nohup /opt/flink/bin/flink run \
-m yarn-cluster \
-ynm ${className} \  # yarn监控页面flink任务名称
-yjm 2048m \
-ytm 4096m \
-ys 1 \
-yqu root.ai \
-c ${class} \
${path}/lib/${project}-1.0-SNAPSHOT.jar >> ${path}/log/${project}.log &
```