[参考文档](https://www.cnblogs.com/freeweb/p/5276558.html)
### redis
```shell script
# redis是分布式的高性能key-value数据库,数据完全基于内存读写速度极快(10万条/s),可以定期持久化到磁盘防止数据丢失,支持多种数据类型
# 下载
[root@cdh1 ~]$ wget http://download.redis.io/releases/redis-4.0.10.tar.gz
# 解压,brew安装软件默认路径/usr/local/Cellar/ 安装完直接redis-server
[root@cdh1 ~]$ tar -xvf redis-4.0.10.tar.gz -C /usr/local/
# 切换到redis目录  
[root@cdh1 ~]$ cd /usr/local/redis-4.0.10  
# 编译安装  
[root@cdh1 ~]$ make && make install  # 安装完发现/usr/local/bin下多了几个可执行文件
# 修改配置文件
[root@cdh1 ~]$ vim redis.conf  
bind 127.0.0.1     # 将bind注释掉,让其他机器可以通过ip访问,不然只能本地访问
daemonize yes      # 允许redis后台启动  
protected-mode no  # 关闭保护模式,不然要输入用户名和密码  
requirepass        # 可以设置密码 redis-cli -h 192.168.19.11 -p 6379 -a ***  
# 启动redis要指定修改后的配置文件
[root@cdh1 ~]$ redis-server /usr/local/redis-4.0.10/redis.conf 
# 关闭redis
[root@cdh1 ~]$ redis-cli shutdown

# db
127.0.0.1:6379> flushdb                  # 清空当前数据库
127.0.0.1:6379> flushall                 # 清空所有数据库
127.0.0.1:6379> select 0                 # 选择第一个数据库
127.0.0.1:6379> keys *                   # 查看所有key
127.0.0.1:6379> type name                # 查看key类型
127.0.0.1:6379> ttl name                 # 查看key生命周期
127.0.0.1:6379> exists name              # 判断key是否存在,1表示true,0表示false
127.0.0.1:6379> del name                 # 删除键值对

# string
127.0.0.1:6379> set name grubby ex 3600  # 添加键值对,设置过期时间
127.0.0.1:6379> get name                 # 根据key获取value

# list  
127.0.0.1:6379> lpush names grubby  # 往列表左边插入值
127.0.0.1:6379> rpush names moon    # 往列表右边插入值
127.0.0.1:6379> lrange names 0 -1   # 遍历list
127.0.0.1:6379> lindex names 1      # 根据索引求值
127.0.0.1:6379> llen names          # 求list长度
127.0.0.1:6379> lpop names          # 从左边删除列表值
127.0.0.1:6379> rpop names          # 从右边删除列表值
127.0.0.1:6379> lrem names 2 sky    # 删除指定值(count>0从上往下数,count<0从下往上数,count=0删除所有该值)

# set(无序)  
127.0.0.1:6379> sadd names grubby   # 往集合添加元素
127.0.0.1:6379> smembers names      # 遍历set
127.0.0.1:6379> scard names         # 求set长度
127.0.0.1:6379> srem names grubby   # 删除集合元素
127.0.0.1:6379> sinter s1 s2        # 求两个集合交集
127.0.0.1:6379> sunion s1 s2        # 求两个集合并集
127.0.0.1:6379> sdiff s1 s2         # 求两个集合差集
 
# zset(有序)  
127.0.0.1:6379> zadd z1 10 a 11 b 12 c     # 往zset添加值和分数,值存在就更新分数,分数可以相同
127.0.0.1:6379> zrange z1 0 -1             # 遍历zset不带分数
127.0.0.1:6379> zrange z1 0 -1 withscores  # 遍历zset带分数
127.0.0.1:6379> zcard z1                   # 求zset长度

# hash(字典)  
127.0.0.1:6379> hset names orc grubby           # 往字典存键值对
127.0.0.1:6379> hmset names orc grubby ne moon  # 往字典存多个键值对
127.0.0.1:6379> hlen names                      # 求hash长度
127.0.0.1:6379> hget names orc                  # 根据键获取值
127.0.0.1:6379> hmget names orc ne              # 根据键获取多个值
127.0.0.1:6379> hgetall names                   # 遍历
127.0.0.1:6379> hkeys names                     # 获取所有键
127.0.0.1:6379> hvals names                     # 获取所有值
127.0.0.1:6379> hexists names orc               # 判断键是否存在,1表示true,0表示false
127.0.0.1:6379> hdel names baidu                # 删除指定键值对
```