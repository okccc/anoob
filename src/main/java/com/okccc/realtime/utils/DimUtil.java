package com.okccc.realtime.utils;

import com.alibaba.fastjson.JSONObject;
import org.apache.flink.api.java.tuple.Tuple2;

import java.util.List;

/**
 * Author: okccc
 * Date: 2021/11/2 上午11:00
 * Desc: 基于phoenix查询维度数据的工具类
 */
public class DimUtil {

    // 从phoenix表查询维度数据,参数1是表名,参数2是查询条件即列和值,因为条件个数未知所以选用不定长参数的Tuple2
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

    public static void main(String[] args) {
        JSONObject dimInfo = getDimInfo("dim_base_trademark", Tuple2.of("id", "12"));
        System.out.println(dimInfo);  // {"ID":"12","TM_NAME":"联想"}
    }

}
