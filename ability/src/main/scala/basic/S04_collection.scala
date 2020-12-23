package basic

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}


object S04_collection {

  def main(args: Array[String]): Unit = {
    /**
     * scala集合类scala.collection{immutable, mutable}
     * Scala默认不可变集合Array/List/Map,可变集合需要显示指定mutable.{ArrayBuffer, ListBuffer, Map}
     */

//    testArray()
//    testList()
    testMap()
//    testOther()
  }

  def testArray(): Unit = {
    // Array(不可变)：是Scala的一种特殊集合,对应Java数组并且支持泛型,Scala数组和序列兼容(可以在要求Seq[T]的地方传入Array[T])
    val arr: Array[Int] = Array(1,2,3,4)
    println(arr, arr.toSeq, arr.toList)  // [I@ea4a92b, WrappedArray(1, 2, 3, 4), List(1, 2, 3, 4)
    // Array可以隐式转换成WrappedArray
    val seq : Seq[Int] = arr          // implicit
    val seq1: Seq[Int] = arr.toSeq    // explicit
    println(seq == seq1)  // true
    println(arr.eq(seq.toArray) && arr.eq(seq1.toArray))  // true
    // 索引,长度,转换为字符串
    println(arr(0), arr.length, arr.mkString(","))
    // 遍历
    arr.foreach(println)
    // 添加元素,返回新数组
    val arr1: Array[Int] = arr:+5  // 尾部添加
    val arr2: Array[Int] = 5+:arr  // 头部添加
    println(arr sameElements arr1)  // false
    println(arr1.mkString(","))  // 1,2,3,4,5
    println(arr2.mkString(","))  // 5,1,2,3,4
    // 修改,update操作说明Array其实是可变的
    arr.update(0, 5)  // 或者array(0) = 5
    println(arr.mkString(","))  // 5,2,3,4
    // 删除
    val array3: Array[Int] = arr.drop(10)
    println(array3.length)  // 空数组

    // ArrayBuffer(可变)
    val ab: ArrayBuffer[Int] = ArrayBuffer(1,2,3,4)
    println(ab)  // ArrayBuffer(1, 2, 3, 4)
    // 添加元素,返回buffer本身
    ab+=8  // 尾部添加
    ab.insert(0, 5)  // 指定位置插入
    println(ab)  // ArrayBuffer(5, 1, 2, 3, 4, 8)
    // 移除
    ab.remove(0)  // 移除索引为i的元素并返回该元素
    ab.remove(1, 2)  // 移除从索引i开始的n个元素
    ab.clear()  // 移除所有元素
    // 克隆
    ab.clone()  // 和buffer拥有相同元素的新缓冲
    // 转换为Array
    println(ab.toArray)  // [I@ea1a8d5
  }

  def testList(): Unit = {
    // List(不可变)
    val list: List[Int] = List(1,2,3,4)
    println(list)  // List(1, 2, 3, 4)
    // 遍历
    list.foreach(println)
    // 索引,长度,转换成字符串
    println(list.head, list.length, list.mkString(","))
    // 添加
    val list1: List[Int] = list:+5  // 尾部添加
    val list2: List[Int] = 5+:list  // 头部添加
    // ++可以合并两个list
    val list3: List[Int] = list++list1
    println(list3)  // List(1, 2, 3, 4, 1, 2, 3, 4, 5)
    // ::从右往左运算
    val list4: List[Int] = 7::8::9::list
    println(list4)  // List(7, 8, 9, 1, 2, 3, 4)
    val list5: List[Any] = 9::list1::list
    println(list5)  // List(9, List(1, 2, 3, 4, 5), 1, 2, 3, 4)
    // :::可以往list里添加list
    val list6: List[Int] = 9::list1:::list
    println(list6)  // List(9, 1, 2, 3, 4, 5, 1, 2, 3, 4)
    // Nil表示空集合,等同于List()
    println(Nil)  // List()
    // 修改
    val list7: List[Int] = list.updated(0,9)
    println(list7)  // List(9, 2, 3, 4)
    // 删除
    val list8: List[Int] = list.drop(10)
    println(list8)  // List()
    // 过滤
    val ints: List[Int] = list.filter((i: Int) => {i%2==0})
    println(ints)  // List(2, 4)
    val l1: List[Int] = List(1,2,3)
    val l2: List[Int] = List(3,4,5)
    // 拉链
    println(l1.zip(l2).mkString(","))  // (1,3),(2,4),(3,5)
    // 交集,并集(不去重),差集
    println(l1.intersect(l2), l1.union(l2), l1.diff(l2))  // List(3), List(1,2,3,3,4,5), List(1,2)
    // 求和,乘积,最值,反转
    println(l2.sum, l2.product, l2.max, l2.reverse)  // 12,60,5

    // ListBuffer(可变)
    val lb: ListBuffer[Int] = ListBuffer(1,2,3,4)
    lb.append(5)
    println(lb)  // ListBuffer(1, 2, 3, 4, 5)
    // 头部
    println(lb.head)  // 1
    // 去掉头部,tail.tail可以递归
    println(lb.tail)  // ListBuffer(2, 3, 4, 5)
    println(lb.tail.tail)  // ListBuffer(3, 4, 5)
    // 尾部
    println(lb.last)  // 5
    // 去掉尾部
    println(lb.init)  // ListBuffer(1, 2, 3, 4)
    // 转换为List
    println(lb.toList)  // List(1, 2, 3, 4, 5)
  }

