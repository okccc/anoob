package com.okccc.realtime.utils;

import com.okccc.realtime.common.MyConfig;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Author: okccc
 * Date: 2021/10/27 上午11:33
 * Desc: hdfs工具类
 */
public class HdfsUtil {

    // 监控hdfs文件大小
    public static void monitor() throws IOException {
        // hdfs配置信息
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", MyConfig.HDFS_URL);
        // 获取文件系统
        FileSystem fs = FileSystem.get(conf);
        // 指定目标路径
        Path path = new Path("/data/hive/warehouse/ods.db");
        // 获取该路径下所有文件和子路径
        ArrayList<Path> paths = new ArrayList<>();
        FileStatus[] fileStatuses = fs.listStatus(path);
        for (FileStatus fileStatus : fileStatuses) {
            System.out.println(fileStatus.getPath());
            paths.add(fileStatus.getPath());
        }
        // 获取路径概要
        for (Path p : paths) {
            ContentSummary contentSummary = fs.getContentSummary(p);
            // 获取路径大小(可以用来监控离线或实时任务是否跑成功,没数据就触发监控告警)
            System.out.println(p + " : " + contentSummary.getLength() / 1024 / 1024 + "M");
        }
    }

    public static void main(String[] args) throws IOException {
        String job = args[0];
        switch (job) {
            case "offline":
                System.out.println("offline");
            case "realtime":
                monitor();
        }
    }

}
