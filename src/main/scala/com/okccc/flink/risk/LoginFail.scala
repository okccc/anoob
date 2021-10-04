package com.okccc.flink.risk

import java.time.Duration
import java.util

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.scala.createTypeInformation
import org.apache.flink.cep.PatternSelectFunction
import org.apache.flink.cep.scala.pattern.Pattern
import org.apache.flink.cep.scala.{CEP, PatternStream}
import org.apache.flink.streaming.api.scala.{DataStream, KeyedStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.windowing.time.Time

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
     * flink-CEP(Complex Event Processing)专门处理连续多次这种复杂事件
     * 处理事件的规则叫Pattern,定义输入流中的复杂事件,用来提取符合规则的事件序列,类似正则表达式
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

    // 2.获取数据
    val keyedStream: KeyedStream[LoginEvent, String] = env
      .readTextFile("input/LoginLog.csv")
      // 将流数据封装成样例类对象
      .map((data: String) => {
        val words: Array[String] = data.split(",")
        LoginEvent(words(0), words(1), words(2), words(3).toLong)
      })
      // 提取事件时间生成水位线
      .assignTimestampsAndWatermarks(
        WatermarkStrategy.forBoundedOutOfOrderness[LoginEvent](Duration.ofSeconds(0))
          .withTimestampAssigner(new SerializableTimestampAssigner[LoginEvent] {
            override def extractTimestamp(element: LoginEvent, recordTimestamp: Long): Long = element.timestamp * 1000
          })
      )
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

// 自定义模式选择函数
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