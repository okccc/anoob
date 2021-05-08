package com.okccc.spark.session

case class CategorySortKey(clickCount: Long, orderCount: Long, payCount: Long) extends Ordered[CategorySortKey] {

  // 实现Ordered特质的compare方法
  override def compare(that: CategorySortKey): Int = {
    if (this.clickCount - that.clickCount != 0) {
      return (this.clickCount - that.clickCount).toInt
    } else if (this.orderCount - that.orderCount != 0) {
      return (this.orderCount - that.orderCount).toInt
    } else if (this.payCount - that.payCount != 0) {
      return (this.payCount - that.payCount).toInt
    }
    0
  }
}
