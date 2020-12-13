[参考文档](https://www.cnblogs.com/freeweb/p/5276558.html)
### redis
```shell script
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

# redis三大特点  
1.高性能的key-value数据库,数据完全基于内存所以读写速度极快,每秒约10万条,定期持久化到磁盘防止数据丢失  
2.支持string/list/set/zset/hash多种数据类型  
3.分布式集群

# db
flushdb        # 清空当前数据库  
flushall       # 清空所有数据库  
select 0       # 选择第一个数据库  
del key_name   # 删除键值对  
keys *         # 查看所有key
type key_name  # 查看key的类型

# string
set uname grubby ex 60  # 设置键值对,ex是过期时间  
get uname               # 根据key获取value
ttl uname               # 查看生命周期

# list  
llen websites             # 求list长度  
lpush websites baidu.com  # 往列表左边插入值  
rpush websites hupu.com   # 往列表右边插入值  
lrange websites 0 -1      # 遍历list  
lpop websites             # 从左边删除列表值  
rpop websites             # 从右边删除列表值  
lrem websites 2 qq.com    # 删除指定值(count>0从上往下删,count<0从下往上删,count=0删除所有)  
lindex websites 1         # 根据索引求值

# set(无序)  
scard team1         # 求set长度  
sadd team1 kobe     # 往集合添加元素  
smembers team1      # 遍历set  
srem team1 kobe     # 删除集合元素  
sinter team1 team2  # 求两个集合交集  
sunion team1 team2  # 求两个集合并集  
sdiff team1 team2   # 求两个集合差集

# zset(有序)  
zcard myzset                   # 求zset长度  
zadd myzset 10 a 11 b 12 c     # 往zset添加值和分数,值存在就更新分数,分数可以相同  
zrange myzset 0 -1             # 遍历zset不带分数  
zrange myzset 0 -1 withscores  # 遍历zset带分数

# hash(字典)  
hlen website                  # 求hash长度
hset website baidu baidu.com  # 往字典存键值对
hget website baidu            # 根据键获取值
hgetall website               # 遍历
hkeys website                 # 获取所有键
hvals website                 # 获取所有值
hdel website baidu            # 删除指定键值对
hexists website baidu         # 判断键是否存在 0不存在,1存在
```