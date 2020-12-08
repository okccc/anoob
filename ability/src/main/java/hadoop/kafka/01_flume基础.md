### nginx
```shell script
# nginx三大功能：反向代理、负载均衡、动静分离
# 安装依赖  
[root@cdh1 ~]$ yum -y install gcc pcre-devel zlib zlib-devel openssl openssl-devel net-tools
# 下载压缩包  
[root@cdh1 ~]$ wget http://nginx.org/download/nginx-1.12.2.tar.gz
# 解压  
[root@cdh1 ~]$ tar -xvf nginx-1.21.2.tar.gz -C /usr/local
# 切换到nginx目录  
[root@cdh1 ~]$ cd /usr/local/nginx-1.21.2
# 编译安装  
[root@cdh1 ~]$ ./configure
[root@cdh1 ~]$ make && make install  # 安装完后/nginx/sbin目录多了nginx执行命令
# 测试配置文件  
[root@cdh1 ~]$ /usr/local/nginx/sbin/nginx -t
# 启动/停止/重启  
[root@cdh1 ~]$ /usr/local/nginx/sbin/nginx  
[root@cdh1 ~]$ /usr/local/nginx/sbin/nginx -s stop  
[root@cdh1 ~] /usr/local/nginx/sbin/nginx -s reload
# 查看nginx进程,jps显示的是java进程,nginx是c++写的
[root@cdh1 ~]$ ps -ef | grep nginx
# 浏览器访问(默认80端口)  
http://192.168.152.11
Welcome to nginx!
```

