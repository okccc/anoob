package com.okccc.util

import com.okccc.realtime.common.Configs
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{ContentSummary, FileStatus, FileSystem, Path}

import scala.collection.mutable.ArrayBuffer

/**
 * Author: okccc
 * Date: 2021/7/20 下午2:02
 * Desc: 监控hdfs的工具类
 */
object HdfsUtils {

  def monitor(): Unit = {
    // hdfs配置信息
    val conf: Configuration = new Configuration
    conf.set("fs.defaultFS", Configs.get(Configs.HDFS_URL))
    // 创建文件系统
    val fs: FileSystem = FileSystem.get(conf)
    // 文件路径
    val path: Path = new Path("/data/hive/warehouse/ods.db")
    // 获取该路径下文件和子路径
    val fileStatuses: Array[FileStatus] = fs.listStatus(path)
    // scala的ArrayBuffer相当于java的ArrayList
    val paths: ArrayBuffer[Path] = new ArrayBuffer[Path]()
    for (fileStatus <- fileStatuses) {
      System.out.println(fileStatus.getPath)
      paths.append(fileStatus.getPath)
    }
    // 获取路径概要
    for (i <- paths) {
      val summary: ContentSummary = fs.getContentSummary(i)
      // 获取路径大小,可以用来监控离线或实时任务是否跑成功,没数据就触发监控告警
      System.out.println(i + " : " + summary.getLength / 1024 / 1024 + "M")
    }
  }

  def main(args: Array[String]): Unit = {
//    val job: String = args(0)
    val job: String = "realtime"
    // 模式匹配: scala的match相当于java的switch
    job match {
      case "offline" => println("offline")
      case "realtime" => monitor()
    }
  }

}
