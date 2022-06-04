package com.okccc.warehouse.kafka;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Author: okccc
 * Date: 2020/11/29 18:22
 * Desc: 模拟kafka生产者,实际场景一般是flume或者canal
 * kafka所有配置参数在官方文档都有具体介绍,源码和文档结合使用 https://kafka.apache.org/documentation
 */
public class ProducerDemo {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        /*
         * 消息队列
         * java提供的Queue是基于内存的单机版队列,MQ通常是分布式队列并且数据可以持久化,当然系统设计会更复杂
         * java提供的HashMap也是基于内存的单机版,可以使用redis存储键值对数据,分布式存储并且数据可以持久化
         *
         * 应用场景
         * 异步：页面注册 - 写数据库 - 调用发短信接口(将请求写入MQ,短信接口作为消费者会轮询MQ处理请求) - 响应用户
         * 解耦：A系统生产数据并调用接口发送到BCD系统,随着业务发展C下线了D故障了E进来了A得忙死,将数据写入MQ需要的自取
         * 缓冲：秒杀活动请求5000/s系统只能处理1000/s,将请求写入MQ,系统按照消费能力pull数据,高峰期后请求50/s,系统会很快处理完积压消息
         *
         * 两种模式
         * 点对点模式(一对一)：一个消息只能被一个消费者消费,消费完就从queue移除
         * 发布-订阅模式(一对多)：kafka生产者发布消息到topic,消费者订阅该消息,一个消息可以被多个消费者消费,且不管是否消费都会保留7天
         *
         * 消费方式
         * push模式是消费者被动接受发送过来的数据,难以适应消费速率不同的消费者,消费者来不及处理可能会导致网络拥堵甚至程序崩溃
         * pull模式是消费者根据自身消费能力主动去broker拉数据,缺点是broker没有数据时会陷入空循环,需要指定超时参数timeout
         *
         * topic分区好处,大数据场景主要考虑存储和计算两个方面
         * 存储：将大量数据按照partition切割存储在多个broker达到负载均衡
         * 计算：将数据以partition为单位划分可以提高producer和consumer的并行度
         *
         * 生产者分区策略,全局搜索DefaultPartitioner类查看源码注释
         * a.指定partition
         * b.没有指定partition但是有key(user_id/order_info),将key的hash值与partition数取余决定写往哪个partition(很有用)
         * c.没有指定partition也没有key,采用StickyPartition粘性分区器,先随机选择一个分区一直写,等该分区batch已满再换新的分区
         *
         * 生产者数据可靠性
         * 1.ack可靠性级别
         * kafka收到数据后要向生产者发送ack确认,生产者收到ack才会发送下一轮数据,没收到就重新发送,针对可靠性和延迟性分为3种级别
         * ack=0 leader接收到数据还没落盘就返回ack,如果leader故障必然会丢数据
         * ack=1 leader落盘后返回ack,如果在follower同步完成前leader故障也会丢数据
         * ack=-1 leader和follower全部落盘才返回ack,如果在follower同步完成后发送ack前leader故障,生产者收不到ack会重发导致数据重复
         * 2.ISR
         * ack=-1时,如果某个follower故障导致迟迟不能与leader同步,也要一直等它同步结束才发送ack吗?
         * leader维护了一个动态副本同步队列Isr(in-sync replica),存放和leader保持同步的follower集合,只要Isr同步完成leader就发送ack
         * 如果follower长时间不同步数据就会被Isr剔除,可通过replica.lag.time.max.ms参数设定,默认30s,当leader故障时会从Isr中选举新的
         *
         * 数据重复
         * at most once 可能会丢数据(UDP) | at least once 可能数据重复 | exactly once 精准发送,保证每条消息都会发送且只发送一次
         * kafka0.11版本引入了幂等性机制,生产者不管发送多少次数据broker只会持久化一条 at least once + idempotent = exactly once
         * 重复数据判断标准是主键<pid,partition,seqNum>,pid是每次kafka重启会分配一个新的,partition是分区号,seqNum单调自增
         * 所以幂等性只能保证单分区单会话内数据不重复,完全不重复还得在幂等性的基础上开启事务
         *
         * 数据乱序(重点)
         * kafka1.x后的版本可以保证数据单分区有序,设置参数max.in.flight.requests.per.connection=1(未开启幂等性)/<=5(开启幂等性)
         * 多分区有序的话只能consumer收到数据后自己排序了,但是很影响性能还不如只弄一个分区,spark和flink的窗口可以实现该功能
         *
         * controller和zk
         * kafka集群启动时会向zk注册节点信息,最先注册的broker节点就是controller,关闭kafka时zk的/controller和/brokers/ids会清空
         * controller会监控zk的节点变化情况,负责管理broker节点上下线/leader选举/topic分区和副本分配,zk辅助controller进行管理工作
         *
         * broker故障恢复
         * LEO(log end offset)每个副本的最后一个offset | HW(high watermark)所有副本中最小的LEO
         * LEO和HW只能保证数据的一致性,要么都丢数据要么都数据重复,数据不丢失不重复是由ack和幂等性保证的
         * leader故障会从Isr中选举新leader,为了保证副本数据一致性,其余follower会将log文件高于HW的部分截掉,然后从新的leader同步数据
         * follower故障会被临时踢出Isr,恢复后读取本地磁盘记录的HW,并将log文件高于HW的部分截掉,然后同步数据直到追上leader再重新加入Isr
         *
         * kafka高效读写数据
         * 1.将数据分区提高并行度高
         * 2.读数据采用稀疏索引,可以快速定位要消费的数据
         * 3.顺序写磁盘：生产者将数据按顺序追加到log文件末尾,省去了大量的磁头寻址时间,写一个大文件比写多个小文件速度快得多
         * 4.零拷贝和页缓存：kafka数据加工都由生产者和消费者处理,broker应用层不关心存储的数据,传输时就不用走应用层提高效率
         */

