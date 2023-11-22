package com.okccc.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import redis.clients.jedis.Jedis;

/**
 * @Author: okccc
 * @Date: 2023/4/8 09:37:54
 * @Desc: 基于phoenix的维度查询工具类,根据userId/areaId/skuId等关联查询维度信息补充到事实表
 *
 * 计算机存储结构中,低一层的存储器都可以看成高一层的存储器的缓存,比如cache是内存的缓存,内存是硬盘的缓存,硬盘是网络的缓存
 * 缓存容量是固定的,应该只放经常访问的热数据,根据过去的访问时间进行排序删除最老的元素,LRUCache(Least Recently Used)是最常用的缓存策略
 *
 * 缓存选型：
 * 堆缓存(HashMap)：访问本地缓存路径更短性能更好,但是不便于维护,因为本地缓存的数据其它进程无法使用,LRUCache继承了LinkedHashMap
 * 独立缓存(Redis)：创建连接和网络IO会略微降低性能,但是便于维护,因为独立缓存的数据可以被多个进程复用(推荐)
 *
 * 旁路缓存：先查询redis缓存,命中直接返回,没有命中再去查询mysql/hbase数据库同时将结果写入缓存
 * 注意事项：1.缓存要设置过期时间,防止冷数据常驻缓存浪费资源 2.数据库update/delete数据时要及时清除失效缓存
 *
 * redis面试题：数据更新,先更新数据库还是先更新缓存？
 * a.先更新数据库,再删除缓存(常用)：缓存刚好失效,A查询缓存未命中去数据库查询旧值,B更新数据库并让缓存失效,A将旧数据写入缓存,导致脏数据
 * 读操作必须先于写操作进入数据库,又晚于写操作更新缓存才会导致该情况发生,然而实际上数据库的写操作比读操作慢很多还得锁表,所以发生概率极低
 * b.先删除缓存,再更新数据库：A删除缓存,B查询缓存未命中去数据库查询旧值并写入缓存,A更新数据库,导致脏数据
 * c.先更新数据库,再更新缓存：A更新数据为V1,B更新数据为V2,B更新缓存为V2,A更新缓存为V1,A先操作但是网络延迟B先更新了缓存,导致脏数据
 * d.先更新缓存,再更新数据库：如果缓存更新成功但数据库异常导致回滚,而缓存是无法回滚的,导致数据不一致(不考虑)
 */
public class DimUtil {

    /**
     * 直接查询phoenix
     */
    public static JSONObject getDimInfo(String tableName, String primaryKey) throws Exception {
        return PhoenixUtil.getValueById(tableName, primaryKey);
    }

    /**
     * redis旁路缓存优化,提高维度查询效率
     */
    public static JSONObject getDimInfoWithCache(String tableName, String primaryKey) {
        // 声明jedis客户端
        Jedis jedis = null;
        // 声明查询的缓存数据
        String value = null;
        // 声明查询结果
        JSONObject dimInfo = null;

        // 拼接redis的key=库名.表名:主键
        String redisKey = "DIM." + tableName + ":" + primaryKey;

        try {
            // 获取连接有可能失败,比如redis服务挂了,查询缓存失败就继续往下查数据库,所以要捕获异常而不是抛出异常终止程序,提高代码健壮性
            // try代码块尽量只包含可能出现异常的代码,这样一看就知道是哪行报错,提高代码可读性
            jedis = RedisUtil.getJedis();
            // key如果不存在不会报错而是返回null
            value = jedis.get(redisKey);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("redis查询失败：" + redisKey);
        }

        // 缓存命中
        if (value != null && value.length() > 0) {
            // 重置过期时间
            jedis.expire(redisKey, 24 * 3600);
            // 返回查询结果
            dimInfo = JSON.parseObject(value);
        } else {
            // 没有命中就继续查hbase
            try {
                dimInfo = PhoenixUtil.getValueById(tableName, primaryKey);
                if (dimInfo != null && jedis != null) {
                    // 将查询结果更新到缓存
                    jedis.setex(redisKey, 24 * 3600, dimInfo.toJSONString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 归还连接
        if (jedis != null) {
            jedis.close();
        }

        // 返回结果
        return dimInfo;
    }

    /**
     * 删除redis缓存
     */
    public static void deleteDimInfo(String tableName, String key) {
        try {
            // 获取连接有可能失败,比如redis挂了
            Jedis jedis = RedisUtil.getJedis();
            // 删除数据,redis删除不存在的数据也不会报错
            jedis.del("DIM." + tableName + ":" + key);
            jedis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        // 计算客户端查询维度数据耗时
        long start = System.currentTimeMillis();

        // 第一次查询hbase要先访问zk获取meta表所在位置并缓存到本地,然后再去查询具体数据,所以耗时略长
        System.out.println(getDimInfo("DIM_BASE_TRADEMARK", "10"));
        long end1 = System.currentTimeMillis();
        System.out.println(end1 - start);  // 350ms

        // 第二次查询hbase直接从本地缓存读取meta表,然后再去查询具体数据,这才是phoenix连接处于运行当中的真正查询速度
        // 单次查询8ms,每秒能查100多次,这个速度是不够的,假设高峰期QPS=2000就得开20个并行度,需要优化,数据量特别大的话就只能加机器了
        System.out.println(getDimInfo("DIM_BASE_TRADEMARK", "10"));
        long end2 = System.currentTimeMillis();
        System.out.println(end2 - end1);  // 8ms

        // 优化1：旁路缓存
        // 第一次查询redis还没数据,然后继续查询hbase,并且将查询结果放入redis,所以耗时略长
        System.out.println(getDimInfoWithCache("DIM_BASE_TRADEMARK", "10"));
        long end3 = System.currentTimeMillis();
        System.out.println(end3 - end2);  // 136ms

        // 第二次查询redis已经有缓存数据,所以速度非常快,这才是jedis连接处于运行当中的真正查询速度
        // 单次查询1ms,小公司足够了,面试会问你们这点数据用得着缓存吗？当然得考虑未来业务发展啊,并且压测时峰值数据会翻好几倍
        System.out.println(getDimInfoWithCache("DIM_BASE_TRADEMARK", "10"));
        long end4 = System.currentTimeMillis();
        System.out.println(end4 - end3);  // 1ms
    }
}
