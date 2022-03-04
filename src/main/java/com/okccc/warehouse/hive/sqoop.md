### sqoop
```shell
# 下载
[root@cdh1 ~]$ wget http://archive.apache.org/dist/sqoop/1.4.7/sqoop-1.4.7.bin__hadoop-2.6.0.tar.gz
# 安装
[root@cdh1 ~]$ tar -xvf sqoop-1.4.7.bin__hadoop-2.6.0.tar.gz -C /opt/module
# 修改配置文件
[root@cdh1 ~]$ vim sqoop-env.sh
export HADOOP_COMMON_HOME=/opt/module/hadoop-3.1.3
export HADOOP_MAPRED_HOME=/opt/module/hadoop-3.1.3
export HIVE_HOME=/opt/module/hive
export ZOOKEEPER_HOME=/opt/module/zookeeper-3.5.7
export ZOOCFGDIR=/opt/module/zookeeper-3.5.7/conf
# 拷贝jdbc驱动
[root@cdh1 ~]$ cp mysql-connector-java-5.1.48.jar /opt/module/sqoop/lib/
# 测试连接
[root@cdh1 ~]$ bin/sqoop list-databases --connect jdbc:mysql://localhost:3306/ --username root --password root@123
```
