package com.okccc.basic

import scala.collection.mutable

/**
 * Author: okccc
 * Date: 2021/4/28 上午9:56
 * Desc: 
 */
object S05_group {
  def main(args: Array[String]): Unit = {
    // 1.groupBy分组：结果是一个个元组(int/string, List)组成的Map集合
    val ints: List[Int] = List(1,2,3,4,3,2,1)
    // 按元素本身分组
    val intToList: Map[Int, List[Int]] = ints.groupBy((i: Int) => i)
    println("res=" + intToList)  // Map(2 -> List(2, 2), 4 -> List(4), 1 -> List(1, 1), 3 -> List(3, 3))
    // 按元素奇偶性分组
    val intToList1: Map[Int, List[Int]] = ints.groupBy((i: Int) => {i % 2})
    println("res1=" + intToList1)  // Map(1 -> List(1, 3), 0 -> List(2, 4))
    intToList1.foreach((t: (Int, List[Int])) => {t._1 +"="+ t._2})
    // 按字符串首字母分组
    val strings: List[String] = List("aa","ab","cc","cd")
    val stringToList: Map[String, List[String]] = strings.groupBy((i: String) => {i.substring(0,1)})
    println("res2=" + stringToList)  // Map(a -> List(aa, ab), c -> List(cc, cd))
    stringToList.foreach((t: (String, List[String])) => {println(t._1 +"="+ t._2)})

    // 2.sortBy排序：默认升序
    val ints_sort: List[Int] = ints.sortBy((i: Int) => i)
    println("res3=" + ints_sort)  // List(1, 1, 2, 2, 3, 3, 4)
    // sortWith可以指定排序方式 (x,y) => {x ? y}, take()可以取topK
    val ints_sort_desc: List[Int] = ints.sortWith((x: Int, y: Int) => {x > y}).take(3)
    println("res4=" + ints_sort_desc)  // List(4, 3, 3)
    val strings_sort_desc: List[String] = strings.sortWith((x: String, y: String) => {x.substring(1) > y.substring(1)})
    println("res5=" + strings_sort_desc)  // List(cd, cc, ab, aa)

    // 3.map映射：可以改变集合的数据结构
    val intToInt: Map[Int, Int] = intToList.map((t: (Int, List[Int])) => (t._1, t._2.size))
    println("res6=" + intToInt)  // Map(2 -> 2, 4 -> 1, 1 -> 2, 3 -> 2)
    // WordCount案例1(分组 - 映射 - 排序 - top)
    val words: List[String] = List("java","python","scala","java","python","scala","java", "com/okccc/spark" )
    // 先分组
    val wordToList: Map[String, List[String]] = words.groupBy((i: String) => i)
    println(wordToList)  // Map(com.okccc.spark -> List(com.okccc.spark), scala -> List(scala, scala), java -> List(java, java, java), python -> List(python, python))
    // 再映射
    val wordToInt: Map[String, Int] = wordToList.map((t: (String, List[String])) => {(t._1, t._2.size)})
    println(wordToInt)  // Map(com.okccc.spark -> 1, scala -> 2, java -> 3, python -> 2)
    // Map集合是无序的没有排序方法,需转换成List集合再排序
    val result: List[(String, Int)] = wordToInt.toList.sortWith((x: (String, Int), y: (String, Int)) => {x._2 > y._2}).take(3)
    println(result)  // List((java,3), (scala,2), (python,2))

    // 4.扁平化flatMap：如果集合中的元素是整体,要先做扁平化处理将其拆分为单个个体
    // scala读取文件
//    val source: BufferedSource = Source.fromFile("aaa.txt")
//    val words: List[String] = List("hello java","hello python","hello scala")
//    val words_new: List[String] = words.flatMap((i: String) => i.split(" "))
    // WordCount案例2
    val text: List[(String, Int)] = List(("hello scala", 4),("hello python",3 ),("hello java",2),("hello com.okccc.spark",1))
    println(text)  // List((hello scala,4), (hello python,3), (hello java,2), (hello com.okccc.spark,1))
    // 先做扁平化拆分
    val flatMapList: List[(String, Int)] = text.flatMap((t: (String, Int)) => {
      val words: mutable.ArrayOps[String] = t._1.split(" ")
      println(words)
      words.map((w: String) => {(w, t._2)})
    })
    println(flatMapList)  // List((hello,4), (scala,4), (hello,3), (python,3), (hello,2), (java,2), (hello,1), (com.okccc.spark,1))
    // 分组
    val groupWordMap: Map[String, List[(String, Int)]] = flatMapList.groupBy((t: (String, Int)) => {t._1})
    println(groupWordMap)  // Map(java -> List((java,2)), com.okccc.spark -> List((com.okccc.spark,1)), scala -> List((scala,4)), python -> List((python,3)), hello -> List((hello,4), (hello,3), (hello,2), (hello,1)))
    // 映射,mapValues方法可以只针对Map集合的value做操作而key保持不变
    val wordSumMap: Map[String, Int] = groupWordMap.mapValues((l: List[(String, Int)]) => {l.map((t: (String, Int)) => {t._2}).sum})
    println(wordSumMap)  // Map(java -> 2, com.okccc.spark -> 1, scala -> 4, python -> 3, hello -> 10)
    // 排序
    val wordCount: List[(String, Int)] = wordSumMap.toList.sortWith((x: (String, Int), y: (String, Int)) => {x._2 > y._2}).take(3)
    println(wordCount)  // List((hello,10), (scala,4), (python,3))

    // 扁平化案例
    val list_any: List[Any] = List(1,List(2,3),4,List(5,6))
    val list_any_new: List[Int] = list_any.flatMap((any: Any) => {
      any match {
        case any: Int => List(any)
        case any: List[Int] => any
      }
    })
    println(list_any_new)  // List(1, 2, 3, 4, 5, 6)

    // reduce化简
    val list: List[Int] = List(1,3,5,7)
    //    val i: Int = list.reduce((x: Int, y: Int) => {x-y})
    val i: Int = list.reduce((_: Int)-(_: Int))
    // reduceLeft等同于reduce ((1-3)-5)-7
    val i1: Int = list.reduceLeft((_: Int)-(_: Int))
    // reduceRight 1-(3-(5-7))
    val i2: Int = list.reduceRight((_: Int)-(_: Int))
    println(i,i1,i2)  // -14  -14  -4

    // fold折叠：函数柯里化的应用,参数一是初始值
    val arr: Array[(String, Int)] = Array(("scala",1), ("scala",2), ("scala",3))
    println(arr.foldLeft(0)((_: Int) + (_: (String, Int))._2))  // 6
    val i3: Int = list.fold(100)((_: Int)-(_: Int))
    // foldLeft等同于fold (((100-1)-3)-5)-7
    val i4: Int = list.foldLeft(100)((_: Int)-(_: Int))
    // foldRight 1-(3-(5-(7-100)))
    val i5: Int = list.foldRight(100)((_: Int)-(_: Int))
    println(i3,i4,i5)  // 84  84  96
    // scanLeft可以扫描集合运算过程中的中间值
    val ints1: List[Int] = list.scanLeft(100)((_: Int)-(_: Int))
    val ints2: List[Int] = list.scanRight(100)((_: Int)-(_: Int))
    println(ints1)  // List(100, 99, 96, 91, 84)
    println(ints2)  // List(96, -95, 98, -93, 100)

    // 需求：将两个Map集合合并,相同key累加,不同key新增
    val map1: Map[String, Int] = Map("a" -> 1, "b" -> 2, "c" -> 3)
    val map2: Map[String, Int] = Map("a" -> 3, "c" -> 2, "d" -> 1)
    // 如果直接++,后面map会把前面map相同键的值覆盖掉
    println(map1 ++ map2)  // Map(a -> 3, b -> 2, c -> 2, d -> 1)
    // 方法一：可以先处理第二个map的value值,然后再用第一个map与之++,这样覆盖的是已经累加完的值
    val map2_new: Map[String, Int] = map2.map((t: (String, Int)) => t._1 -> (t._2 + map1.getOrElse(t._1, 0)))
    println(map2_new)  // Map(a -> 4, c -> 5, d -> 1)
    val res: Map[String, Int] = map1 ++ map2_new
    println(res)  // Map(a -> 4, b -> 2, c -> 5, d -> 1)
    // 方法二：折叠操作会将初始值map2与依次传入的map1的KV对做运算,但是初始值必须是mutable类型
    // 先将immutable.Map转换成mutable.Map
    val map22: mutable.Map[String, Int] = mutable.Map(map2.toList:_*)
    val map_new: mutable.Map[String, Int] = map1.foldLeft(map22)((map22: mutable.Map[String, Int], t: (String, Int)) => {
      // t是map1的KV对
      map22(t._1) = map22.getOrElse(t._1, 0) + t._2
      map22
    })
    println(map_new)  // Map(b -> 2, d -> 1, a -> 4, c -> 5)
  }

}
