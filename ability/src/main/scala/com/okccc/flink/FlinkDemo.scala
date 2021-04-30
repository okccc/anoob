package com.okccc.flink

import java.sql.{Connection, PreparedStatement}
import java.util

import org.apache.flink.api.common.serialization.{SimpleStringEncoder, SimpleStringSchema}
import org.apache.flink.api.scala._
import java.util.Properties

import com.okccc.flink.statistic.UserBehavior
import com.okccc.util.JdbcUtil
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.configuration.Configuration
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.functions.sink.SinkFunction.Context
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.elasticsearch.{ElasticsearchSinkFunction, RequestIndexer}
import org.apache.flink.streaming.connectors.elasticsearch6.ElasticsearchSink
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.streaming.connectors.redis.RedisSink
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig
import org.apache.flink.streaming.connectors.redis.common.mapper.{RedisCommand, RedisCommandDescription, RedisMapper}
import org.apache.http.HttpHost
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.Requests

/**
 * @author okccc
 * @date 2021/2/14 11:18
 * @desc com.okccc.flink quick start
 */
object FlinkDemo {
  def main(args: Array[String]): Unit = {
    /*
     * 电商用户行为分析
     * 统计分析(实时)：热门商品、热门页面、访问流量、app市场推广统计、页面广告点击量
     * 风险控制(实时)：页面广告黑名单过滤、恶意登录监控、订单支付失效监控、支付实时对账
     * 偏好分析(离线)：点赞、收藏、评价、用户画像、推荐系统
     *
     * flink特点
     * 低延迟&高吞吐,完美适合流式数据场景
     * 支持时间语义,EventTime + Watermark可以处理乱序数据
     * 支持窗口操作,各种灵活的窗口及触发条件处理复杂的流式计算
     * 容错性,轻量级的分布式快照在拥有高吞吐量的同时还能保证强一致性
     * 批流结合,批处理和流处理公用一个引擎
     * 内存管理,flink在jvm中实现了自己的内存管理,应用可以超出主内存的大小限制,承受更小的垃圾回收开销
     *
     * API
     * 有界流(批处理)：摄取所有数据再处理,DataSet API
     * 无界流(流处理)：摄取数据后立刻处理,DataStream API  分流操作：split(已弃用)/getSideOutput  合流操作：connect/union
     *
     * 窗口(window)
     * 窗口是为了周期性获取数据,类似一分钟一小时这种统计需求,Window将无界流拆分成一个个bucket进行计算,包括滚动窗口、滑动窗口、会话窗口
     * 窗口生命周期[start, end)左闭右开,当第一个元素到达时就会开启新窗口,当时间超过windowEnd加上用户设置的延时t就会关闭窗口执行计算
     * 分配窗口前可以先keyBy()将原始数据流按照key划分到不同区,KeyedStream窗口由多任务并行执行,non-KeyedStream窗口只能由单任务执行
     *
     * 时间语义(time)
     * 发生顺序：事件时间(EventTime) - 提取时间(IngestionTime) - 处理时间(ProcessingTime)
     * flink默认基于ProcessingTime处理流数据,这个适用于实时性非常高的场景,不会等待延迟数据,到窗口结束时间就计算,结果往往有偏差
     * 所以通常使用EventTime作为时间推进的考量,让时间进度取决于数据本身而不是任何时钟,配合watermark可以处理乱序数据获得准确结果
     *
     * 水位线(watermark)
     * 流处理过程是event - source - operator,由于网络延迟和分布式等原因会导致数据乱序,比如kafka多个分区之间的数据就是无序的
     * 窗口计算时对于事件时间内的延迟数据不能无限期等下去,必须要有某种机制保证到达特定时间(水位线)后就触发窗口计算,该机制就是watermark
     * 相当于把表调慢了,表示流中时间戳小于水位线的数据都已到达,当水位线越过窗口结束时间时窗口关闭开始计算,是一种延迟触发机制,专门处理乱序/迟到数据
     * watermark = 进入flink的最大事件时间(maxEventTime) - 指定延迟时间(t)    水位线会随着数据流动不断上升,是衡量EventTime进展的机制
     * 指定延迟时间t的设定：太大会导致等待时间过久实时性很差,太小实时性高了但是会漏数据导致结果不准确,数据的乱序程度应该是符合正态分布的,可以先设置
     * 一个很小的延迟时间hold住大部分延迟数据,剩下少量延迟太久的数据通过allowLateness和sideOutput处理,这样能同时兼顾结果准确性和实时性
     * 触发窗口计算条件：watermark >= windowEnd
     * 标点水位线(Punctuated Watermark) 每条数据后面都有一个水位线,适用于数据稀少的情况
     * 定期水位线(Periodic Watermark) 隔几条数据后面会有一个水位线,适用于数据密集的情况
     *
     * 迟到事件
     * 实际上接收到水位线以前的数据是不可避免的,这就是迟到事件,属于乱序事件的特例,它们的乱序程度超出了水位线的预计,在它们到达之前窗口已经关闭
     * 对于迟到事件flink默认直接丢弃,也可以使用另外两种处理方式
     * allowLateNess是在窗口关闭后一直保留窗口状态至最大允许时长,迟到事件会触发窗口重新计算,保存状态需要更多额外内存,因此该时长不宜过久
     * sideOutPut是最后的兜底操作,所有过期的延迟数据在窗口彻底关闭后会被放到侧输出流,作为窗口计算结果的副产品以便用户获取并对其进行特殊处理
     *
     * 乱序数据三重保证：window - watermark - allowLateNess - sideOutput
     * window将流数据分块,watermark确定什么时候不再等待更早的数据触发窗口计算,allowLateNess将窗口关闭时间再延迟一会儿,sideOutput兜底操作
     *
     * flink状态管理
     * DataStream算子分为有状态和无状态,map/flatmap/filter等简单的转换算子就是无状态的,只和当前输入数据有关,aggregate/window等算子
     * 通常是有状态的,因为聚合函数或窗口函数输出的结果不仅仅和当前输入数据有关,还和之前所有数据的统计结果有关,这个统计结果就是当前任务的状态
     * 状态可以理解成本地变量,由于算子转换过程中会涉及序列化/检查点容错等复杂机制,flink运行任务时会自己进行状态管理,开发者只需专注于业务逻辑
     * 两种类型状态: 算子状态OperatorState、键控状态KeyedState(常用)
     * KeyedState数据结构：ValueState(单值状态)/ListState(列表状态)/MapState(键值对状态)/ReducingState & AggregatingState(聚合状态)
     * flink会为每个key维护一个状态实例,并将具有相同key的数据分到同一个算子任务中,当任务处理一条数据时,会将状态的访问范围限定为当前数据的key
     *
     *
     * 运行架构
     * spark本质上是批处理,将DAG划分成不同stage,一个完成后才可以计算下一个
     * flink是标准的流处理,一个事件在一个节点处理完之后可以直接发往下一个节点进行处理,有头没尾源源不断
     *
     * Parallelism：算子的子任务个数,stream的并行度一般就是指所有算子中最大的并行度
     * TaskManager：对应一个jvm进程,会在独立的线程上执行一个或多个子任务,能接收多少个task由task slot控制,代表了TaskManager的并发执行能力
     * Slot：不同子任务也可以共享slot,一个slot可以保存作业的整个管道
     *
     * flink程序由三部分组成：Source读取数据源、Transformation利用各种算子加工处理、Sink输出
     * flink运行的程序会被映射成逻辑数据流DataFlows,以一个或多个source开始以一个或多个sink结束,类似于有向无环图DAG
     *
     * 常见问题：
     * 1.could not find implicit value for evidence parameter of type org.apache.com.okccc.flink.api.common.typeinfo.TypeInformation[String]
     * val result:DataSet[(String,Int)] = inputDataSet.flatMap(_.split(" "))
     * scala版本要和flink引用的scala版本保持一致,不然会冲突导致无法使用scala的隐式转换
     * 2.No ExecutorFactory found to execute the application.
     * flink1.11.0版本之后flink-clients依赖从flink-streaming-java中移除了,要单独添加依赖
     */

//    batchProcess()
    streamProcess(args)
  }

