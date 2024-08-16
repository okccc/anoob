package com.okccc.func;

import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * @Author: okccc
 * @Date: 2024/5/7 18:40:23
 * @Desc: 自定义hive桶分配器,继承默认的基于时间的分配器DateTimeBucketAssigner
 */
public class HiveBucketAssigner<IN> extends DateTimeBucketAssigner<IN> {

//    public HiveBucketAssigner(String formatString, ZoneId zoneId) {
//        super(formatString, zoneId);
//    }
//
//    @Override
//    public String getBucketId(IN element, Context context) {
//        // The bucketing logic defines how the data will be structured into subdirectories inside the base output directory.
//        // hdfs://cdh01:8020/data/hive/warehouse/ods.db/ods_xxx_realtime/dt=20240505
//        // flink分桶将文件放入不同文件夹,桶号是个时间值,对应hive表的分区,由于hive分区通常以dt=开头,所以要稍微修改下
//        // 点进去查看源码发现默认是以currentProcessingTime作为dt字段,可能存在零点漂移问题
//        // 应该以事件时间处理数据,先在source端.withTimestampAssigner()指定某个long类型的时间戳字段作为dt
//        String bucketId = super.getBucketId(element, context);
//        return "dt=" + bucketId;
//    }

    public String formatString;
    public ZoneId zoneId;
    private DateTimeFormatter dateTimeFormatter;

    public HiveBucketAssigner(String formatString, ZoneId zoneId) {
        this.formatString = formatString;
        this.zoneId = zoneId;
    }

    @Override
    public String getBucketId(IN element, Context context) {
        if (dateTimeFormatter == null) {
            dateTimeFormatter = DateTimeFormatter.ofPattern(formatString).withZone(zoneId);
        }
        return "dt=" + dateTimeFormatter.format(Instant.ofEpochMilli(context.timestamp()));
    }
}
