package com.okccc.realtime.util;

import com.okccc.realtime.common.MyConfig;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

/**
 * @Author: okccc
 * @Date: 2021/10/27 上午11:33
 * @Desc: hdfs工具类
 */
public class HdfsUtil {

    private static FileSystem fs;
    static {
        // hdfs配置信息
        Configuration conf = new Configuration();
        // 本地调试ok打jar包后报错：java.io.IOException: No FileSystem for scheme: hdfs,说明maven-assembly打包时发生了些变化
        // https://www.cnblogs.com/justinzhang/p/4983673.html
        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
        try {
            // 获取文件系统
            fs = FileSystem.get(new URI(MyConfig.HDFS_URL), conf, "hdfs");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 监控目录大小
     */
    public static void testDirectory() throws Exception {
        // 获取指定路径下的所有文件和目录
        FileStatus[] fileStatuses = fs.listStatus(new Path("/data/hive/warehouse/ods.db"));
        for (FileStatus fileStatus : fileStatuses) {
            // 获取路径大小(可以用来监控离线或实时任务是否跑成功,没数据就触发监控告警)
            Path path = fileStatus.getPath();
            ContentSummary contentSummary = fs.getContentSummary(path);
            System.out.println(path + " : " + contentSummary.getLength() / 1024 / 1024 + "M");
        }
    }

    /**
     * 查看文件详情
     */
    public static void testFile() throws Exception {
        // 递归获取指定目录下所有文件
        RemoteIterator<LocatedFileStatus> iterator = fs.listFiles(new Path("/"), false);
        // 遍历迭代器
        while(iterator.hasNext()){
            LocatedFileStatus status = iterator.next();
            // 获取当前文件详细信息
            System.out.println(DateUtil.parseUnixToDateTime(status.getAccessTime()));  // 2022-01-21 18:56:23
            System.out.println(DateUtil.parseUnixToDateTime(status.getModificationTime()));  // 2022-01-21 18:56:25
            System.out.println(status.getBlockSize()/1024/1024 + "M");  // 128M
            System.out.println(status.getReplication());  // 2
            System.out.println(status.getPath());  // hdfs://dev-bigdata-cdh1:8020/b.txt
            System.out.println(status.getPath().getName());  // b.txt
            System.out.println(status.getLen());  // 23143
            System.out.println(status.getPermission());  // rw-r--r--
            System.out.println(status.getOwner());  // hdfs
            System.out.println(status.getGroup());  // supergroup
            // 获取存储的块信息
            BlockLocation[] blockLocations = status.getBlockLocations();
            for (BlockLocation blockLocation : blockLocations) {
                System.out.println(Arrays.toString(blockLocation.getHosts()));  // [dev-bigdata-cdh3, dev-bigdata-cdh5]
                System.out.println(Arrays.toString(blockLocation.getNames()));  // [10.18.3.22:50010, 10.18.3.24:50010]
                System.out.println(blockLocation.getLength());  // 23143
            }
        }
    }

    /**
     * 上传下载
     */
    public static void testUpload() throws Exception {
        // 上传
//        fs.copyFromLocalFile(new Path("a.txt"), new Path("/a.txt"));
        // 下载
//        fs.copyToLocalFile(false, new Path("/a.txt"), new Path("a.txt"), true);
        // 删除目录
//        fs.delete(new Path("/aaa"), true);
        // 文件重命名
//        fs.rename(new Path("/a.txt"), new Path("/b.txt"));
    }

    /**
     * 往hdfs(hive表)写数据,覆盖
     */
    public static void overwriteToHdfs(String filePath, List<String> lines) throws IOException {
        // 先判断当前分区是否有数据
        String pathString = new File(filePath).getParent();
        Path path = new Path(pathString);
        // 有就删除
        if (fs.exists(path)) {
            fs.delete(path, true);
        }

        // 创建新文件并写入数据
        FSDataOutputStream os = fs.create(new Path(filePath));
        for (String line : lines) {
            // 对应hive表中一行数据
            os.write(line.getBytes());
            // 写入换行符
            os.write("\r\n".getBytes());
        }
    }

    /**
     * 往hdfs(hive表)写数据,追加
     */
    public static void appendToHdfs(String filePath, List<String> lines) throws IOException {
        // 创建新文件并写入数据
        FSDataOutputStream os = fs.create(new Path(filePath));
        for (String line : lines) {
            // 对应hive表中一行数据
            os.write(line.getBytes());
            // 写入换行符
            os.write("\r\n".getBytes());
        }
    }

    public static void main(String[] args) throws Exception {
//        String job = args[0];
//        switch (job) {
//            case "offline":
//                System.out.println("plan a");
//            case "realtime":
//                System.out.println("plan b");
//        }

//        testFile();
        testDirectory();
//        testUpload();
//        overwriteToHdfs("a.txt", Collections.singletonList("hello world"));
    }
}
