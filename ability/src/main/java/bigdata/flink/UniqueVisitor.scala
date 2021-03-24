package bigdata.flink

/**
 * Author: okccc
 * Date: 2021/3/18 4:17 下午
 * Desc: 统计1小时内的独立访客数(UV=UniqueVisitor)
 */

// 定义输入数据样例类(同一个package下面类名不能重复,只需定义一次即可)
//case class UserBehavior(userId: Long, itemId: Long, categoryId: Int, behavior: String, timestamp: Long)
// 定义输出结果样例类
case class UVCount(windowEnd: Long, count:Int)

object UniqueVisitor {
  def main(args: Array[String]): Unit = {
    /**
     * 一亿的uid存储空间大小：10^8 * 10byte ≈ 1g  使用set集合存储是极其消耗内存的,redis也消耗不起这么多数据
     * 可以考虑使用特殊的数据结构布隆过滤器：10^8 * 1bit ≈ 10m  考虑hash碰撞可以给大一点空间比如50m,随便放内存还是redis都很轻松
     */

    // 1.创建流处理执行环境

    // 2.读取kafka数据

    // 3.转换处理

    // 4.启动任务
  }

}
