package com.okccc.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * @Author: okccc
 * @Date: 2021/10/28 10:11:11
 * @Desc: redis工具类
 */
public class RedisUtil {

    // 声明jedis连接池
    private static final JedisPool jedisPool;

    static {
        // 设置连接池配置信息
        JedisPoolConfig poolConfig = new JedisPoolConfig();

        // 最大可用连接数,默认8
        poolConfig.setMaxTotal(20);

        // 最大闲置连接数,默认8
        poolConfig.setMaxIdle(10);

        // 最小闲置连接数,默认0
        poolConfig.setMinIdle(5);

        // 连接耗尽时是否等待
        poolConfig.setBlockWhenExhausted(true);

        // 最大等待时长(ms)
        poolConfig.setMaxWaitMillis(3000);

        // 每次获取连接时进行测试ping pong
        poolConfig.setTestOnBorrow(true);

        // 创建jedis连接池
        jedisPool =  new JedisPool(poolConfig, "localhost", 6379, 3000);
    }

    /**
     * 获取jedis客户端
     */
    public static Jedis getJedis() {
        return jedisPool.getResource();
    }

    public static void main(String[] args) {
        // 测试连接
        Jedis jedis = getJedis();
        System.out.println(jedis.ping());  // PONG

        // 测试key
        jedis.set("orc", "grubby");
        jedis.set("ne", "moon");
        Set<String> keys = jedis.keys("*");
        System.out.println(keys.size());  // 2
        for (String key : keys) {
            System.out.println(key);
        }
        System.out.println(jedis.get("orc"));  // grubby
        System.out.println(jedis.exists("orc"));  // true
        System.out.println(jedis.ttl("orc"));  // -1

        // 测试string
        jedis.mset("orc", "fly", "ud", "ted");
        System.out.println(jedis.mget("orc", "ud"));  // [fly, ted]

        // 测试list
        jedis.lpush("games", "lol");
        jedis.lpush("games", "war3");
        jedis.lpush("games", "red alert");
        List<String> lrange = jedis.lrange("games", 0, -1);
        for (String s : lrange) {
            System.out.println(s);
        }

        // 测试set
        jedis.sadd("orders", "o1");
        jedis.sadd("orders", "o2");
        jedis.sadd("orders", "o3");
        jedis.sadd("orders", "o4");
        Set<String> smembers = jedis.smembers("orders");
        for (String s : smembers) {
            System.out.println(s);
        }

        // 测试zset
        jedis.zadd("skill", 99, "grubby");
        jedis.zadd("skill", 98, "moon");
        jedis.zadd("skill", 97, "sky");
        Set<String> zrange = jedis.zrange("skill", 0, -1);
        for (String s : zrange) {
            System.out.println(s);
        }

        // 测试hash
        jedis.hset("players", "name", "grubby");
        System.out.println(jedis.hget("players", "name"));
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("email", "orc@war3.com");
        hashMap.put("phone", "13888888888");
        jedis.hmset("players", hashMap);
        List<String> hmget = jedis.hmget("players", "phone", "email");
        for (String s : hmget) {
            System.out.println(s);
        }
    }
}
