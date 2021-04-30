package com.okccc.flink.risk

import java.util
import java.util.Properties

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.scala.createTypeInformation
import org.apache.flink.cep.PatternSelectFunction
import org.apache.flink.cep.scala.pattern.Pattern
import org.apache.flink.cep.scala.{CEP, PatternStream}
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.{DataStream, KeyedStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer

/**
 * Author: okccc
 * Date: 2021/4/1 4:35 下午
 * Desc: 恶意登录检测,短时间内连续多次登录失败就认定为恶意登录,这个属于CEP里的严格近邻场景
 */

// 输入数据样例类
case class LoginEvent(userId: String, ip: String, eventType: String, timestamp: Long)
// 输出结果样例类,恶意登录用户的告警信息
case class LoginFailWarning(userId: String, firstFail: Long, lastFail: Long, msg: String)

object LoginFail {
  def main(args: Array[String]): Unit = {
    /**
     * com.okccc.flink-CEP(Complex Event Processing)专门针对连续多次这种复杂事件处理
     * 目标：从有序的简单事件流中发现一些高阶特征
     * 输入：一个或多个由简单事件构成的事件流
     * 处理：识别简单事件之间的内在联系,多个符合一定规则的简单事件构成复杂事件
     * 输出：满足规则的复杂事件
     *
     * 处理事件的规则叫Pattern,对输入流数据进行复杂事件规则定义,用来提取符合规则的事件序列
     * .begin()          模式序列必须以begin开始,且不能以notFollowedBy结束,not类型模式不能被optional修饰
     * .where()          筛选条件 .where().or().until()
     * .next()           严格近邻,事件必须严格按顺序出现  模式"a next b"  事件序列[a,c,b1,b2]不匹配
     * .followedBy()     宽松近邻,允许中间出现不匹配事件  模式"a followedBy b"  事件序列[a,c,b1,b2]匹配为{a,b1}
     * .followedByAny()  非确定性宽松近邻,进一步放宽条件  模式"a followedByAny b"  事件序列[a,c,b1,b2]匹配为{a,b1},{a,b2}
     * .notNext()        不想让某个事件严格近邻前一个事件
     * .notFollowedBy()  不想让某个事件在两个事件之间发生
     * .times()          定义事件次数
     * .within()         定义时间窗口
     */

    // 1.创建流处理执行环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 2.Source操作
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
//    prop.put("key.deserializer", classOf[StringDeserializer])
//    prop.put("value.deserializer", classOf[StringDeserializer])
    val inputStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("login", new SimpleStringSchema(), prop))

    // 3.Transform操作
    // 将流数据封装成样例类对象
    val keyedStream: KeyedStream[LoginEvent, String] = inputStream
      .map((data: String) => {
        val words: Array[String] = data.split(",")
        LoginEvent(words(0), words(1), words(2), words(3).toLong)
      })
      // 提取事件时间生成水位线
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[LoginEvent](Time.seconds(1)) {
        override def extractTimestamp(element: LoginEvent): Long = element.timestamp * 1000
      })
      // 按照userId分组
      .keyBy((le: LoginEvent) => le.userId)

    // 使用CEP进行登录校验
    // 1).定义模式序列pattern：5秒内连续3次登录失败事件,连续事件用.next不连续事件用.followedBy
    val loginFailPattern: Pattern[LoginEvent, LoginEvent] = Pattern
      .begin[LoginEvent]("first fail").where((le: LoginEvent) => le.eventType == "fail")
      .next("second fail").where((le: LoginEvent) => le.eventType == "fail")
      .next("third fail").where((le: LoginEvent) => le.eventType == "fail")
      .within(Time.seconds(5))
    // 事件类型相同的话可以.times简写成次数
    val loginFailPattern02: Pattern[LoginEvent, LoginEvent] = Pattern
      .begin[LoginEvent]("fail").where((le: LoginEvent) => le.eventType == "fail")
      .times(3).consecutive()
      .within(Time.seconds(5))
    // 2).将pattern应用到数据流
    val patternStream: PatternStream[LoginEvent] = CEP.pattern(keyedStream, loginFailPattern02)
    // 3).获取符合pattern的事件序列
    val resultStream: DataStream[LoginFailWarning] = patternStream.select(new LoginSelect())

    // 4.Sink操作
    resultStream.print("res")  // res> LoginFailWarning(1036,1558430843,1558430845,login fail)
    // 5.启动任务
    env.execute("login fail check")
  }
}

/*
 * 自定义模式选择函数
 * interface PatternSelectFunction<IN, OUT>
 * IN: 输入元素类型,LoginEvent
 * OUT: 输出元素类型,LoginFailWarning
 */
class LoginSelect() extends PatternSelectFunction[LoginEvent, LoginFailWarning] {
  // 模式匹配到的事件序列保存在util.Map<String(事件名称), util.List(事件序列)>
  override def select(pattern: util.Map[String, util.List[LoginEvent]]): LoginFailWarning = {
    // 根据key获取value列表
//    val firstFail: LoginEvent = pattern.get("first fail").get(0)
//    val thirdFail: LoginEvent = pattern.get("third fail").get(0)
    val iter: util.Iterator[LoginEvent] = pattern.get("fail").iterator()
    val firstFail: LoginEvent = iter.next()
    val secondFail: LoginEvent = iter.next()
    val thirdFail: LoginEvent = iter.next()
    // 封装成样例类对象返回结果
    LoginFailWarning(firstFail.userId, firstFail.timestamp, thirdFail.timestamp, "login fail")
  }
}