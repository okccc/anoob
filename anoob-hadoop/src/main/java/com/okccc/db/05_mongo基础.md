- [参考文档](http://c.biancheng.net/mongodb2/)
### linux安装mongodb
```shell script
# mongodb是一个基于分布式文件存储的NoSQL数据库,旨在为web应用提供可扩展的高性能数据存储解决方案
# 优点：数据结构灵活(类json格式)、高性能(支持mr处理海量数据)、扩展性好
# 缺点：nosql不支持事务和表关联等操作、占用空间大
# 配置MongoDb的yum源
[root@master1 ~]# vim /etc/yum.repos.d/mongodb-org-3.4.repo
[mongodb-org-3.4]
name=MongoDB Repository  
baseurl=https://repo.mongodb.org/yum/redhat/$releasever/mongodb-org/3.4/x86_64/
gpgcheck=0
enabled=1
gpgkey=https://www.mongodb.org/static/pgp/server-3.4.asc
# 安装MongoDb
[root@master1 ~]# yum -y install mongodb-org
# 查看安装位置
[root@master1 ~]# whereis mongod
# 修改配置文件
[root@master1 ~]# vim /etc/mongod.conf
# bindIp: 127.0.0.1  # Listen to local interface only, comment to listen on all interfaces.
# 启动服务端
[root@master1 ~]# systemctl start mongod && systemctl enable mongod
# 监控日志
[root@master1 ~]# tail -f /var/log/mongodb/mongod.log
# 数据备份
[root@master1 ~]# mongodump -h hostname -d test -o output_path  
# 数据恢复
[root@master1 ~]# mongorestore -h hostname -d test --dir input_path  
# 导入数据
[root@master1 ~]# /usr/bin/mongoimport -d tencent -c position --file ./position.json(csv)  
# 启动客户端
[root@master1 ~]# mongo
MongoDB shell version v3.4.16
connecting to: mongodb://127.0.0.1:27017  # 默认端口27017
> 
```

### mac安装mongodb
```shell
# 下载
[root@cdh1 ~]$ wget https://fastdl.mongodb.org/osx/mongodb-macos-x86_64-4.4.3.tgz
# 解压
[root@cdh1 ~]$ tar -zxvf mongodb-macos-x86_64-4.4.3.tgz
[root@cdh1 ~]$ mv mongodb-macos-x86_64-4.4.3 mongodb
# 创建目录存储数据和日志
[root@cdh1 ~]$ mkdir data & mkdir log
# 修改环境变量
[root@cdh1 ~]$ open -e .bash_profile | vim ~/.bash_profile & source ~/.bash_profile
export MONGODB_HOME=/Users/okc/modules/mongodb
export PATH=$PATH:$MONGODB_HOME/bin
# 启动服务端,--dbpath数据存放目录/--logpath日志存放目录/--fork后台运行
[root@cdh1 ~]$ mongod --dbpath /Users/okc/modules/mongodb/data --logpath /Users/okc/modules/mongodb/log/mongo.log --fork
# 启动客户端
[root@cdh1 ~]$ mongo
MongoDB shell version v4.4.1
connecting to: mongodb://127.0.0.1:27017/?compressors=disabled&gssapiServiceName=mongodb
>
```

### type
- Object ID：文档ID保证唯一性,是一个12字节的十六进制数：当前时间戳(4) + 机器ID(3) + mongodb服务进程id(2) + 增量值(3)
- String：字符串
- Boolean：true/false
- Integer：32/64位整数(取决于服务器)
- Double：双精度浮点值
- Arrays：数组/列表
- Object：嵌入式文档,一个值即一个文档
- Null：空值
- Timestamp：时间戳
- Date：存储当前日期或时间的UNIX格式

|       | mysql | mongodb | es |
| :---: | :---: | :---: | :---: |
|库     |database|db        |index    |
|表     |table   |collection|type(废弃)|
|行     |row     |document  |document|
|字段   |column  |field     |field    |
|索引   |index   |index     |-       |
|关联   |join    |-         |-       |

|         | mysql  |redis| es   | hbase| hive|
| :---:   | :---:  |:---:|:---: | :---:|:---:|
|容量      |中等     |小   |较大  |海量  | 海量  |
|查询时效性 |中等     |极快  |较快  |中等  | 慢   |
|查询灵活性 |很好(sql)|较差  |较好  |较差  |很好(sql)|
|写入速度   |中等     |极快 |较快  |较快  | 慢   |
|一致性(事务)|Y       |N   |N    |N    | N    |

### db
- db：当前数据库名称
- show dbs：查看所有数据库
- use test：切换数据库(如果数据库不存在则指向数据库但不创建,直到插入数据或创建集合时数据库才被创建)
- db.version()：当前数据库版本
- <font color=red>db.stats()</font>：当前数据库信息
- db.dropDatabase()：删除当前数据库
- db._adminCommand("connPoolStats")：当前正在使用的链接
## collection
- show collections：查看当前数据库所有集合
- db.createCollection(name, options)：创建集合
- db.createCollection("position")
- db.createCollection("sub", {capped : true, size : 10})  // capped默认false不设置上限,true要指定size,文档达到上限会覆盖之前数据
- db.集合.drop()：删除集合
- db.help()：数据库相关帮助命令
- db.集合.help()：集合相关帮助命令
## crud
#### <font color=gray>增</font>
- <font color=red>db.集合.insert({})</font>  // insert：_id存在会报错 save：_id存在会更新
- 造数据：for(i=1;i<=100;i++){db.position.insert({name:"test"+i,age:i})}
#### <font color=gray>删(慎用!)</font>
- <font color=red>db.集合.remove({query}, {justOne:true})</font>  // justOne默认false删除所有,true只删除第一条
- db.position.remove({gender:0}, {justOne:true})
- db.position.remove({})  # 清空集合
#### <font color=gray>改</font>
- <font color=red>db.集合.update({query}, {update}, {multi:true})</font>  // query相当于where、update相当于set、multi默认false只更新第一条,true更新所有
- db.position.update({category:"技术"},{$set:{location:"上海"}},{multi:true})  // 将所有category为"技术"的文档的location改成"上海"
- db.position.update({},{$set:{category:"研发"}},{multi:true})  // 将所有文档的category改成"研发"
#### <font color=gray>查</font>
- <font color=red>db.集合.find({query})</font>
- db.集合.findOne({query})
- db.集合.find({query}).pretty()
- db.集合.find({query}).explain()
## mongodb高级查询
#### <font color=gray>索引</font>
- 查看索引：<font color=red>db.集合.getIndexes()</font>
- 创建单列索引：<font color=red>db.集合.ensureIndex({field:1/-1})</font>  // 1是升序,-1是降序
- 创建多列索引(复合索引)：db.集合.ensureIndex({field1:1/-1,field2:1/-1})
- 创建子文档索引：db.集合.ensureIndex({field.subfield:1/-1})
- 唯一索引：db.集合.ensureIndex({field:-1},{unique:true})
- 删除单个索引：db.集合.dropIndex({field:1/-1})
- 删除所有索引：db.集合.dropIndexes()
#### <font color=gray>比较运算符</font>
- <font color=red>默认=, $lt < | $lte <= | $gt > | $gte >= | $ne !=</font>  
db.position.find({category:"技术"})  // category=技术  
db.position.find({update_time:{$gte:"2019年05月08日"}})  // 更新时间>=20190508  
db.position.find({location:{$ne:null}})  // 地址非空
#### <font color=gray>逻辑运算符</font>
- <font color=red>默认$and逻辑与,$or表示逻辑或</font>  
db.position.find({category:"技术",location:"上海"})  // 类别是技术并且地址在上海  
db.position.find({$or:{category:"技术"},{location:"上海"}})  // 类别是技术或者地址在上海  
db.position.find({$or:{category:"技术"},{location:"上海"},update_time:{$gte:"2019年05月08日"}})  // 类别是技术或者地址在上海,并且更新时间>=20190508
#### <font color=gray>范围运算符</font>
- <font color=red>使用$in和$nin判断是否在某个范围内</font>  
db.position.find({category:{$in:"技术","产品"}})  // 类别属于技术或产品
#### <font color=gray>正则表达式</font>
- <font color=red>使用//或$regex查找</font>  
db.position.find({title:/算法/})  // 标题中包含"算法"  
db.position.find({title:{$regex:'专家$'}})  // 标题以专家结尾
#### <font color=gray>自定义查询</font>
- db.position.find().limit(4).skip(5)  // limit和skip不分先后
#### <font color=gray>投影(指定字段查询)</font>
- <font color=red>db.集合.find({query},{field:1/0})</font>  // 1显示0不显示,_id字段默认显示  
db.position.find({},{category:1,location:1})  // 如果要选取的字段很少就将需要的字段指定为1  
db.position.find({},{_id:0,responsibility:0})  // 如果要选取的字段很多就将不需要的字段设为0
#### <font color=gray>排序</font>
- <font color=red>db.集合.find().sort({field:1/-1})</font>  # 1升序-1降序  
db.position.find().sort({update_time:-1})
#### <font color=gray>统计</font>
- <font color=red>db.集合.find({query}).count() | db.集合.count({query})</font>  
db.position.find({location:"北京"}).count()  
db.position.count({location:"北京"})
#### <font color=gray>去重</font>
- <font color=red>db.集合.distinct('去重字段',{query})</font>  
db.position.distinct('category')  
db.position.distinct('category',{update_time:{$gte:"2019年05月08日"}})

### mongodb聚合操作
```sql
- <font color=red>db.集合.aggregate({管道:{表达式}},{管道:{表达式}}...)</font> 

管道|作用|表达式|作用
:---:|:---:|:---:|:---:
$group|分组|$sum|求和
$match|过滤|$avg|平均值
$project|修改文档结构|$min|最小值
$sort|排序|$max|最大值
$limit|限制条数|$push|往一个数组中插入值
$skip|跳过指定文档条数|$first|排序后第一条文档
$unwind|拆分数组类型字段|$last|排序后最后一条文档
 
#### <font color=gray>$group</font>
- <font color=red>_id:'$field'指定分组字段,_id:null表示不分组</font>  
db.position.aggregate({$group:{_id:'$location', sum:{$sum:1}}})  
db.position.aggregate({$group:{_id:null, min:{$min:'$update_time'},max:{$max:'$update_time'}}}) 
#### <font color=gray>$match</font>
- <font color=red>先过滤数据再分组聚合(前面管道的结果交给下一个管道)</font>  
db.position.aggregate({$match:{update_time:{$gte:"2019年05月08日"}}},{$group:{_id:"$category",sum:{$sum:1}}})  
#### <font color=gray>$project</font>
- 先分组再修改文档结构  
db.position.aggregate({$group:{_id:"$category",sum:{$sum:1}}},{$project:{_id:0,sum:1}})  
#### <font color=gray>$sort</font>
- <font color=red>分组聚合后对结果排序</font>  
db.position.aggregate({$group:{_id:"$category",sum:{$sum:1}}},{$sort:{sum:-1}})
])：查询男/女生人数然后降序排序  
#### <font color=gray>$limit(skip) </font>
- 限制条数  
db.position.aggregate({$group:{_id:"$category",sum:{$sum:1}}},{$sort:{sum:-1}},{$skip:1},{$limit:3}) 
])：先统计男/女生人数,升序排序,取第二条数据  
#### <font color=gray>$unwind</font>
- <font color=red>将数组类型字段拆分成多条文档</font>  
db.position.insert({_id:1,title:["开发","产品","销售"]})  
db.position.aggregate({$match:{_id:1}},{$unwind:'$title'})
```