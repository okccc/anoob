package bigdata.flink

import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer011

import java.util.Properties

/**
 * @author okccc
 * @date 2021/2/14 11:18
 * @desc flink基础
 */
object F01_Source {

  def main(args: Array[String]): Unit = {
    /**
     * 流数据更真实地反映生活方式
     * flink特点：低延迟、高吞吐、可容错、精准一次性exactly-once、支持事件时间event-time和处理时间processing-time语义
     * flink的世界观中,一切都是由流组成,离线数据是有界的流,实时数据是无界的流
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
     * TaskManager：对应一个jvm进程,会在独立的线程上执行一个或多个子任务,能接收多少个task由task slot控制,代表了TaskManager的并发执行能力1
     * Slot：不同子任务也可以共享slot,一个slot可以保存作业的整个管道
     *
     * flink程序由三部分组成：Source读取数据源、Transformation利用各种算子加工处理、Sink输出
     * flink运行的程序会被映射成逻辑数据流DataFlows,以一个或多个source开始以一个或多个sink结束,类似于有向无环图DAG
     *
     * could not find implicit value for evidence parameter of type org.apache.flink.api.common.typeinfo.TypeInformation[String]
     * val result:DataSet[(String,Int)] = inputDataSet.flatMap(_.split(" "))
     * scala版本要和flink引用的scala版本保持一致,不然会冲突导致无法使用scala的隐式转换
     */

    // 批处理版本
//    batchProcess()
    // 流处理版本
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
    // 创建流处理执行环境,scala会自动推断类型,所以定义变量的时候可以省略类型,但是为了增加可读性还是写上吧
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    // 设置并行度,默认是最大核数,生产环境一般可配置
    env.setParallelism(4)

    // 从外部命令提取参数,Edit Configuration - Program arguments - 指定--host localhost --port 7777
    val parameterTool: ParameterTool = ParameterTool.fromArgs(args)
    val host: String = parameterTool.get("host")
    val port: Int = parameterTool.getInt("port")
    // 监听socket流
    val socketDataStream: DataStream[String] = env.socketTextStream(host, port)


    // 词频统计
    val resultDataStream: DataStream[(String, Int)] = socketDataStream
      // _是lambda表达式的简写方式
      .flatMap((line: String) => line.split(" "))
      .filter((line: String) => line.nonEmpty)
      .map((word: String) => (word, 1))
      .keyBy(0)  // 以第一个元素作为key进行分组
      .sum(1)  // 对所有数据的第二个元素进行求和
    // 输出,可以人为设置并行度控制资源分配
    resultDataStream.print().setParallelism(1)
    // 启动任务,流处理是有头没尾源源不断的,开启后一直监听直到手动关闭
    env.execute("stream word count")
  }

}
