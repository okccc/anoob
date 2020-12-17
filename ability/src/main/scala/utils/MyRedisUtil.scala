package utils

import java.util.Properties

import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

/**
 * @author okccc
 * @date 2020/12/13 10:31
 * @desc 获取Jedis的工具类
 */
object MyRedisUtil {

  // 声明jedisPool连接池
  private var jedisPool: JedisPool = _

  // 从jedisPool连接池中获取jedis
  def getJedis: Jedis = {
    if (jedisPool == null) {
      jedisPool = build()
    }
    val jedis: Jedis = jedisPool.getResource
    jedis
  }

  // 创建JedisPool连接池对象
  def build(): JedisPool = {
    // 读取配置文件
    val prop: Properties = MyPropertiesUtil.load("config.properties")
    val host: String = prop.getProperty("redis.host")
    val port: String = prop.getProperty("redis.port")
    // 连接池配置信息
    val config: JedisPoolConfig = new JedisPoolConfig()
    config.setMaxTotal(100)  // 最大连接数
    config.setMaxIdle(20)  // 最大空闲数
    config.setMinIdle(20)  // 最小空闲数
    config.setBlockWhenExhausted(true)  // 忙碌时是否等待
    config.setMaxWaitMillis(5000)  // 最大等待时长(ms)
    config.setTestOnBorrow(true)  // 每次获得连接进行测试
    // 创建JedisPool连接池
    val jedisPool: JedisPool = new JedisPool(config, host, port.toInt)
    jedisPool
  }

  def main(args: Array[String]): Unit = {
    val jedis: Jedis = getJedis
    // 测试是否能ping通
    println(jedis.ping())  // PONG
    // 释放资源
    jedis.close()
  }

}