### flume
[flume官方文档](http://flume.apache.org/releases/content/1.7.0/FlumeUserGuide.html)
```shell script
# 修改配置文件
[root@cdh1 ~]$ vim flume-env.sh
export JAVA_HOME=/usr/java/jdk1.8.0_181-cloudera
# flume内存优化,将JVM heap设置为4g防止OOM,-Xms和-Xmx尽量保持一致减少内存抖动带来的性能影响
export JAVA_OPTS="-Xms4096m -Xmx4096m -Dcom.sun.management.jmxremote"

# 集群生成日志启动脚本
# java -jar和java -cp区别：打包时已指定主类名java -jar a.jar,未指定主类名java -cp a.jar 包名.类名
[root@cdh1 ~]$ vim log.sh
#!/bin/bash
for i in cdh1 cdh2 cdh3
do
    ssh $i "source /etc/profile && java -jar mock-1.0-SNAPSHOT-jar-with-dependencies.jar $1 $2 > a.log &"
done

# 集群一键启动flume脚本
[root@cdh1 ~]$ vim flume.sh
#!/bin/bash
case $1 in
"start"){
    for i in cdh1 cdh2 cdh3
    do
        echo "================= ${i}启动flume ================"
        ssh ${i} "source /etc/profile && cd /opt/module/flume-1.7.0 && nohup flume-ng agent -c conf -f conf/nginx-flume-kafka.conf -n a1 -Dflume.root.logger=INFO,LOGFILE > /dev/null 2>&1 &"
    done
};;
"stop"){
    for i in cdh1 cdh2 cdh3
    do
        echo "================= ${i}停止flume ================"
        ssh ${i} "ps -ef | grep 'nginx-flume-kafka' | grep -v grep | awk '{print \$2}' | xargs kill"  # 这里的$2要加\转义,不然会被当成脚本的第二个参数
    done
};;
esac

# event
flume传输数据的基本单元,由header和body组成 Event: {headers:{} body: 61 61 61  aaa}
# agent
jvm运行flume的最小单元,由source-channel-sink组成
# source
flume1.7版本使用TailDir可以监控多目录,且会记录日志文件读取位置,故障重启后重新采集就从该位置开始,解决断点续传问题
# channel
file channel：数据存到磁盘,速度慢,可靠性高,默认100万个event
memory channel：数据存到内存,速度快,可靠性低,默认100个event
kafka channel：数据存到kafka也是磁盘,可靠性高,且省去sink阶段速度更快,kafka channel > memory channel + sink
channel selectors：replicating将events发往所有channel,multiplexing将events发往指定channel
# sink
不断轮询channel中的事件并将其移除到存储系统或下一个agent,目的地通常是hdfs/logger/kafka
```

#### nginx-flume-kafka.conf
```shell script
# 命名agent组件
a1.sources = r1
a1.channels = c1 c2

# 配置source
a1.sources.r1.type = TAILDIR  # exec方式flume宕机会丢数据
a1.sources.r1.positionFile = /opt/module/flume-1.7.0-bin/test/log_position.json  # 记录日志文件读取位置
a1.sources.r1.filegroups = f1                  # 监控的是一组文件
a1.sources.r1.filegroups.f1 = /tmp/logs/app.+  # 一组文件可以以空格分隔,也支持正则表达式
a1.sources.r1.fileHeader = true
a1.sources.r1.channels = c1 c2
# 拦截器(要将拦截器代码打成jar包放到flume的lib目录下)
a1.sources.r1.interceptors = i1 i2
a1.sources.r1.interceptors.i1.type = flume.LogETLInterceptor$Builder    # etl拦截器
a1.sources.r1.interceptors.i2.type = flume.LogTypeInterceptor$Builder   # 日志类型拦截器
# 选择器
a1.sources.r1.selector.type = multiplexing         # 根据日志类型发往指定channel
a1.sources.r1.selector.header = topic              # event的header的key
a1.sources.r1.selector.mapping.topic_start = c1
a1.sources.r1.selector.mapping.topic_event = c2

# 配置channel
a1.channels.c1.type = org.apache.flume.channel.kafka.KafkaChannel       # 使用KafkaChannel省去sink阶段
a1.channels.c1.kafka.bootstrap.servers = cdh1:9092,cdh2:9092,cdh3:9092  # kafka集群地址
a1.channels.c1.kafka.topic = topic_start                                # start类型的日志发往channel1,对应kafka的topic_start
a1.channels.c1.parseAsFlumeEvent = false
a1.channels.c1.kafka.consumer.group.id = flume-consumer

a1.channels.c2.type = org.apache.flume.channel.kafka.KafkaChannel
a1.channels.c2.kafka.bootstrap.servers = cdh1:9092,cdh2:9092,cdh3:9092  
a1.channels.c2.kafka.topic = topic_event                                # event类型的日志发往channel2,对应kafka的topic_event
a1.channels.c2.parseAsFlumeEvent = false
a1.channels.c2.kafka.consumer.group.id = flume-consumer

# 先启动kafka
[root@cdh1 ~]$ kafka-server-start.sh -daemon ../config/server.properties
[root@cdh1 ~]$ kafka-topics.sh --create --zookeeper cdh1:2181 --topic test --partitions 1 --replication-factor 1
[root@cdh1 ~]$ kafka-console-consumer.sh --bootstrap-server cdh1:9092 --from-beginning --topic test
# 再启动flume-ng
[root@cdh1 ~]$ flume-ng agent -c conf/ -f conf/flume-kafka.conf -n a1 -Dflume.root.logger=INFO,console
# 最后启动生成日志脚本,消费者能收到数据说明ok
[root@cdh1 ~]$ nohup java -cp mock-1.0-SNAPSHOT-jar-with-dependencies.jar app.AppMain > /dev/null 2>&1 &
```

#### nginx-flume-hdfs.conf
```shell script
# 命名agent组件
a1.sources = r1
a1.channels = c1
a1.sinks = k1

# source是文件
a1.sources.r1.type = exec  # execute,表示通过执行linux命令来读取文件
a1.sources.r1.command = tail -f /var/log/hive/hadoop-cmf-hive-HIVESERVER2.log.out  # 要监控的日志文件
# 添加拦截器
a1.sources.r1.interceptors = regex
a1.sources.r1.interceptors.regex.type=REGEX_FILTER
a1.sources.r1.interceptors.regex.regex=^.+uid=.+&uname=.+spuId=.+$
a1.sources.r1.interceptors.regex.excludeEvents=false
# source是目录
a2.sources.r2.type = spooldir
a2.sources.r2.spoolDir = /opt/module/flume/upload
a2.sources.r2.fileSuffix = .COMPLETED
a2.sources.r2.fileHeader = true
a2.sources.r2.ignorePattern = ([^ ]*\.tmp)  # 忽略所有以.tmp结尾的文件2
# source是kafka
a3.sources.r3.type = org.apache.flume.source.kafka.KafkaSource
a3.sources.r3.kafka.bootstrap.servers = cdh1:9092,cdh2:9092,cdh3:9092  # kafka集群地址
a3.sources.r3.kafka.topics = topic_start, topic_event                  # topic列表,用逗号分隔,也可以用正则表达式匹配
a1.sources.r1.batchSize = 1000                                         # 每个批次写往channel的消息数
a1.sources.r1.batchDurationMillis = 1000                               # 批次时间间隔

# memory channel
a1.channels.c1.type = memory
a1.channels.c1.capacity = 1000             # channel最多存储1000个event
a1.channels.c1.transactionCapacity = 100   # channel收集到100个event才会提交事务
a1.channels.c1.keep-alive = 3              # 添加或移除一个event的超时时间(秒)
# file channel
a1.channels.c1.type = file
a1.channels.c1.checkpointDir = /opt/module/flume/cp     # 存储checkpoint的文件
a1.channels.c1.dataDirs = /opt/module/flume/data/logs/  # 存储日志的目录列表,用逗号分隔,优化：指向不同硬盘的多个路径提高flume吞吐量
a1.channels.c1.maxFileSize = 2146435071                 # 单个log文件的最大字节数
a1.channels.c1.capacity = 1000000                       # channel的最大容量
a1.channels.c1.keep-alive = 6                           # 等待put操作的超时时间(秒)

# 配置sink
a1.sinks.k1.type = hdfs
a1.sinks.k1.hdfs.path = hdfs://ns1/user/flume/qb-events/%y-%m-%d/%H
a1.sinks.k1.hdfs.filePrefix = log-                      # 指定文件前缀
a1.sinks.k1.hdfs.useLocalTimeStamp = true               # 是否使用本地时间戳代替event header的时间戳
a1.sinks.k1.hdfs.batchSize = 1000                       # 有100个event写入文件就flush到hdfs
# 数据压缩
a1.sinks.k1.hdfs.fileType = CompressedStream            # 文件类型,默认SequenceFile
a1.sinks.k1.hdfs.codeC = lzop                           # 指定压缩方式
# 控制hdfs文件大小,默认参数会生成大量小文件
a1.sinks.k1.hdfs.rollInterval = 3600                    # tmp文件达到3600秒时会滚动生成正式文件
a1.sinks.k1.hdfs.rollSize = 134217728                   # tmp文件达到128M时会滚动生成正式文件
a1.sinks.k1.hdfs.rollCount = 0                          # tmp文件的滚动与写入的event数量无关
a1.sinks.k1.hdfs.roundUnit = second                     # 滚动时间单位
a1.sinks.k1.hdfs.roundValue = 10                        # 10秒滚动一次文件

# 给source和sink绑定channel
a1.sources.r1.channels = c1  # 一个source可以接多个channel
a1.sinks.k1.channel = c1     # 一个sink只能接一个channel

# 启动flume
[root@cdh1 ~]$ flume-ng agent -c conf -f conf/nginx-hdfs.conf -n a1 -Dflume.root.logger=INFO,console  # 测试监听端口时使用
# 往监测文件写数据
[root@cdh1 ~]$ for i in {1..10000}; do echo "hello spark ${i}" >> test.log; echo ${i}; sleep 0.01; done
```

```java
package org.com.qbao.dc.spark.streaming;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.apache.spark.Accumulator;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.StorageLevels;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.qbao.dc.redis.IRedisService;
import com.qbao.dc.redis.common.RedisModel;
import com.qbao.dc.redis.factory.RedisModelFactory;
import com.qbao.dc.redis.impl.IRedisServiceImpl;
import com.qbao.dc.redis.model.SpuCount;
import com.qbao.dc.redis.model.UserViewCountData;
import redis.clients.jedis.Jedis;
import scala.Tuple2;
public final class KafkaToRedis {
//    private static final Pattern SPACE = Pattern.compile(" ");
    private static Logger logger = Logger.getLogger(KafkaToRedis.class);
    private static IRedisService iRedisService= null;
    private static boolean isInitXml = true;
    @SuppressWarnings({ "deprecation", "serial" })
    public static void main(String[] args) throws InterruptedException {
        //错误提示
        if(args.length < 4){
            System.err.println("Usage: KafkaWordCount <zkQuorum> <group> <topics> <numThreads>");
            System.exit(1);
        }
        //加载配置文件
        SparkConf conf = new SparkConf().setAppName("KafkaToRedis");
        //生成stream context
        JavaStreamingContext jssc = new JavaStreamingContext(conf, Durations.seconds(3));
        //指定topic的线程数
        int numThreads = Integer.parseInt(args[3]);
        //封装topic
        HashMap<String, Integer> topicMap = new HashMap<>();
        String[] topics = args[2].split(",");
        for (String topic : topics) {
            topicMap.put(topic, numThreads);
        }
        //获取kafka数据流
        JavaPairReceiverInputDStream<String, String> messages =
                KafkaUtils.createStream(jssc, args[0], args[1], topicMap,StorageLevels.MEMORY_AND_DISK_SER);
        //将messages转换为DStream数据流
        JavaDStream<String> lines = messages.map(new Function<Tuple2<String,String>, String>() {
            public String call(Tuple2<String, String> tuple2) throws Exception {
                return tuple2._2();
            }
        });
        //将JavaDStream类型转换成String类型
        lines.foreach(new Function<JavaRDD<String>, Void>() {
            @Override
            public Void call(JavaRDD<String> rdd) throws Exception {
                //遍历循环每条记录
                rdd.foreachPartition(new VoidFunction<Iterator<String>>() {
                    @Override
                    public void call(Iterator<String> records) throws Exception {
                        if(isInitXml){
                            //加载spring配置文件
                            String[] paths = {"qbao_dc_redis_application.xml"};
                            ApplicationContext ctx = new ClassPathXmlApplicationContext(paths);
                            //获取iRedisService接口
                            iRedisService = ctx.getBean(IRedisServiceImpl.class);
                            isInitXml = false;
                        }
                        while(records.hasNext()){
                            //拿到一条记录
                            String record = records.next();
                            //添加regex
                            Pattern p1 = Pattern.compile("uid=(\\d+)");
                            Pattern p2 = Pattern.compile("spuId=(\\d+)");
                            //匹配当前记录
                            Matcher m1 = p1.matcher(record);
                            Matcher m2 = p2.matcher(record);
                            //获取字段值
                            if(m1.find() && m2.find()){
                                UserViewCountData data = new UserViewCountData();
                                String uid = m1.group(1);
                                String spuId = m2.group(1);
                                //获取QueryModel
                                RedisModel queryModel = RedisModelFactory.getQueryModel(uid, UserViewCountData.class);
                                //调用find方法
                                RedisModel model=iRedisService.find(queryModel);
                                //如果该uid已经存在
                                if(model.getValue()!=null){
                                    //获取该uid
                                    data = (UserViewCountData) model.getValue();
                                    //获取该uid对应的所有spus
                                    List<SpuCount> list = data.getSpus();
                                    //判断spuId是否存在
                                    boolean isExist = false;
                                    //循环spucount
                                    for(SpuCount spucount : list){
                                        //spuId已存在
                                        if(spucount.getSpuId().equals(spuId)){
                                            //次数直接+1
                                            spucount.setCount(spucount.getCount()+1);
                                            isExist=true;
                                            break;
                                        }
                                    }
                                    //如果spuId不存在
                                    if(!isExist){
                                        SpuCount spucount = new SpuCount();
                                        //添加该spuId
                                        spucount.setSpuId(spuId);
                                        //给个初始值1
                                        spucount.setCount(1);
                                        //将该spuId添加到spus集合中
                                        list.add(spucount);
                                    }
                                //如果该uid不存在
                                }else{
                                    //先添加该uid
                                    data.setUid(uid);
                                    List<SpuCount> list  = new ArrayList<SpuCount> ();
                                    SpuCount spucount = new SpuCount();
                                    //添加该spuId
                                    spucount.setSpuId(spuId);
                                    //给个初始值1
                                    spucount.setCount(1);
                                    //将该spuId添加到spus集合中
                                    list.add(spucount);
                                    //添加到UserViewCountData
                                    data.setSpus(list);
                                }
                              //获取RedisModel
                              RedisModel redisModel=RedisModelFactory.getRedisModel(data.getUid(), data);
                              //保存到redis
                              iRedisService.save(redisModel);
                            }
//                            Jedis jedis = new Jedis("172.16.14.128", 6379);
//
//                                //jedis.set(m1.group(1), m2.group(1));
//                                jedis.set(m1.group(),m2.group());
//                                jedis.close();
//                            }
                        }
                    }
                });
                return null;
            }
        });
//        //切割
//        JavaDStream<String> words = lines.flatMap(new FlatMapFunction<String, String>() {
//
//            public Iterable<String> call(String s) throws Exception {
//                return Arrays.asList(SPACE.split(s));
//            }
//        });
//        //统计次数
//        JavaPairDStream<String, Integer> wordCounts = words.mapToPair(new PairFunction<String, String, Integer>() {
//
//            public Tuple2<String, Integer> call(String s) throws Exception {
//                return new Tuple2<String, Integer>(s, 1);
//            }
//        }).reduceByKey(new Function2<Integer, Integer, Integer>() {
//
//            public Integer call(Integer a, Integer b) throws Exception {
//                return a + b;
//            }
//        });
//        //输出操作
//        wordCounts.foreachRDD(new Function<JavaPairRDD<String,Integer>, Void>() {
//
//            @Override
//            public Void call(JavaPairRDD<String, Integer> rdd) throws Exception {
//                //遍历循环rdd
//                rdd.foreach(new VoidFunction<Tuple2<String,Integer>>() {
//
//                    @Override
//                    public void call(Tuple2<String, Integer> wordcount) throws Exception {
//                        //看是否打印结果
//                        System.out.println(wordcount._1() + ":" + wordcount._2());
//                        //将wordcount以(k,v)键值对形式存入redis
//                        Jedis jedis = new Jedis("172.16.14.128", 6379);
//                        jedis.select(0);
//                        jedis.set(wordcount._1(), wordcount._2().toString());
//                    }
//                });
//                return null;
//            }
//        });
        //启动程序
        jssc.start();
        jssc.awaitTermination();
    }
```