  def batchProcess(): Unit = {
    // scala可执行程序一般都选object单例对象,class类需要创建对象才能执行
    // 1.创建批处理执行环境
    val env: ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment
    // 2.Source操作,flink可以读取text/csv等不同格式的文件
    val inputPath: String = "/Users/okc/projects/anoob/ability/input/aaa.txt"
    val inputDataSet: DataSet[String] = env.readTextFile(inputPath)
    // 3.Transform操作
    val resultDataSet: AggregateDataSet[(String, Int)] = inputDataSet
      // _是lambda表达式的简写方式
      .flatMap((_: String).split(" "))
      .filter((_: String).nonEmpty)
      .map((_: String, 1))
      .groupBy(0)  // 以第一个元素作为key进行分组
      .sum(1)      // 对所有数据的第二个元素进行求和
    // 4.Sink操作
    resultDataSet.print()
  }

  def streamProcess(args: Array[String]): Unit = {
    // 1.创建流处理执行环境,scala会自动推断类型,所以定义变量的时候可以省略类型,但是为了增加可读性还是写上吧
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度,本地默认最大核数,生产环境一般可配置
    env.setParallelism(4)


    // 2.Source操作
//    // a.读取集合数据
//    val dataList: List[String] = List("aa", "bb", "cc")
//    val listStream: DataStream[String] = env.fromCollection(dataList)
//    // b.读取文件数据
//    val inputPath: String = ""
//    val fileStream: DataStream[String] = env.readTextFile(inputPath)
//    // c.监听socket流
//    // 从外部命令提取参数,Edit Configuration - Program arguments - 指定--host localhost --port 7777
//    val parameterTool: ParameterTool = ParameterTool.fromArgs(args)
//    val host: String = parameterTool.get("host")
//    val port: Int = parameterTool.getInt("port")
//    val socketStream: DataStream[String] = env.socketTextStream(host, port)
    // d.读取kafka数据
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    val kafkaStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.Transform操作
    val mapStream: DataStream[UserBehavior] = kafkaStream.map((data: String) => {
      // 将输入流封装成样例类对象
      val words: Array[String] = data.split(",")
      UserBehavior(words(0).toLong, words(1).toLong, words(2).toInt, words(3), words(4).toLong)
    })

    // 4.Sink操作
    // a.输出到控制台,可以人为设置并行度控制资源分配
    mapStream.print().setParallelism(1)
    // b.输出到文件
    val output: String = "/Users/okc/projects/anoob/ability/input"
//    mapStream.addSink(StreamingFileSink.forRowFormat(new Path(output), new SimpleStringEncoder[UserBehavior]()).build())
    // c.输出到mysql
//    mapStream.addSink(new MyJdbcSink())
    // d.输出到redis
    val flinkJedisPoolConfig: FlinkJedisPoolConfig = new FlinkJedisPoolConfig.Builder().setHost("localhost").setPort(6379).build()
    mapStream.addSink(new RedisSink[UserBehavior](flinkJedisPoolConfig, new MyRedisMapper()))
    // e.输出到es(可能存在版本问题)
    val httpHosts: util.ArrayList[HttpHost] = new util.ArrayList[HttpHost]()
    httpHosts.add(new HttpHost("localhost", 9200))
    // 这里创建的是ElasticsearchSink的非静态内部类Builder的对象
//    mapStream.addSink(new ElasticsearchSink.Builder[UserBehavior](httpHosts, new MyElasticsearchSinkFunction()).build())
    // f.输出到kafka(版本api有问题,貌似只能用010)
//    mapStream.addSink(new FlinkKafkaProducer011[String]("localhost:9092", "sinktest", new SimpleStringSchema()))

    // 5.启动任务,流处理是有头没尾源源不断的,开启后持续监听直到手动关闭
    env.execute("flink-stream")
  }
}


