package com.okccc.basic

/**
 * Author: okccc
 * Date: 2021/4/28 上午9:56
 * Desc: 
 */
object S06_match {
  def main(args: Array[String]): Unit = {
    /**
     * _作用
     * import：导包时类似java中的*
     * class：给类起别名、隐藏类
     * match：模式匹配的默认case分支
     * var：系统给全局变量的初始赋值
     * function：替代方法中只使用一次的参数、可以让函数不执行而是作为返回值
     *
     * match模式匹配
     * 1.如果前面case都不匹配,就执行条件守卫case _ 分支,没有case _ 分支会抛异常MatchError
     * 2.每个case分支省略了break关键字,执行当前case分支后会自动中断
     * 3.可以在match中使用其他类型,除了字符还可以是表达式
     * 4.=> 后面代码块的{}可以省略
     */

    // 匹配字符串
    val n1: Int = 10
    val n2: Int = 20
    for (ch <- "+-3") {
      val res: Int = ch match {
        case '+' => n1 + n2
        case '-' => n1 - n2
        // case后面除了跟字符还可以跟表达式
        case _ if ch.toString.equals("3") => 3
        // 条件守卫一般放在最下面一行,不然if判断可能走不到
        case _ => 100
      }
      println(res)  // 30 -10 3
    }

    // 匹配类型
    val l: List[Any] = List(1, BigInt(3), "2", Array("aa"), Array(1,2,3), Array("aa",1), Map("aa" -> 1), Map(1 -> "aa"))
    for (obj <- l) {
      val res: Any = obj match {
        case i: Int => "数字"
        case i: BigInt => Int.MaxValue
        case i: String => "字符串"
        // i1和i2是不同结果,此处String和Int不是泛型,编译成.class文件会变成int[]和string[]
        case i1: Array[String] => "字符串数组"
        case i2: Array[Int] => "数字数组"
        case i: Array[Any] => "任意类型数组"
        // i3和i4是相同结果,在类型匹配时泛型不起作用
        case i3: Map[String, Int] => "字符串-数字 Map集合"
        case i4: Map[Int, String] => "数字-字符串 Map集合"
        case _ => None
      }
      println(res)
    }

    // 匹配数组
    for (arr <- Array(Array(0), Array(1,0), Array(0,1,0), Array(1,1,0))) {
      val res: Any = arr match {
        case Array(0) => "0"
        case Array(x,y) => x + y
        case Array(0, _*) => "以0开头的数组"
        case _ => None
      }
      println(res)
    }

    // 匹配集合
    for (list <- Array(List(0), List(1,0), List(0,0,0), List(1,0,0))) {
      val res: Any = list match {
        case 0 :: Nil => "0"
        case x :: y :: Nil => x + y
        case 0 :: _ => "以0开头的集合"
        case _ => None
      }
      println(res)
    }

    // 匹配元组
    for (tuple <- Array((0,1), (1,0), (2,1), (1,0,2))) {
      val res: Any = tuple match {
        case (0, _) => "0..."
        case (y, 0) => y
        case (a, b) => (b, a)
        case _ => None
      }
      println(res)
    }

    // 匹配对象
    object Square {
      def apply(arg: Double): Double = arg * arg
      def unapply(arg: Double): Option[Double] = Some(math.sqrt(arg))
    }
    val num: Double = 36
    num match {
      // 模式匹配会自动调用对象的unapply方法,从对象中提取值
      case Square(num) => println(num)  // 6.0
      case _ => None
    }


    val list1: List[List[Int]] = List(List(1,2), List(3,4))
//    val list2: List[Int] = list1.flatMap(_)
    // 此处 i => i 不能简写成_,因为无法推断这个_到底是参数还是方法
    val list2: List[Int] = list1.flatten
    println(list2)  // List(1, 2, 3, 4)

    val list3: List[String] = List("hello scala", "hello spark")
    val list4: List[Char] = list3.flatten
    println(list4)  // List(h, e, l, l, o,  , s, c, a, l, a, h, e, l, l, o,  , s, p, a, r, k)
    // 此处 (i: String) => i 可以简写成_: String,因为后面调用了split方法,说明_是参数而不是方法
    val list5: List[String] = list3.flatMap((i: String) => i.split(" "))
    println(list5)  // List(hello, scala, hello, spark)

    // 需求：将集合中的数字数加一,非数字舍弃
    // 复杂版：filter + map
    val list6: List[Any] = List(1,2,3,4,"abc")
    val list7: List[Any] = list6.filter((i: Any) => i.isInstanceOf[Int])
    println(list7)  // List(1, 2, 3, 4)
    val list8: List[Int] = list7.map { case i: Int => i + 1 }
    println(list8)  // List(2, 3, 4, 5)
    // 偏函数：将包含在{}内的一组case语句封装成函数,只对符合条件的元素进行逻辑操作
    // map不支持偏函数,因为map是遍历所有数据,不能指定条件,scala.MatchError: abc (of class java.lang.String)
//    list6.map { case i: Int => i + 1}
    val list9: List[Int] = list6.collect { case i: Int => i + 1 }
    println(list9)  // List(2, 3, 4, 5)

  }

}
