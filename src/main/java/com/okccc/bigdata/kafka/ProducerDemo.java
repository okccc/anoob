package com.okccc.bigdata.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Author: okccc
 * Date: 2020/11/29 18:22
 * Desc: 模拟kafka生产者,实际场景一般是flume或者canal
 */
public class ProducerDemo {
    public static void main(String[] args) throws InterruptedException {
        /*
         * 消息队列
         * java提供的Queue是基于内存的单机版队列,MQ通常是分布式队列并且数据可以持久化,当然系统设计会更复杂
         * java提供的HashMap也是基于内存的单机版,可以使用redis存储键值对数据,分布式存储并且数据可以持久化
         *
         * 应用场景
         * 异步：页面注册 - 写数据库 - 调用发短信接口(将请求写入MQ,短信接口作为消费者会轮询MQ处理请求) - 响应用户
         * 解耦：A系统生产数据并调用接口发送到BCD系统,随着业务发展C下线了D故障了E进来了A得忙死,将数据写入MQ需要的自取
         * 缓冲：秒杀活动瞬间请求5K/s系统只能处理2K/秒,将请求写入MQ,系统按照消费能力pull数据,高峰期后请求50个/秒,系统很快就处理完积压的消息
         *
         * 两种模式
         * 点对点模式(一对一)：一个消息只能被一个消费者消费,消费完就从queue移除
         * 发布-订阅模式(一对多)：kafka生产者发布消息到topic,消费者订阅该消息,一个消息可以被多个消费者消费,且不管是否消费都会保留7天
         *
         * 消费方式
         * push模式是消费者被动接受发送过来的数据,难以适应消费速率不同的消费者,消费者来不及处理可能会导致网络拥堵甚至程序崩溃
         * pull模式是消费者根据自身消费能力主动去broker拉数据,缺点是broker没有数据时会陷入空循环,需要指定超时参数timeout
         *
         * 生产者数据可靠性
         * 为了保证生产者发送数据的可靠性,topic的每个partition收到数据后都要向生产者发送ack确认,生产者收到ack才会发送下一轮数据,否则重新发送
         * 1).副本同步策略
         * leader何时向生产者发送ack呢?
         * 方案1：半数follower同步完成就发送ack,优点是延迟低,缺点是选举新leader容忍n个节点的故障需要2n+1个副本会造成大量数据冗余
         * 方案2：所有follower同步完成才发送ack,优点是选举新leader容忍n个节点的故障只需要n+1个副本,缺点是有的节点同步速度慢导致延迟高
         * kafka采用的是第二种,因为网络延迟对kafka的影响相较于数据冗余要小很多
         * 2).Isr
         * 如果有一个follower故障导致迟迟不能与leader同步,也要一直等它同步结束才发送ack吗?
         * leader维护了一个动态的副本同步队列Isr(in-sync replica),存放和leader保持同步的follower集合,只要Isr同步完成leader就发送ack
         * 如果follower长时间不同步数据就会被Isr剔除,通过replica.lag.time.max.ms参数设定,当leader故障时会从Isr中选举新的leader
         * 3).ack可靠性级别
         * 有些数据对可靠性要求不高允许有少量丢失,kafka提供了3种可靠性级别,用户可以针对可靠性和延迟性权衡
         * ack=0 leader接收到数据还没落盘就返回ack,如果leader故障必然会丢数据
         * ack=1 leader落盘后返回ack,如果在follower同步完成前leader故障也会丢数据
         * ack=-1(all) leader和follower全部落盘才返回ack,如果在follower同步完成后发送ack前leader故障,生产者收不到ack会重发导致数据重复
         * 4).故障恢复
         * LEO(log end offset)每个副本的最后一个offset | HW(high watermark)所有副本中最小的LEO
         * LEO和HW只能保证数据的一致性,要么都丢数据要么都数据重复,数据不丢失不重复是由ack保证的
         * leader故障会从Isr中选举新leader,为了保证副本数据一致性,其余follower会将log文件高于HW的部分截掉,然后从新的leader同步数据
         * follower故障会被临时踢出Isr,恢复后读取本地磁盘记录的HW,并将log文件高于HW的部分截掉,然后同步数据,等到追上leader时会重新加入Isr
         * 5).精准发送
         * at most once 可能会丢数据 | at least once 可能数据重复 | exactly once 精准发送,保证每条消息都会发送且只发送一次
         * kafka0.11版本引入了幂等性机制(保证数据唯一性) enable.idempotence=true, at least once + idempotent = exactly once
         *
         * 生产者分区策略
         * a.指定partition
         * b.没有指定partition但是有key(比如userId),将key的hash值与partition数进行取余决定往哪个partition写数据
         * c.没有指定partition也没有key,将递增随机数与partition数进行取余决定往哪个partition写数据,这就是round-robin轮询算法
         *
         * controller和zk
         * kafka集群启动时会向zk注册节点信息,最先注册的broker节点就是controller,关闭kafka时zk的/controller和/brokers/ids会清空
         * controller会监控zk的节点变化情况,负责管理broker节点上下线/leader选举/topic分区和副本分配,zk辅助controller进行管理工作
         *
         * kafka高效读写数据
         * 1.顺序写磁盘：生产者往partition写数据是按顺序追加到log文件末尾,省去了大量的磁头寻址时间,写一个大文件比写多个小文件速度快得多
         * 2.零拷贝技术：计算机在网络中发送文件时直接在内核空间Kernel Space传输到网络,不用拷贝到用户空间User Space,省去在内存中的拷贝
         *
         * kafka出问题先看进程,再查日志
         * kafka关闭有延迟,如果zk先停了,/brokers/ids下的节点还在,此时kafka还存活但与zk失去连接导致无法停止,只能手动杀掉进程
         * kafka故障重启可能会导致kafka的logs/meta.properties的cluster.id不一致,把这个干掉,kafka重启之后会重新生成该文件
         */

        // 1.生产者属性配置
        Properties prop = new Properties();
        // 必选参数
        prop.put("bootstrap.servers", "localhost:9092");                 // kafka集群地址
        prop.put("key.serializer", StringSerializer.class.getName());    // key的序列化器
        prop.put("value.serializer", StringSerializer.class.getName());  // value的序列化器
        // 可选参数
        prop.put("acks", "all");                    // ack可靠性级别 0/1/-1(all)
        prop.put("enable.idempotence", true);       // 开启幂等性机制,配合ack=-1确保生产者exactly once
        prop.put("retries", 1);                     // 重试次数
        prop.put("batch.size", 1024*16);            // 批次大小,当数据累积到该数值后sender线程才会发送到kafka,可以控制生产者吞吐量
        prop.put("linger.ms", 10);                  // 等待时间,如果数据迟迟未达到batch.size大小,sender线程等待该时间后就发送数据
        prop.put("max.request.size", 1024*1024*5);  // 生产者往kafka批量发送请求的最大字节数,默认1M
        prop.put("buffer.memory", 1024*1024*32);    // 缓冲区大小
        // 添加拦截器集合(可选)
//        List<String> interceptors = new ArrayList<>();
//        interceptors.add("com.okccc.bigdata.kafka.InterceptorDemo");
//        prop.put("interceptor.classes", interceptors);

        // 2.创建生产者对象
        KafkaProducer<String, String> producer = new KafkaProducer<>(prop);

        // 3.往kafka发送数据
        while(true) {
            // 主题
            String topicName = "nginx";
            // 生产环境nginx埋点日志
            String event01 = "{ \"@timestamp\": \"22/Jun/2021:19:35:54 +0800\", \"hostname\": \"prod-bigdata-amp01\", \"remote_addr\": \"10.42.251.122\", \"ip\": \"122.236.115.191\", \"Method\": \"POST\", \"referer\": \"-\", \"request\": \"POST /amplitude/ HTTP/1.1\", \"request_body\": \"v=2&client=76382ab7cc9f61be703afadc802bf276&e=%5B%7B%22event_type%22%3A%22Cocos%20EnterSection%20LoadNodes%22%2C%22timestamp%22%3A1624361735816%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%221bcaff77-d30c-45bc-a25b-675235efad51%22%2C%22sequence_number%22%3A48956%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22des%22%3A%22T09%20enter%20game%20scene%22%2C%22scene%22%3A%22Game%22%2C%22timestamp%22%3A1624361735816%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93285%7D%2C%7B%22event_type%22%3A%22Cocos_Res_Render_Cost%22%2C%22timestamp%22%3A1624361735866%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%2264491ff9-42d1-4937-9c15-c77138c08047%22%2C%22sequence_number%22%3A48957%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22time%22%3A401%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93286%7D%2C%7B%22event_type%22%3A%22Sub%20Lesson%20View%20Success%22%2C%22timestamp%22%3A1624361736163%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%223b5a83a7-a319-40b7-af99-d9923a969e4d%22%2C%22sequence_number%22%3A48958%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22Type%22%3A%22newinteraction%22%2C%22LessonType%22%3A%22Compulsory%22%2C%22WeekNum%22%3A%22L2XXW20%22%2C%22ID%22%3A%22L2XX076sub01%22%2C%22Unit%22%3A%22L2XXU11%22%2C%22LoadDuring%22%3A%229%22%2C%22Name%22%3A%22%E4%BA%92%E5%8A%A8%E8%AF%BE%E5%A0%82%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93287%7D%2C%7B%22event_type%22%3A%22Cocos%20Game%20Start%22%2C%22timestamp%22%3A1624361736165%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%22f868012a-6277-4410-9ace-d3a985ed088a%22%2C%22sequence_number%22%3A48959%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22AdjustTime%22%3A0%2C%22Cocos%20Download%20In%20Loading%22%3Afalse%2C%22SubjectVersion%22%3A%221.0%22%2C%22Native%20Download%20in%20loading%22%3Atrue%2C%22LastDuration%22%3A1413%2C%22ABVersion%22%3A%22B%22%2C%22SessionId%22%3A%22d04ec3e8-a55d-4c5a-af12-ea775501ff98%22%2C%22SubLessonId%22%3A%22L2XX0761%22%2C%22Subject%22%3A%22XX%22%2C%22LastNodeName%22%3A%22Cocos%20Engine%20Start%22%2C%22TotalDuration%22%3A9886%2C%22GameId%22%3A%22L2XX0761%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93288%7D%2C%7B%22event_type%22%3A%22onPlayVideo%22%2C%22timestamp%22%3A1624361736358%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%22d6de76e6-7cc9-439e-b717-cd4c62fab22a%22%2C%22sequence_number%22%3A48960%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22msg%22%3A%22L2XX0761_V01%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93289%7D%2C%7B%22event_type%22%3A%22videoPlayerEvent%22%2C%22timestamp%22%3A1624361736359%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%223d240a2d-813d-42a7-b9e7-cdf075c65f71%22%2C%22sequence_number%22%3A48961%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22url%22%3A%22L2XX0761_V01%22%2C%22msg%22%3A%22playVideoWith%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93290%7D%2C%7B%22event_type%22%3A%22videoPlayerEvent%22%2C%22timestamp%22%3A1624361736368%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%22fdd2547f-d473-410e-8151-29c17d76091e%22%2C%22sequence_number%22%3A48962%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22url%22%3A%7B%22_super%22%3Anull%2C%22_name%22%3A%22%22%2C%22_objFlags%22%3A0%2C%22_native%22%3A%22%5C%2F%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.jiliguala.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%2C%22loadMode%22%3A0%2C%22loaded%22%3Atrue%2C%22url%22%3A%22%22%2C%22_callbackTable%22%3A%7B%7D%2C%22_audio%22%3A%22%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.jiliguala.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%7D%2C%22msg%22%3A%22playVideo%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93291%7D%2C%7B%22event_type%22%3A%22videoPlayerEvent%22%2C%22timestamp%22%3A1624361736477%2C%22user_id%22%3A%2267a651a99f2b4cdab7cc53d73503c5f1%22%2C%22device_id%22%3A%22f965f74d-7a49-4355-947f-1faff573669bR%22%2C%22session_id%22%3A1624361713074%2C%22uuid%22%3A%222b22bad8-b60d-408d-b2f7-d58edbb33b70%22%2C%22sequence_number%22%3A48963%2C%22version_name%22%3A%2211.5.1%22%2C%22os_name%22%3A%22android%22%2C%22os_version%22%3A%2210%22%2C%22device_brand%22%3A%22HUAWEI%22%2C%22device_manufacturer%22%3A%22HUAWEI%22%2C%22device_model%22%3A%22TAS-AN00%22%2C%22carrier%22%3A%22%E4%B8%AD%E5%9B%BD%E7%94%B5%E4%BF%A1%22%2C%22country%22%3A%22CN%22%2C%22language%22%3A%22zh%22%2C%22platform%22%3A%22Android%22%2C%22library%22%3A%7B%22name%22%3A%22amplitude-android%22%2C%22version%22%3A%222.23.2%22%7D%2C%22api_properties%22%3A%7B%22limit_ad_tracking%22%3Afalse%2C%22gps_enabled%22%3Afalse%7D%2C%22event_properties%22%3A%7B%22url%22%3A%7B%22_super%22%3Anull%2C%22_name%22%3A%22%22%2C%22_objFlags%22%3A0%2C%22_native%22%3A%22%5C%2F%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.jiliguala.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%2C%22loadMode%22%3A0%2C%22loaded%22%3Atrue%2C%22url%22%3A%22%22%2C%22_callbackTable%22%3A%7B%7D%2C%22_audio%22%3A%22%5C%2Fdata%5C%2Fuser%5C%2F0%5C%2Fcom.jiliguala.niuwa%5C%2Ffiles%5C%2Fstorage%5C%2Fgame%5C%2FNativeGame%5C%2Fpackage%5C%2Fvideo%5C%2FL2XX0761_V01.mp4%22%7D%2C%22msg%22%3A%22android%20ready-to-play%22%2C%22Business%22%3A%22GuaEnglish%22%2C%22App%22%3A%22JLGL%22%7D%2C%22user_properties%22%3A%7B%7D%2C%22groups%22%3A%7B%7D%2C%22group_properties%22%3A%7B%7D%2C%22event_id%22%3A93292%7D%5D&upload_time=1624361754400&checksum=03a53b51d7270bed39637a7eacdd3ff8\", \"status\": \"200\", \"bytes\": \"17\", \"agent\": \"okhttp/4.2.2\", \"x_forwarded\": \"122.236.115.191\"}";
//            String event02 = "{ \"@timestamp\": \"22/Jun/2021:19:35:54 +0800\"";
//            String eventLog = getEventLog();
            // 将消息封装成ProducerRecord发送,可以指定topic/partition/key/value,还能添加回调函数在producer收到ack时调用
            // 生产者分区策略：1.指定partition  2.不指定partition但是指定key(比如userId)  3.既不指定partition也不指定key
            producer.send(new ProducerRecord<>(topicName, event01));
//            producer.send(new ProducerRecord<>(topicName, event02));
            Thread.sleep(1000);
        }
    }

    public static String getEventLog() {
        // 创建字符串缓冲区
        StringBuilder sb = new StringBuilder();
        // 生成时间
        String time = DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss"));
        // 随机生成用户ID
        String uid = "u-" + new Random().nextInt(1000);
        // 随机生成行为
        String[] actions = {"like", "comment", "follow", "collect", "vote"};
        String action = actions[new Random().nextInt(5)];
        // 往字符串缓冲区添加内容,拼接成一条日志
        sb.append(time).append("\t").append(uid).append("\t").append(action);
        // 返回这条日志
        return sb.toString();
    }
}