// 自定义SinkFunction
class MyJdbcSink extends RichSinkFunction[UserBehavior] {
  // 定义连接和预编译sql
  var conn: Connection = _
  var ps: PreparedStatement = _

  // 初始化工作,数据库连接等
  override def open(parameters: Configuration): Unit = {
    conn = JdbcUtil.getConnection
    ps = conn.prepareStatement("insert into user_behavior values (?,?,?,?,?)")
  }

  // 插入数据(这里只实现了简单的insert操作,实际情况要考虑去重和更新等复杂逻辑)
  override def invoke(value: UserBehavior, context: Context): Unit = {
    ps.setInt(1, value.userId.toInt)
    ps.setInt(2, value.itemId.toInt)
    ps.setInt(3, value.categoryId)
    ps.setString(4, value.behavior)
    ps.setString(5, value.timestamp.toString)
    ps.execute()
  }

  // 收尾工作,关闭连接等
  override def close(): Unit = {
    ps.close()
    conn.close()
  }
}


// 自定义RedisMapper
class MyRedisMapper extends RedisMapper[UserBehavior] {
  // 定义数据类型的描述符
  override def getCommandDescription: RedisCommandDescription = {
    // 指定redis数据类型hash(要根据实际业务场景考虑)  hset key field1 value1
    new RedisCommandDescription(RedisCommand.HSET, "user")
  }

  // 从对象中提取field
  override def getKeyFromData(data: UserBehavior): String = data.userId.toString

  // 从对象中提取value
  override def getValueFromData(data: UserBehavior): String = data.itemId.toString
}


// 自定义ElasticsearchSinkFunction
class MyElasticsearchSinkFunction extends ElasticsearchSinkFunction[UserBehavior] {
  override def process(element: UserBehavior, ctx: RuntimeContext, requestIndexer: RequestIndexer): Unit = {
    // 封装数据源对象
    val dataSource: util.HashMap[String, String] = new util.HashMap[String, String]()
    dataSource.put("user_id", element.userId.toString)
    dataSource.put("item_id", element.itemId.toString)
    dataSource.put("category_id", element.categoryId.toString)
    dataSource.put("behavior", element.behavior)
    dataSource.put("timestamp", element.timestamp.toString)

    // 创建请求
    val indexRequest: IndexRequest = Requests.indexRequest().index("behavior").source(dataSource)

    // 发送请求
    requestIndexer.add(indexRequest)
  }
}