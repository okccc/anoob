package com.okccc.util

import java.util
import java.util.Properties

import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

/**
 * Author: okccc
 * Date: 2020/12/13 10:31
 * Desc: 获取Jedis的工具类
 */
object RedisUtil {

  // 声明jedis连接池
  private var jedisPool: JedisPool = _

  // 从jedis连接池中获取jedis
  def getJedis: Jedis = {
    if (jedisPool == null) {
      jedisPool = initJedisPool()
    }
    val jedis: Jedis = jedisPool.getResource
    jedis
  }

  // 创建jedis连接池
  def initJedisPool(): JedisPool = {
    // 1.获取redis地址
    val prop: Properties = PropertiesUtil.load("config.properties")
    val host: String = prop.getProperty("redis.host")
    val port: String = prop.getProperty("redis.port")

    // 2.设置连接池配置信息
    val config: JedisPoolConfig = new JedisPoolConfig()
    config.setMaxTotal(100)             // 最大可用连接数
    config.setMaxIdle(20)               // 最大闲置连接数
    config.setMinIdle(20)               // 最小闲置连接数
    config.setBlockWhenExhausted(true)  // 连接耗尽时是否等待
    config.setMaxWaitMillis(5000)       // 最大等待时长(ms)
    config.setTestOnBorrow(true)        // 每次获取连接时进行测试ping pong

    // 3.创建jedis连接池
    val jedisPool: JedisPool = new JedisPool(config, host, port.toInt)
    jedisPool
  }

  def main(args: Array[String]): Unit = {
    // jedis是java写的,所以返回的结果是java类型
    import scala.collection.JavaConverters._

    // 测试连接
    val jedis: Jedis = getJedis
    println(jedis.ping())  // PONG

    // 测试key
    jedis.set("orc", "grubby")
    jedis.set("ne", "moon")
    jedis.set("hum", "sky")
    val keys: util.Set[String] = jedis.keys("*")
    println(keys.size())
    for (elem <- keys.asScala) {
      println(elem)
    }
    println(jedis.get("orc"))
    println(jedis.exists("orc"))
    println(jedis.ttl("orc"))

    // 测试string
    jedis.mset("orc", "fly", "ud", "ted")
    println(jedis.mget("orc", "ud"))

    // 测试list
    jedis.lpush("games", "lol")
    jedis.lpush("games", "war3")
    jedis.lpush("games", "red alert")
    val range: util.List[String] = jedis.lrange("games", 0, -1)
    for (elem <- range.asScala) {
      println(elem)
    }

    // 测试set
    jedis.sadd("orders", "o1")
    jedis.sadd("orders", "o2")
    jedis.sadd("orders", "o3")
    jedis.sadd("orders", "o4")
    val members: util.Set[String] = jedis.smembers("orders")
    for (elem <- members.asScala) {
      println(elem)
    }

    // 测试zset
    jedis.zadd("skill", 99, "grubby")
    jedis.zadd("skill", 98, "moon")
    jedis.zadd("skill", 97, "sky")
    val zrange: util.Set[String] = jedis.zrange("skill", 0, -1)
    for (elem <- zrange.asScala) {
      println(elem)
    }

    // 测试hash
    jedis.hset("players", "name", "grubby")
    println(jedis.hget("players", "name"))
    val hm: util.HashMap[String, String] = new util.HashMap[String, String]()
    hm.put("email", "orc@war3.com")
    hm.put("age", "19")
    hm.put("phone", "13888888888")
    jedis.hmset("players", hm)
    val values: util.List[String] = jedis.hmget("players", "phone", "email")
    for (elem <- values.asScala) {
      println(elem)
    }
  }
}