package com.okccc.realtime.app.func;

import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.util.DimUtil;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

/**
 * @Author: okccc
 * @Date: 2021/10/24 下午1:33
 * @Desc: 通过phoenix将canal/maxwell抓取的维度数据写入hbase
 */
public class DimSinkFunction extends RichSinkFunction<JSONObject> {

    @Override
    public void invoke(JSONObject value, Context context) throws Exception {
        /* {
         *     "data": [
         *         {
         *             "tm_name": "联想",
         *             "id": "13"
         *         }
         *     ],
         *     "type": "INSERT",
         *     "database": "maxwell",
         *     "sink_table": "dim_base_trademark",
         *     "table": "base_trademark",
         *     "ts": 1635131300181
         * }
         */

        // 获取hbase表名
        String tableName = value.getString("sink_table");
        // 获取插入表的字段和对应值
        JSONObject data = value.getJSONArray("data").getJSONObject(0);
        // 根据表名和data更新数据
        DimUtil.updateDimInfo(tableName, data);

        // 如果维度数据执行的是update/delete操作
        String type = value.getString("type").toLowerCase();
        if (type.equals("update") || type.equals("delete")) {
            // 此时redis中的缓存数据已经失效,要及时清除
            DimUtil.deleteCache(tableName, data.getString("id"));
        }
    }
}
