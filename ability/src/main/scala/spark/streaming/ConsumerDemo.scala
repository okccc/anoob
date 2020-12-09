package spark.streaming

/**
 * @author okccc
 * @date 2020/12/9 3:38 下午
 * @version 1.0
 */
object ConsumerDemo {
  /**
   * SparkStreaming2.3版本读取kafka数据
   * kafka两个参数
   * heartbeat.interval.ms：kafka集群确保消费者保持连接的心跳通信间隔时间,默认3s.这个值必须设置的比session.timeout.ms小,一般设置不大于session.timeout.ms的1/3
   * session.timeout.ms：消费者与kafka之间的session会话超时时间,如果在这个时间内,kafka没有接收到消费者的心跳heartbeat.interval.ms,那么kafka将移除当前的消费者,默认30s
   * 这个时间位于配置 group.min.session.timeout.ms(6s) 和 group.max.session.timeout.m(300S)之间的一个参数,如果SparkSteaming 批次间隔时间大于5分钟,也就是大于300s,那么就要相应的调大group.max.session.timeout.ms 这个值
   * SparkStreaming读数据一般用 LocationStrategies.PreferConsistent 策略,将分区均匀的分布在集群的Executor之间
   * 如果Executor在kafka集群中的某些节点上,可以使用 LocationStrategies.PreferBrokers 策略,那么当前这个Executor中的数据会来自当前broker节点
   * 如果节点之间的分区有明显的分布不均,可以使用 LocationStrategies.PreferFixed 策略,通过map将topic分区分布在指定节点
   *
   * 新的消费者api可以将kafka中的消息预读取到缓存区中,默认大小为64k,默认缓存区在Executor中,加快处理数据速度
   * 可以通过参数 spark.streaming.kafka.consumer.cache.maxCapacity 调大,也可以通过spark.streaming.kafka.consumer.cache.enabled 设置成false 关闭缓存机制
   * 注意：官网中描述这里建议关闭,在读取kafka时如果开启会有重复读取同一个topic partition 消息的问题,报错：KafkaConsumer is not safe for multi-threaded access"
   *
   * 关于消费者offset
   * 1.设置checkpoint存储offset,缺点是可能重复消费
   * 2.kafka内置topic存储offset,设置enable.auto.commit=false,不然先提交后消费可能丢数据,缺点是kafka会定时清空offset
   * 3.自己存储offset,保证数据处理的事务,数据处理成功才保存,确保exactly once
   */
  def main(args: Array[String]): Unit = {

  }
}
