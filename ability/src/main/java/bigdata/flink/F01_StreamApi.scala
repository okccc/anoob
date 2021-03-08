package bigdata.flink

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.serialization.{SimpleStringEncoder, SimpleStringSchema}
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.scala._  // 导入隐式转换
import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.elasticsearch.{ElasticsearchSinkFunction, RequestIndexer}
import org.apache.flink.streaming.connectors.kafka.internals.KeyedSerializationSchemaWrapper
import java.util.Properties

import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer

/**
 * @author okccc
 * @date 2021/2/14 11:18
 * @desc flink stream
 */
object F01_StreamApi {

  def main(args: Array[String]): Unit = {
    /**
     * 电商用户行为分析
     * 统计分析（实时）：热门商品、热门页面、访问流量、app市场推广统计、页面广告点击量
     * 风险控制（实时）：页面广告黑名单过滤、恶意登录监控、订单支付失效监控、支付实时对账
     * 偏好分析（离线）：点赞、收藏、评价、用户画像、推荐系统
     *
     * API
     * 批处理：数据累积到一定程度再处理,对应有界数据,API是DataSet
     * 流处理：数据来一条处理一条,对应无界数据,API是DataStream
     *
     * 窗口(window)
     * 流处理是按条处理,类似一分钟一小时内的统计操作就需要窗口,Window将无界流拆分成一个个bucket进行计算,窗口分为滚动窗口、滑动窗口、会话窗口
     * 窗口的生命周期是[start, end)左闭右开的,当属于窗口的第一个元素到达时就会开启一个窗口,当时间超过结束时间戳加上用户设置的延迟窗口就会关闭
     * 定义窗口之前要确定是否要keyBy()将原始流分成逻辑流,KeyedStream允许窗口计算由多个任务并行执行,non-KeyedStream窗口计算由单个任务执行
     *
     * 时间语义(time)
     * 发生顺序：事件时间(EventTime) - 提取时间(IngestionTime) - 处理时间(ProcessingTime)
     * 通常基于事件时间处理数据,好处是时间进度取决于数据本身而不是任何时钟,即使处理乱序数据也能获得准确结果,需要指定watermark表示事件时间的进度
     *
     * 水位线(watermark)
     * 流处理过程是event - source - operator,由于网络延迟和分布式等原因,event到达flink的顺序和其实际产生顺序不一致导致数据乱序
     * 事件时间内对于延迟数据不能无限期等下去,必须要有机制保证在特定时间后会触发窗口计算
     * watermark本质上是一个时间戳,表示数据流中timestamp小于watermark的数据都已到达从而触发window执行,是一种延迟触发机制,用于处理乱序数据
     * 标点水位线(Punctuated Watermark)
     * 定期水位线(Periodic Watermark)
     *
     * 迟到事件
     * 实际上接收到水位线以前的数据是不可避免的,这就是迟到事件,属于乱序事件的特例,它们的乱序程度超出了水位线的预计,在它们到达之前窗口已经关闭
     * 对于迟到事件flink默认是直接丢弃,也可以使用另外两种处理方式
     * allowLateNess是在窗口关闭后一直保留窗口状态至最大允许时长,迟到事件会触发窗口重新计算,保存状态需要更多额外内存,因此该时长不宜过久
     * sideOutPut是最后的兜底操作,所有过期的延迟数据在窗口彻底关闭后会被放到侧输出流,作为窗口计算结果的副产品以便用户获取并对其进行特殊处理
     *
     * window - watermark - allowLateNess - sideOutput
     *
     * flink特点：在保证exactly-once精准一次性的同时具有低延迟和高吞吐的处理能力
     * flink的世界观中,一切都是由流组成,离线数据是有界的流,实时数据是无界的流
     * flink可以实现批流统一,时间语义可以处理乱序数据,保证结果的正确性
     *
     * 数据模型
     * spark采用RDD模型,SparkStreaming的DStream实际上也是不同批次的RDD集合
     * flink基本数据模型是数据流,以及事件(Event)序列
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
     *
     *
     * flink状态管理
     * DataStream算子分为有状态和无状态,map/flatmap/filter等简单的转换算子就是无状态的,只和当前输入数据有关,aggregate/window等算子
     * 通常是有状态的,因为聚合函数或窗口函数输出的结果不仅仅和当前输入数据有关,还和之前所有数据的统计结果有关,这个统计结果就是当前任务的状态
     * 状态可以理解成本地变量,由于算子转换过程中会涉及序列化/检查点容错等复杂机制,flink运行任务时会自己进行状态管理,开发者只要专注于业务逻辑即可
     * 两种类型状态: 算子状态OperatorState、键控状态KeyedState(常用)
     * KeyedState根据输入数据流中定义的key来维护和访问
     * flink为每个key维护一个状态实例,并将具有相同key的数据都分到同一个算子任务中
     * 当任务处理一条数据时,会自动将状态的访问范围限定为当前数据的key
     *
     * could not find implicit value for evidence parameter of type org.apache.flink.api.common.typeinfo.TypeInformation[String]
     * val result:DataSet[(String,Int)] = inputDataSet.flatMap(_.split(" "))
     * scala版本要和flink引用的scala版本保持一致,不然会冲突导致无法使用scala的隐式转换
     */

//    batchProcess()
    streamProcess(args)
  }

