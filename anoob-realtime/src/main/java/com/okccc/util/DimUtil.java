package com.okccc.util;

import redis.clients.jedis.Jedis;

/**
 * @Author: okccc
 * @Date: 2023/4/8 09:37:54
 * @Desc:
 */
public class DimUtil {

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
}