  def testMap(): Unit = {
    // Map(不可变)：Map集合存储的元素是tuple()
    val map: Map[String, Int] = Map(("a", 1), ("b", 2), ("c", 3))
    val mapp: Map[String, Int] = Map("a" -> 1, "b" -> 2, "c" -> 3)
    println(map.equals(mapp))  // true
    println("map = " + map)  // map = Map(a -> 1, b -> 2, c -> 3)
    // 遍历
    map.foreach((t: (String, Int)) => println(t._1 + "=" + t._2))
    // 取值
    val v1: Option[Int] = map.get("a")
    val v2: Option[Int] = map.get("d")
    val v3: Int = map.getOrElse("d", 0)
    println(v1, v2, v3)  // Some(1), None, 0
    // 添加
    val map1: Map[String, Int] = map+("d"->4)
    println("map1 = " + map1)  // map1 = Map(a -> 1, b -> 2, c -> 3, d -> 4)
    // ++可以合并两个map,键相同值覆盖
    println(map++map1)  // Map(a -> 1, b -> 2, c -> 3, d -> 4)
    // 修改
    val map2: Map[String, Int] = map.updated("a", 5)
    println("map2 = " + map2)  // map2 = Map(a -> 5, b -> 2, c -> 3)
    // 删除
    val map3: Map[String, Int] = map-"b"
    println("map3 = " + map3)  // Map(a -> 1, c -> 3)

    // mutable.Map(可变)
    val map4: mutable.Map[String, Int] = mutable.Map("a" -> 1, "b" -> 2)
    // 转换为Map
    val map5: Map[String, Int] = map4.toMap
    println(map4 == map5)  // true
  }

  def testOther(): Unit = {
    // set集合
    val s1: Set[Int] = Set(1, 2, 3, 3, 4)
    val s2: mutable.Set[Int] = mutable.Set(1, 2, 3, 3, 4)
    println(s1, s2)  // Set(1, 2, 3, 4) Set(1, 2, 3, 4)
    val s11: Set[Int] = s1 + 5
    val s21: mutable.Set[Int] = s2 += 5
    println(s11, s21)  // Set(5, 1, 2, 3, 4) Set(1, 5, 2, 3, 4)

    // 元组(不可变)可以容纳不同类型元素
    val tuple: (String, Int, String) = ("grubby", 18, "orc")
    println(tuple)  // (grubby,18,orc)
    println(tuple._1 +" "+ tuple._2 +" "+ tuple._3)  // grubby 18 orc

    // 队列(一定可变)
    val queue: mutable.Queue[Int] = mutable.Queue(1,2,3)
    println(queue)  // Queue(1, 2, 3)
    // 进队列
    queue.enqueue(4)
    println(queue)  // Queue(1,2,3,4)
    // 出队列(先进先出)
    val q:Int = queue.dequeue()
    println(q)  // 1
    println(queue)  // Queue(2,3,4)
  }

}
