### redis
```shell script
# NoSQL(Not Only SQL)泛指非关系型数据库,不遵循RDBMS的设计范式和技术标准,专门为某些特定应用场景设计,从而提升性能/容量/扩展性
# redis是分布式的高性能key-value数据库,数据完全基于内存读写速度极快(10万条/s),可以定期持久化到磁盘防止数据丢失,支持多种数据类型
# redis单线程+多路IO复用技术: 利用select/poll/epoll可以同时监察多个IO流事件的能力,在空闲时会把当前线程阻塞,有IO事件发生就从阻塞中唤醒,
# 在同一个进程内用单个线程处理多个IO流,可以处理大量的并发IO,而不需要消耗太多CPU/内存,memcached是多线程+锁
# redis应用场景: 1.配合mysql做高速缓存降低数据库IO 2.大数据场景高频率读写的少量数据,用key查询的缓存数据/临时数据/计算结果 3.特殊数据结构
[root@cdh1 ~]$ wget http://download.redis.io/releases/redis-4.0.10.tar.gz
# 安装(mac下的brew install默认安装路径/usr/local/Cellar,并且自动将可执行命令添加到$PATH的/usr/local/bin,$PATH是可执行命令的查找顺序)
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
# 启动redis服务端
[root@cdh1 ~]$ redis-server /usr/local/redis-4.0.10/redis.conf
# 启动redis客户端,--raw解决中文显示问题
[root@cdh1 ~]$ redis-cli --raw
# 关闭redis
[root@cdh1 ~]$ redis-cli shutdown
# 性能测试
[root@cdh1 ~]$ redis-benchmark

# db
127.0.0.1:6379> flushdb                  # 清空当前数据库
127.0.0.1:6379> flushall                 # 清空所有数据库
127.0.0.1:6379> select 0                 # 选择第一个数据库
127.0.0.1:6379> dbsize                   # 查看当前库下key的数量
127.0.0.1:6379> keys *                   # 查看所有key
127.0.0.1:6379> type name                # 查看key类型
127.0.0.1:6379> expire key 10            # 设置key的过期时间为10秒
127.0.0.1:6379> ttl name                 # 查看key生命周期,-1表示永不过期,-2表示已经过期
127.0.0.1:6379> exists name              # 判断key是否存在,1表示true,0表示false
127.0.0.1:6379> del name                 # 删除键值对

# 字符串(string): value最大512M
127.0.0.1:6379> set <key> <value> ex 3600  # 添加键值对,设置过期时间
127.0.0.1:6379> get <key>                  # 根据key获取value
127.0.0.1:6379> append <key> <value>       # 将给定值追加到原值末尾
127.0.0.1:6379> setnx <key> <value>        # 当key不存在时才设置value
127.0.0.1:6379> incr/decr <key> <步长>      # 针对数字,将key中存储的数值加/减1或指定步长
127.0.0.1:6379> mset <k1> <v1> <k2> <v2>   # 同时设置多个键值对,原子性操作
127.0.0.1:6379> mget <k1> <k2>             # 同时获取多个值

# 列表(list): 单键多值的字符串列表,按照插入顺序排序,底层是双向链表,两端操作性能很快,通过索引操作中间节点性能很差
127.0.0.1:6379> llen <key>                            # 求list长度
127.0.0.1:6379> lrange <key> <start> <end>            # 按照索引起始位置截取范围内元素,0 -1表示遍历列表
127.0.0.1:6379> lindex <key> <index>                  # 根据索引取值
127.0.0.1:6379> lpush/rpush <key> <value>             # 往列表左边/右边插入值
127.0.0.1:6379> lpop/rpop <key>                       # 从列表左边/右边删除值
127.0.0.1:6379> rpoplpush <key1> <key2>               # 从k1列表右边吐出一个值插入到k2列表左边
127.0.0.1:6379> lrem <key> <n> <value>                # 删除n个value值(n>0从左往右,n<0从右往左,n=0删除所有该值)
127.0.0.1:6379> linsert <key> before/after <v1> <v2>  # 在v1前面/后面插入v2

# 集合(set): 单键多值的字符串无序集合,可以去重和判存,底层是value为null的hash表,所以CRUD的复杂度都是O(1),随着数据增加操作时间不变
127.0.0.1:6379> scard <key>                    # 求set长度
127.0.0.1:6379> smembers <key>                 # 遍历集合
127.0.0.1:6379> sismember <key> <value>        # 判断集合是否包含该value,1表示true,0表示false
127.0.0.1:6379> sadd <key> <value>             # 往集合添加元素
127.0.0.1:6379> srem <key> <v1> <v2>           # 删除集合元素
127.0.0.1:6379> sinter/sunion/sdiff <k1> <k2>  # 求两个集合交集/并集/差集
 
# 有序集合(zset): 有序的set,给每个元素都关联评分score,从低到高排序集合中的元素,因为有序所以可以根据score截取特定范围内的元素
127.0.0.1:6379> zcard <key>                        # 求zset长度
127.0.0.1:6379> zadd <key> <s1> <v1> <s2> <v2>     # 往zset添加score和value,值唯一但评分可以重复
127.0.0.1:6379> zrange <key> <start> <stop>        # 返回索引范围内元素,默认升序zrevrange逆序,0 -1表示遍历集合,withscores是否带上分数
127.0.0.1:6379> zrangebyscore <key> <min> <max>    # 返回score介于min和max之间的元素,withscores是否带上分数,limit offset count限制数量
127.0.0.1:6379> zincrby <key> <increment> <value>  # 针对数字,为元素的score加上增量
127.0.0.1:6379> zrem <key> <value>                 # 删除指定元素
# 利用zset实现商品访问量排行榜
127.0.0.1:6379> zadd topN 100 p1 200 p2 300 p3 && zrange topN 0 -1 withscores

# 哈希(hash): 字符串类型的field和value的映射表,hash适合存储对象,类似java的HashMap<String, Object>
127.0.0.1:6379> hlen <key>                         # 求hash长度
127.0.0.1:6379> hgetall <key>                      # 根据key获取所有field及value
127.0.0.1:6379> hexists <key> <field>              # 判断key的field是否存在,1表示true,0表示false
127.0.0.1:6379> hset <key> <field> <value>         # 往key添加field及value
127.0.0.1:6379> hget <key> <field>                 # 根据key和field获取value
127.0.0.1:6379> hmset <key> <f1> <v1> <f2> <v2>    # 往key添加多个field及value
127.0.0.1:6379> hmget <key> <f1> <f2>              # 根据key的多个field获取多个value
127.0.0.1:6379> hkeys <key>                        # 获取key的所有field
127.0.0.1:6379> hvals <key>                        # 获取key的所有value
127.0.0.1:6379> hdel <key> <field>                 # 删除key的field,1表示true,0表示false
127.0.0.1:6379> hincrby <key> <field> <increment>  # 针对数字,给key的field的值加1/-1
```