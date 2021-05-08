package com.okccc.spark.session

import org.apache.spark.util.AccumulatorV2

import scala.collection.mutable

/**
  * 自定义累加器
  */
class SessionAggAccumulator extends AccumulatorV2[String, mutable.HashMap[String, Int]] {

  // 创建保存聚合数据的集合
  private val hm: mutable.HashMap[String, Int] = mutable.HashMap[String, Int]()

  // 判断当前累加器是否为初始状态
  override def isZero: Boolean = hm.isEmpty

  // 复制累加器对象
  override def copy(): AccumulatorV2[String, mutable.HashMap[String, Int]] = {
    val newAcc: SessionAggAccumulator = new SessionAggAccumulator
    hm.synchronized {
      newAcc.hm ++= this.hm
    }
    newAcc
  }

  // 重置累加器对象
  override def reset(): Unit = hm.clear()

  // 往累加器添加数据
  override def add(v: String): Unit = {
    if (!hm.contains(v)) {
      hm += (v -> 0)
    }
    hm.update(v, hm(v) + 1)
  }

  // 合并累加器
  override def merge(other: AccumulatorV2[String, mutable.HashMap[String, Int]]): Unit = {
    other match {
      case acc: SessionAggAccumulator =>
        (hm /: acc.value){
          case (map, (k,v)) => map += (k -> (v + map.getOrElse(k, 0)))
        }
    }
  }

  // 获取累加器结果
  override def value: mutable.HashMap[String, Int] = hm
}
