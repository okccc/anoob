package com.okccc.func;

import com.alibaba.fastjson.JSONObject;
import com.okccc.util.DimUtil;
import com.okccc.util.PhoenixUtil;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

/**
 * @Author: okccc
 * @Date: 2023/3/9 16:29:42
 * @Desc: 自定义SinkFunction将kafka中的维度数据写入phoenix
 */
public class DimSinkFunction implements SinkFunction<JSONObject> {

    @Override
    public void invoke(JSONObject value, Context context) throws Exception {
        /**
         * {
         *     "database":"mock",
         *     "data":{
         *         "id":11,
         *         "name":"欧阳翠花",
         *         "create_time":"2020-11-23 20:03:49",
         *     },
         *     "type":"bootstrap-insert",
         *     "table":"user_info",
         *     "sink_table":"dim_user_info",
         * }
         */

        // 获取hbase表名和数据
        String sinkTable = value.getString("sink_table");
        JSONObject data = value.getJSONObject("data");

        // 往phoenix写数据
        PhoenixUtil.upsertValues(sinkTable, data);

        // 判断操作类型
        String type = value.getString("type").toLowerCase();
        if ("update".equals(type) || "delete".equals(type)) {
            // 如果是更新操作,此时redis的缓存数据已失效,及时清除
            DimUtil.deleteDimInfo(sinkTable, data.getString("id"));
        }
    }
}