  def batchProcess(): Unit = {
    // 创建批处理执行环境
    val env: ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment
    // flink可以读取text/csv等不同格式的文件
    val inputPath: String = "/Users/okc/projects/anoob/ability/input/aaa.txt"
    val inputDataSet: DataSet[String] = env.readTextFile(inputPath)
    // 词频统计
    val resultDataSet: AggregateDataSet[(String, Int)] = inputDataSet
      .flatMap((line: String) => line.split(" "))
      .map((word: String) => (word, 1))
      .groupBy(0)  // 以第一个元素作为key进行分组
      .sum(1)      // 对所有数据的第二个元素进行求和
    // 输出
    resultDataSet.print()
  }

  def streamProcess(args: Array[String]): Unit = {
    // 创建可执行的scala程序一般都选object单例对象,class需要创建对象才能执行
    // 1.创建流处理执行环境,scala会自动推断类型,所以定义变量的时候可以省略类型,但是为了增加可读性还是写上吧
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度,本地默认最大核数,生产环境一般可配置
    env.setParallelism(4)

    // 2.Source操作
//    // a.读取集合数据
//    val dataList: List[String] = List("aa", "bb", "cc")
//    val listDataStream: DataStream[String] = env.fromCollection(dataList)
//    // b.读取文件数据
//    val inputPath: String = ""
//    val fileDataStream: DataStream[String] = env.readTextFile(inputPath)
//    // c.监听socket流
//    // 从外部命令提取参数,Edit Configuration - Program arguments - 指定--host localhost --port 7777
//    val parameterTool: ParameterTool = ParameterTool.fromArgs(args)
//    val host: String = parameterTool.get("host")
//    val port: Int = parameterTool.getInt("port")
//    val socketDataStream: DataStream[String] = env.socketTextStream(host, port)
    // d.读取kafka数据
    val prop: Properties = new Properties()
    prop.put("bootstrap.servers", "localhost:9092")
    prop.put("group.id", "consumer-group")
    val kafkaDataStream: DataStream[String] = env.addSource(new FlinkKafkaConsumer[String]("nginx", new SimpleStringSchema(), prop))

    // 3.Transform操作
    val resultDataStream: DataStream[(String, Int)] = kafkaDataStream
      // _是lambda表达式的简写方式
      .flatMap((line: String) => line.split(" "))
      .filter((line: String) => line.nonEmpty)
      .map((word: String) => (word, 1))
      .keyBy(0)  // 以第一个元素作为key进行分组
      .sum(1)  // 对所有数据的第二个元素进行求和

    // 4.Sink操作
    // a.打印,可以人为设置并行度控制资源分配
    resultDataStream.print().setParallelism(1)
    // b.输出到文件
    // c.输出到es
    // d.输出到redis
    // e.输出到kafka

    // 5.启动任务,流处理是有头没尾源源不断的,开启后一直监听直到手动关闭
    env.execute("flink source and sink")
  }

}