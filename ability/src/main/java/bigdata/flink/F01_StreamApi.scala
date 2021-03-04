package bigdata.flink

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.serialization.{SimpleStringEncoder, SimpleStringSchema}
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.api.scala._
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
     * 批处理处理的是大容量的静态数据,特点是海量、有界、持久化,数据全部到齐才能计算
     * 流数据处理的是短时间的动态数据,特点是无界、基于事件触发,数据来一条处理一条
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
     * 窗口(window)：真实的流都是无界的,窗口就是将无限的数据流切割为有限流的一种方式,将流数据分发到有限大小的桶(bucket)中进行分析
     * 时间窗口(Time Window)：滚动时间窗口、滑动时间窗口、会话窗口
     * 计数窗口(Count Window)：滚动计数窗口、滑动计数窗口
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