        // 1.生产者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");  // kafka集群地址
        prop.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());    // key序列化器
        prop.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());  // value序列化器
        // 生产者吞吐量
        prop.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1024*1024*32);  // 缓冲区大小,默认32m
        prop.put(ProducerConfig.BATCH_SIZE_CONFIG, 1024*16);          // 批次大小,默认16k
        prop.put(ProducerConfig.LINGER_MS_CONFIG, 10);                // 等待时间,默认0
        prop.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");   // 压缩类型,默认none
        // 生产者可靠性
        prop.put(ProducerConfig.ACKS_CONFIG, "all");                  // ack可靠性级别,0基本不用、1普通日志、-1(all)涉及钱的
        prop.put(ProducerConfig.RETRIES_CONFIG, 1);                   // 重试次数
        prop.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);     // 开启幂等性
        prop.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "tid");      // 开启事务

        // 添加拦截器集合(可选)
        List<String> interceptors = new ArrayList<>();
        interceptors.add("com.okccc.warehouse.kafka.InterceptorDemo");
        prop.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, interceptors);
        // 添加分区器(可选)
        prop.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, "com.okccc.warehouse.kafka.PartitionerDemo");

        // 2.创建生产者对象,<String, String>是topics和record
        KafkaProducer<String, String> producer = new KafkaProducer<>(prop);

        // 3.往kafka发送数据
        while(true) {
//            String topics = "nginx";
//            String log = "{ \"@timestamp\": \"22/Jun/2021:19:35:54 +0800\", \"hostname\": \"prod-bigdata-amp01\", \"remote_addr\": \"10.42.251.122\", \"ip\": \"122.236.115.191\", \"Method\": \"POST\", \"referer\": \"-\", \"request\": \"POST /amplitude/ HTTP/1.1\", \"request_body\": \"v=2&client=76382ab7cc9f61be703afadc802bf276&e=%5B%7B%22event_type%22%3A%22Cocos%20EnterSection%20LoadNodes%22%2C%22timestamp%22%3A1624361735816%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%221bcaff77-d30c-45bc-a25b-675235efad51%22%2C%22sequence_number%22%3A48956%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22des%22%3A%22T09%20enter%20game%20scene%22%2C%22scene%22%3A%22Game%22%2C%22timestamp%22%3A1624361735816%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22company%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93285%7D%2C%7B%22event_type%22%3A%22Cocos_Res_Render_Cost%22%2C%22timestamp%22%3A1624361735866%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%2264491ff9-42d1-4937-9c15-c77138c08047%22%2C%22sequence_number%22%3A48957%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22time%22%3A401%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22company%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93286%7D%2C%7B%22event_type%22%3A%22Sub%20Lesson%20View%20Success%22%2C%22timestamp%22%3A1624361736163%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%223b5a83a7-a319-40b7-af99-d9923a969e4d%22%2C%22sequence_number%22%3A48958%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22Type%22%3A%22newinteraction%22%2C%22LessonType%22%3A%22Compulsory%22%2C%22WeekNum%22%3A%22L2XXW20%22%2C%22ID%22%3A%22L2XX076sub01%22%2C%22Unit%22%3A%22L2XXU11%22%2C%22LoadDuring%22%3A%229%22%2C%22Name%22%3A%22%E4%BA%92%E5%8A%A8%E8%AF%BE%E5%A0%82%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22company%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93287%7D%2C%7B%22event_type%22%3A%22Cocos%20Game%20Start%22%2C%22timestamp%22%3A1624361736165%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%22f868012a-6277-4410-9ace-d3a985ed088a%22%2C%22sequence_number%22%3A48959%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22AdjustTime%22%3A0%2C%22Cocos%20Download%20In%20Loading%22%3Afalse%2C%22SubjectVersion%22%3A%221.0%22%2C%22Native%20Download%20in%20loading%22%3Atrue%2C%22LastDuration%22%3A1413%2C%22ABVersion%22%3A%22B%22%2C%22SessionId%22%3A%22d04ec3e8-a55d-4c5a-af12-ea775501ff98%22%2C%22SubLessonId%22%3A%22L2XX0761%22%2C%22Subject%22%3A%22XX%22%2C%22LastNodeName%22%3A%22Cocos%20Engine%20Start%22%2C%22TotalDuration%22%3A9886%2C%22GameId%22%3A%22L2XX0761%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22company%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93288%7D%2C%7B%22event_type%22%3A%22onPlayVideo%22%2C%22timestamp%22%3A1624361736358%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%22d6de76e6-7cc9-439e-b717-cd4c62fab22a%22%2C%22sequence_number%22%3A48960%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22msg%22%3A%22L2XX0761_V01%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22company%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93289%7D%2C%7B%22event_type%22%3A%22videoPlayerEvent%22%2C%22timestamp%22%3A1624361736359%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%223d240a2d-813d-42a7-b9e7-cdf075c65f71%22%2C%22sequence_number%22%3A48961%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22url%22%3A%22L2XX0761_V01%22%2C%22msg%22%3A%22playVideoWith%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22company%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93290%7D%2C%7B%22event_type%22%3A%22videoPlayerEvent%22%2C%22timestamp%22%3A1624361736368%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%22fdd2547f-d473-410e-8151-29c17d76091e%22%2C%22sequence_number%22%3A48962%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22url%22%3A%7B%22_super%22%3Anull%2C%22_name%22%3A%22%22%2C%22_objFlags%22%3A0%2C%22_native%22%3A%22%5C%2F%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.company.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%2C%22loadMode%22%3A0%2C%22loaded%22%3Atrue%2C%22url%22%3A%22%22%2C%22_callbackTable%22%3A%7B%7D%2C%22_audio%22%3A%22%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.company.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%7D%2C%22msg%22%3A%22playVideo%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22company%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93291%7D%2C%7B%22event_type%22%3A%22videoPlayerEvent%22%2C%22timestamp%22%3A1624361736477%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%222b22bad8-b60d-408d-b2f7-d58edbb33b70%22%2C%22sequence_number%22%3A48963%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22url%22%3A%7B%22_super%22%3Anull%2C%22_name%22%3A%22%22%2C%22_objFlags%22%3A0%2C%22_native%22%3A%22%5C%2F%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.company.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%2C%22loadMode%22%3A0%2C%22loaded%22%3Atrue%2C%22url%22%3A%22%22%2C%22_callbackTable%22%3A%7B%7D%2C%22_audio%22%3A%22%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.company.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%7D%2C%22msg%22%3A%22android%20ready-to-play%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22company%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93292%7D%5D&upload_time=1624361754400&checksum=03a53b51d7270bed39637a7eacdd3ff8\", \"status\": \"200\", \"bytes\": \"17\", \"agent\": \"okhttp/4.2.2\", \"x_forwarded\": \"122.236.115.191\"}";
//            String log01 = "{ \"@timestamp\": \"22/Jun/2021:19:35:54 +0800\"";
            String topics = "canal";
            String log = "{\"data\":[{\"id\":\"21987652\",\"platform\":\"9\",\"channel\":\"0\",\"order_no\":\"O86608711347716096\",\"user_no\":\"2c457aefda67461ca9cf222bad1bfcc0\",\"buy_way\":\"0\",\"state\":\"2\",\"address_snap_id\":\"5782948\",\"billing_type\":\"0\",\"origin_price\":\"19900.0\",\"price\":\"990.0\",\"coupon_no\":null,\"coupon_discount\":\"0.0\",\"gua_dou_num\":\"0\",\"gua_dou_price\":\"0.0\",\"diamond_num\":\"0\",\"diamond_price\":\"0.0\",\"magika_num\":\"0\",\"magika_price\":\"0.0\",\"discount\":\"0.0\",\"admin_discount\":\"0.0\",\"pay_price\":\"990.0\",\"pay_channel\":\"wx\",\"charge_id\":\"7128257\",\"user_agent\":\"Dalvik/2.1.0 (Linux; U; Android 10; TAS-AN00 Build/HUAWEITAS-AN00); NiuWa : 110701; AndroidVersion : 11.7.1\",\"user_remarks\":null,\"admin_remarks\":null,\"mq_state\":\"1\",\"commodity_category\":\"6\",\"pay_at\":\"2021-07-28 14:15:26\",\"delete_at\":null,\"create_at\":\"2021-07-28 14:15:16\",\"update_at\":\"2021-07-28 14:15:27\",\"source\":null,\"marketing_channel_code\":null,\"flag\":\"1\"}],\"database\":\"eshop_orders\",\"es\":1627452927000,\"id\":680881,\"isDdl\":false,\"mysqlType\":{\"id\":\"bigint\",\"platform\":\"tinyint\",\"channel\":\"tinyint\",\"order_no\":\"varchar(40)\",\"user_no\":\"varchar(32)\",\"buy_way\":\"tinyint\",\"state\":\"tinyint\",\"address_snap_id\":\"bigint\",\"billing_type\":\"tinyint\",\"origin_price\":\"decimal(10,2)\",\"price\":\"decimal(10,2)\",\"coupon_no\":\"varchar(50)\",\"coupon_discount\":\"decimal(10,2)\",\"gua_dou_num\":\"int\",\"gua_dou_price\":\"decimal(10,2)\",\"diamond_num\":\"int\",\"diamond_price\":\"decimal(10,2)\",\"magika_num\":\"int\",\"magika_price\":\"decimal(10,2)\",\"discount\":\"decimal(10,2)\",\"admin_discount\":\"decimal(10,2)\",\"pay_price\":\"decimal(10,2)\",\"pay_channel\":\"varchar(32)\",\"charge_id\":\"bigint\",\"user_agent\":\"text\",\"user_remarks\":\"varchar(255)\",\"admin_remarks\":\"varchar(255)\",\"mq_state\":\"tinyint\",\"commodity_category\":\"tinyint\",\"pay_at\":\"timestamp\",\"delete_at\":\"timestamp\",\"create_at\":\"timestamp\",\"update_at\":\"timestamp\",\"source\":\"varchar(255)\",\"marketing_channel_code\":\"varchar(255)\",\"flag\":\"tinyint\"},\"old\":[{\"mq_state\":\"0\"}],\"pkNames\":[\"id\"],\"sql\":\"\",\"sqlType\":{\"id\":-5,\"platform\":-6,\"channel\":-6,\"order_no\":12,\"user_no\":12,\"buy_way\":-6,\"state\":-6,\"address_snap_id\":-5,\"billing_type\":-6,\"origin_price\":3,\"price\":3,\"coupon_no\":12,\"coupon_discount\":3,\"gua_dou_num\":4,\"gua_dou_price\":3,\"diamond_num\":4,\"diamond_price\":3,\"magika_num\":4,\"magika_price\":3,\"discount\":3,\"admin_discount\":3,\"pay_price\":3,\"pay_channel\":12,\"charge_id\":-5,\"user_agent\":2005,\"user_remarks\":12,\"admin_remarks\":12,\"mq_state\":-6,\"commodity_category\":-6,\"pay_at\":93,\"delete_at\":93,\"create_at\":93,\"update_at\":93,\"source\":12,\"marketing_channel_code\":12,\"flag\":-6},\"table\":\"orders\",\"ts\":1627452927578,\"type\":\"UPDATE\"}";

            // 将消息封装成ProducerRecord发送,可以指定topic/partition(N)/key(N)/value,还能添加回调函数在producer收到ack时调用
            // 同步发送：生产者将外部数据往缓存队列中放一批,必须等这批数据发送到kafka集群再继续放下一批(不常用)
//            producer.send(new ProducerRecord<>(topics, log)).get();
            // 异步发送：生产者将外部数据一批一批往缓存队列中放,不管这批数据有没有发送到kafka集群
            producer.send(new ProducerRecord<>(topics, log), (metadata, exception) -> {
                if (exception == null) {
                    System.out.println("topic = " + metadata.topic() + ", partition = " + metadata.partition());
                }
            });
            Thread.sleep(1000);
        }
    }
}