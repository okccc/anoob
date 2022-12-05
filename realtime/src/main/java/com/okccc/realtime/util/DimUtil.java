package com.okccc.realtime.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.java.tuple.Tuple2;
import redis.clients.jedis.Jedis;

import java.util.List;

/**
 * Author: okccc
 * Date: 2021/11/2 上午11:00
 * Desc: 基于phoenix读写hbase维度数据的工具类
 */
public class DimUtil {

    /**
     * 将phoenix查询数据方法继续封装,通常不会直接传入sql字符串,参数1是表名,参数2是查询条件即列和值,因为条件个数未知所以选用不定长参数的Tuple2
     */
    @SafeVarargs
    public static JSONObject getDimInfo(String tableName, Tuple2<String, String>... columnAndValues) {
        // 拼接查询sql
        StringBuilder dimSql = new StringBuilder("select * from " + tableName + " where ");
        // 遍历可变参数
        for (int i = 0; i < columnAndValues.length; i++) {
            Tuple2<String, String> columnAndValue = columnAndValues[i];
            String column = columnAndValue.f0;
            String value = columnAndValue.f1;
            dimSql.append(column + "='" + value + "'");
            if (i < columnAndValues.length - 1) {
                dimSql.append(" and ");
            }
        }
//        System.out.println("拼接的查询sql是 " + sb.toString());

        // 从phoenix表查数据
        List<JSONObject> jsonObjects = PhoenixUtil.queryList(dimSql.toString(), JSONObject.class);
        JSONObject jsonObject = null;
        if (jsonObjects.size() > 0) {
            // 因为是根据主键id查询,所以结果集只会有一条数据
            jsonObject = jsonObjects.get(0);
        } else {
            System.out.println("维度数据没找到：" + dimSql);
        }
        return jsonObject;
    }

    /**
     * redis旁路缓存优化,提升维度查询效率
     */
    @SafeVarargs
    public static JSONObject getDimInfoWithCache(String tableName, Tuple2<String, String>... columnAndValues) {
        // 拼接查询sql
        StringBuilder dimSql = new StringBuilder("select * from " + tableName + " where ");
        // 拼接redis的key=表名:主键1_主键2
        StringBuilder dimKey = new StringBuilder(tableName + ":");
        // 遍历可变参数
        for (int i = 0; i < columnAndValues.length; i++) {
            Tuple2<String, String> columnAndValue = columnAndValues[i];
            String column = columnAndValue.f0;
            String value = columnAndValue.f1;
            dimSql.append(column + "='" + value + "'");
            dimKey.append(value);
            if (i < columnAndValues.length - 1) {
                dimSql.append(" and ");
                dimKey.append("_");
            }
        }
//        System.out.println("dimSql = " + dimSql.toString());  // dimSql = select * from dim_base_trademark where id='12'
//        System.out.println("dimKey = " + dimKey.toString());  // dimKey = dim_base_trademark:12

        // 声明redis客户端
        Jedis jedis = null;
        // 声明redis查询的缓存数据
        String value = null;
        // 声明最终返回的查询结果
        JSONObject jsonObject = null;

        // 先去redis查缓存
        try {
            jedis = RedisUtil.getJedis();
            value = jedis.get(dimKey.toString());
        } catch (Exception e) {
            // 注意：这里异常要捕获而不是抛出,因为缓存没有命中的话就继续往下查询数据库而不是抛出异常终止程序
            e.printStackTrace();
            System.out.println("---redis查询异常---");
        }

        // 判断缓存是否命中
        if (value != null && value.length() > 0) {
            jsonObject = JSON.parseObject(value);
        } else {
            // 没有命中就再去查phoenix
            List<JSONObject> jsonObjects = PhoenixUtil.queryList(dimSql.toString(), JSONObject.class);
            if (jsonObjects.size() > 0) {
                // 因为是根据主键id查询,所以结果集只会有一条数据
                jsonObject = jsonObjects.get(0);
                // 将查询结果放到缓存,设置缓存有效期24小时,防止冷数据常驻缓存浪费资源
                if (jedis != null) {
                    jedis.setex(dimKey.toString(), 24 * 3600, jsonObject.toJSONString());
                }
            } else {
                System.out.println("维度数据没找到：" + dimSql);
            }
        }

        // 用完及时释放连接,不然连接池很快就消耗完
        if (jedis != null) {
            jedis.close();
        }

        // 返回最终查询结果
        return jsonObject;
    }

    /**
     * 默认按照主键id查询,即只有一个查询条件
     */
    public static JSONObject getDimInfoWithCache(String tableName, String id) {
        // 将id封装成Tuple2
        return getDimInfoWithCache(tableName, Tuple2.of("id", id));
    }

    /**
     * 删除redis缓存数据
     */
    public static void deleteCache(String tableName, String id) {
        String key = tableName.toLowerCase() + ":" + id;
        try {
            Jedis jedis = RedisUtil.getJedis();
            jedis.del(key);
            jedis.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("---删除redis缓存异常---");
        }
    }

    /**
     * 将phoenix更新数据方法继续封装,通常不会直接传入sql字符串,参数1是表名,参数2和参数3是更新的字段和值
     */
    public static void updateDimInfo(String tableName, JSONObject data) {
        // 获取插入表的字段和对应值
        String fields = StringUtils.join(data.keySet(), ",");
        String values = StringUtils.join(data.values(), "','");  // value之间的逗号要加引号,不然传入的是一整个字符串
        // 拼接sql
        String dimSql = "upsert into " + tableName + " (" + fields + ") " + "values('" + values + "')";
//        System.out.println("dimSql =  " + sql);  // dimSql = upsert into base_trademark (id,tm_name) values("10","小米")
        // 更新插入数据
        PhoenixUtil.upsert(dimSql);
    }

    public static void main(String[] args) {
//        System.out.println(getDimInfo("dim_base_trademark", Tuple2.of("id", "12")));
//        System.out.println(getDimInfoWithCache("dim_base_trademark", Tuple2.of("id", "12")));
        System.out.println(getDimInfoWithCache("dim_base_trademark", "12"));  // {"ID":"12","TM_NAME":"联想"}
    }
}
