package basic

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer}


object S04_collection {
  def main(args: Array[String]): Unit = {
    // 1.数组,实际上是可变的,存放相同类型元素
    val array: Array[Int] = Array(1,2,3,4)
    println(array)  // [I@ea4a92b
    // 索引,长度,将数组转换为字符串打印
    println(array(0), array.length, array.mkString(","))
    // 遍历
    for (elem <- array) {println(elem)}
    array.foreach(println)
    // 添加元素,返回新数组
    val array1: Array[Int] = array:+5  // 尾部添加
    val array2: Array[Int] = 5+:array  // 头部添加
    println(array sameElements array1)  // false
    println(array1.mkString(","))  // 1,2,3,4,5
    println(array2.mkString(","))  // 5,1,2,3,4
    // 修改
    array.update(0, 5)  // 或者array(0) = 5
    println(array.mkString(","))  // 5,2,3,4
    // 删除
    val array3: Array[Int] = array.drop(10)
    println(array3.length)  // 空数组

    // 可变数组
    val arrayBuffer: ArrayBuffer[Int] = ArrayBuffer(1,2,3,4)
    println(arrayBuffer)  // ArrayBuffer(1, 2, 3, 4)
    // 添加元素,改变原数组
    arrayBuffer+=8  // 尾部添加
    arrayBuffer.insert(0, 5)  // 指定位置插入
    println(arrayBuffer)  // ArrayBuffer(5, 1, 2, 3, 4, 8)
    // 指定索引删除元素,返回被删除元素
    val i:Int = arrayBuffer.remove(0)
    println(i)  // 5
    // 指定索引删除指定长度的元素
    arrayBuffer.remove(1,2)
    println(arrayBuffer)  // ArrayBuffer(1, 4, 8)

    // 可变数组和不可变数组转换
    val array2buffer: mutable.Buffer[Int] = array.toBuffer
    val buffer2array: Array[Int] = arrayBuffer.toArray
    println(array2buffer)  // ArrayBuffer(5, 2, 3, 4)
    println(buffer2array)  // [I@33f88ab

    // 2.list,不可变,存放相同类型元素
    val list: List[Int] = List(1,2,3,4)
    println(list)  // List(1, 2, 3, 4)
    // 索引,长度,转换成字符串
    println(list(1), list.length, list.mkString(","))
    // 遍历
    for (elem <- list) {println(elem)}
    list.foreach(println)
    // 添加元素
    val list1: List[Int] = list:+5  // 尾部添加
    val list2: List[Int] = 5+:list  // 头部添加
    println(list == list1)  // false
    // ++可以合并两个list
    val list3: List[Int] = list ++ list1
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

    // 可变list集合
    val buffer: ListBuffer[Int] = ListBuffer(1,2,3,4)
    println(buffer)  // ListBuffer(1, 2, 3, 4)
    // 头部
    println(buffer.head)  // 1
    // 去掉头部,tail.tail可以递归
    println(buffer.tail)  // ListBuffer(2, 3, 4)
    println(buffer.tail.tail)  // ListBuffer(3, 4)
    // 尾部
    println(buffer.last)  // 4
    // 去掉尾部
    println(buffer.init)  // ListBuffer(1, 2, 3)
    
    // 3.set集合
    val set: Set[Int] = Set(1,2,3,4,4,5,6,7,8)
    println(set)  // Set(5, 1, 6, 2, 7, 3, 8, 4)
    val set1: Set[Int] = set+9
    println(set1)  // Set(5, 1, 6, 9, 2, 7, 3, 8, 4)
    println(set == set1)  // false
    val set2: Set[Int] = set-2
    println(set2)  // Set(5, 1, 6, 7, 3, 8, 4)

    // 4.map集合
    val map: Map[String, Int] = Map("a" -> 1, "b" -> 2, "c" -> 3)
    println(map)  // Map(a -> 1, b -> 2, c -> 3)
    // 取值
    println(map("a"))  // 1
    // 如果空指针就取默认值
    println(map.getOrElse("f", 0))  // 0
    // 添加元素
    val map1: Map[String, Int] = map+("d"->4)
    println(map1)  // Map(a -> 1, b -> 2, c -> 3, d -> 4)
    // ++可以合并两个map,键相同值覆盖
    println(map ++ map1)
    // 删除元素
    val map2: Map[String, Int] = map-"b"
    println(map2)  // Map(a -> 1, c -> 3)
    // 修改元素
    val map3: Map[String, Int] = map.updated("a", 5)
    println(map3)  // Map(a -> 5, b -> 2, c -> 3)
    // 遍历,输出kv对
    map.foreach((t: (String, Int)) => println(t._1 +"="+ t._2))

    // 5.元组,不可变,可以容纳不同类型元素
    val tuple: (String, Int, String) = ("grubby", 18, "orc")
    println(tuple)  // (grubby,18,orc)
    println(tuple._1 +" "+ tuple._2 +" "+ tuple._3)  // grubby 18 orc

    // 6.队列(一定可变)
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
