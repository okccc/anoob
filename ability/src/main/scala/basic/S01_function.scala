package basic

import scala.collection.immutable
import scala.util.control.Breaks.{break, breakable}


object S01_function {
  def main(args: Array[String]): Unit = {
    /**
     * Scala combines object-oriented and functional programming in one concise, high-level language.
     * Scala's static types help avoid bugs in complex applications, and its JVM and JavaScript runtimes
     * let you build high-performance systems with easy access to huge ecosystems of libraries.
     *
     * Scala是函数式编程,就是用函数进行声明的一种编程风格
     * 函数式编程通常只提供几种核心数据结构,它希望开发者能基于这些简单的数据结构组合出复杂的数据结构,Spark核心数据结构只有RDD
     * 面向对象编程鼓励开发者针对具体问题建立专门的数据结构
     * 函数必须有自变量(入参)和因变量(返回值)
     * 低阶函数：就是普通函数比如RDD的各种算子,低阶函数是复用的,语言开发者专注于优化低阶函数和核心数据结构
     * 高阶函数：用户可以将自定义函数以参数的形式传入低阶函数中,应用开发者专注于优化高阶函数
     * 惰性求值是函数式编程常见特性,由闭包实现,延迟计算可以对开销大的计算按需计算,Spark也是采用惰性求值触发计算
     */

    // 1.如果函数没有返回值,会采用Unit声明(等同于java的void),其实Unit和=都可以省略,当函数体中只有一行代码时{}也可以省略
    def test1(): Unit = {
      println("grubby")
    }
    test1()

    // 2.如果函数有返回值,scala会根据函数体的最后一行代码推断返回值类型,所以返回值类型也可以省略,return关键字一般也省略
    // 如果函数声明中没有参数列表,()可以省略,调用函数时也不能加(),所以声明函数必须加def关键字,不然无法判断是变量还是函数
    // 如果函数声明时没有参数列表,()也没省略,调用函数时加不加()都行
    def test2(): String = {
      "grubby"
    }
    println(test2())

    def test3: String = "grubby"
    println(test3)

    // 3.调用带参数的函数时,如果不传值就使用初始化值,如果传值就按顺序覆盖初始化值,此时可用带名参数传值
    def test4(v1: Int = 10, v2: Int): Int = {
      v1 + v2
    }
    println(test4(v2 = 20)) // 30
    println(test4(15, 20)) // 35

    // 不定长参数
    def test5(v: Int *): Int = {
      var res: Int = 0
      for (i <- v) {res += i}
      res
    }
    println(test5(1, 2, 3)) // 6

    // 4.函数可以作为返回值传递
    def test6(): () => Unit = {
      // 不带参函数后面加下划线表示不执行该函数而只是作为外部函数的返回值
      // 带参函数就不用加下划线,因为执行带参函数式肯定要传值,不传值就不会执行
      test1 _
    }
    test6()() // grubby

    // 引申出闭包：内部函数使用到外部变量会改变其生命周期
    def outer(i: Int): Int => Int = {
      def inner(j: Int): Int = {i * j}
      inner
    }
    println(outer(2)) // <function1>
    println(outer(2)(3)) // 6

    // 函数柯里化：简化了闭包的写法
    def test8(i: Int)(j: Int): Int = {
      i * j
    }
    println(test8(3)(4)) // 12

    // 5.函数可以作为参数传递  f: (参数列表) => 返回值
    def test9(f: (Int, Int) => Int): Int = {
      f(10, 20)
    }
    def test10(i: Int, j: Int): Int = {
      i + j
    }
    println(test9(test10)) // 30

    // 使用匿名函数简化 (参数) => {返回值} (常用)
    println(test9((x: Int, y: Int) => {
      x + y
    })) // 30
    // 如果方法中参数只使用了一次,可以用下划线代替
    println(test9((_: Int) + (_: Int))) // 30

    // 递归
    def recursive(n: Int): Int = {
      if (n == 1) 1 else n * recursive(n - 1)
    }
    println(recursive(5)) // 120

    // 懒加载：延迟变量/函数的加载,等用到时再运算,特定场景下(比如查数据库)可以提升性能
    def sum(i: Int, j: Int): Int = {
      // 注意观察加lazy前后aaa和bbb的先后顺序
      println("aaa")
      i + j
    }
    lazy val result: Int = sum(10, 20)
    println("bbb")
    println("result=" + result)

    // 1.if判断
    val a: Int = 1
    // scala中任意表达式都有返回值,所以if else表达式也是有返回结果的,结果值取决于满足条件的代码体的最后一行内容
    // 如果判断逻辑中返回的结果类型相同那么变量类型能够自动推断出来,如果返回的结果类型不同那么会设定变量类型为Any
    // scala中()和{}是一样的,如果只有一行代码就用(),多行代码就用{}
    val res: Any = if (a == 1) {
      println(true)
      "grubby"
    } else {
      println(false)
    }
    println(res) // 注意观察res的类型

    // java三元表达式：string res = a > b ? a : b
    // python三元表达式：res = a if a > b else b
    // scala三元表达式：val res = if (a > b) a else b

    // 2.for循环
    // 左闭右闭
    for (i <- 1 to 3 reverse) {
      println(i)
    }
    // 左闭右开
    for (i <- 1 until 3) {
      println(i)
    }
    // 循环守卫
    for (i <- 1 to 3 if i != 2) {
      println(i)
    }
    // 循环步长
    for (i <- 1 to 10 by 2) {
      println(i)
    }
    // 引入变量
    for (i <- 1 to 3; j = 5 - i) {
      println(i, j)
    }
    // 嵌套循环
    for (i <- 1 to 3; j <- 1 to 3) {
      println(i, j)
    }
    // 循环返回新集合
    val seq_new: immutable.IndexedSeq[Int] = for (i <- 1 to 3) yield i
    println(seq_new) // Vector(1, 2, 3)

    // 3.while循环
    var n: Int = 1
    breakable {
      while (n <= 5) {
        println(n)
        n += 1
        if (n == 3) {break()}
      }
    }

    // 4.异常处理
    try {
      val num: Int = 10 / 0
    } catch {
      case exception: Exception => println(exception) // java.lang.ArithmeticException: / by zero
    } finally {
      println("...")
    }

    // 需求：将名单中的单字符删除,将首字母大写,拼成一个用逗号分隔的字符串
    // 命令式编程实现(python版)
//    names = ["grubby", "moon", "c", "ted"]
//    names_new = [i.capitalize() for i in names if len(i) > 1]
    // 函数式编程实现(scala版)
    val names: List[String] = List("grubby", "moon", "c", "ted")
    val names_new: List[String] = names.filter((i: String) => i.length > 1).map((f: String) => f.capitalize)
    println(names_new)
    // 代码解析：命令式编程只执行了1次循环,函数式编程执行了filter和map2次循环,每次循环只完成一种逻辑即用户编写的匿名函数
    // 性能上命令式编程更好,说明在硬件性能羸弱时函数式编程缺点会被放大,但是函数式编程不需要维护外部变量i,对于并行计算场景非常友好
  }
}
