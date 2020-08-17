package basic

import java.io.File
import scala.io.Source
import scala.reflect.ClassTag


object S03_implicit {
  def main(args: Array[String]): Unit = {
    /**
     * 装饰模式/OCP原则：不改变原先代码动态扩展功能
     * 隐式转换：scala可以将函数/变量/参数标记为implicit,编译器会尝试自动转换使编译通过,简化程序设计同时增加灵活性
     * 使用场景
     * 1.当方法中的参数类型和目标类型不一致时
     * 2.当对象调用类中不存在的方法或成员时,编译器会自动将对象隐式转换
     *
     * Spark中的RDD之所以可以进行基于Key的Shuffle操作,是因为RDD被隐式转换为PairRDDFunctions类型(https://www.cnblogs.com/xia520pi/p/8745923.html)
     */

    // 隐式函数：当方法中的参数类型和目标类型不一致时会使用隐式函数转换
    implicit def transform(d: Double): Int = d.toInt
    val i: Int = 5.0  // Double类型的5.0会隐式转换为Int类型的5
    println(i)  // 5

    // 隐式变量：编译器会在方法省略隐式参数时去搜索作用域内的隐式值作为缺省参数使其编译通过
    // 使用场景：随着业务升级发现函数的默认值没用了但是又不想更改原有代码,此时可以额外设置一个隐式变量值
    implicit val frame: String = "hadoop"
//    implicit val name: String = "grubby"  // 注意：多个隐式值会冲突

    // 隐式参数：调用方法时可以不传,编译器会在当前作用域内搜索可以使用的隐式变量值
    def coding(language: String)(implicit frame: String = "spark"): Unit = {
      println(language + ":" + frame)
    }
    // 调用函数时不添加()表示调用的是不带参函数,不会使用默认参数值,为了使编译通过会在当前作用域搜索String类型的隐式值
    coding("java") // java:hadoop
    // 调用函数时添加()表示调用的是带参函数,会使用默认参数值使编译通过,此时不涉及隐式转换
    coding("scala")()  // scala:spark
    // 调用方法时添加()并且明确指定参数,就使用给定参数使编译通过,此时不涉及隐式转换
    coding("python")("django")  // python:django
  }
}

// RichFile类相当于是File类的增强类,将File类作为参数传入构造器
class RichFile(val file: File) {
  def read(): String = {Source.fromFile(file).mkString("")}
}
// 定义隐式转换函数的单例对象
object MyPredef {
  // File => RichFile
  implicit def file2RichFile(file: File): RichFile = new RichFile(file)
}
// 程序入口
object Hello {
  def main(args: Array[String]): Unit = {
    val file: File = new File("scala/input/aaa.txt")
    // RichFile类直接调用read方法
    println(new RichFile(file).read())
    // File类本身没有read方法,通过隐式转换完成
    import MyPredef.file2RichFile  // 导入隐式转换
    println(file.read())
  }
}

/**
 * 泛型：在声明类和方法时可以指定泛型类型[T]来约束参数类型,提升程序的健壮性和稳定性,Array/List/Map等容器就是泛型类
 * 调用时指定[T],返回必须是T类型,编译时会检查类型,不满足则编译不通过
 * 调用时不指定[T],scala会根据传递的变量值自动推断类型使编译通过而不报错
 * [A <：B]上边界,A类型必须是B类型或其子类
 * [A >：B]下边界,A类型必须是B类型或其父类
 * 使用场景：不确定类型T有没有compareTo方法
 * 编译报错：class Pair[T](val a: T, val b: T) {def bigger = if (a.compareTo(b)>0) a else b}
 * 编译通过：class Pair[T <：Comparable](val a: T, val b: T) {def bigger = if (a.compareTo(b)>0) a else b}
 * [A <% B]视图边界(上边界加强版),A类型会隐式转换到B类型
 * [T : Ordering]上下文界定,说明存在一个隐式值implicit order: Ordering[T] (spark常用)
 * [T : ClassTag]上下文界定,动态类型,编译时没有类型运行时才确定类型 (spark程序分为Driver和Executor,运行时才知道完整类型信息)
 * [+T]协变,如果A是B的子类,那么Class[A]是Class[B]的子类
 * [-T]逆变,如果A是B的子类,那么Class[A]是Class[B]的父类
 */
class Pair01[T <: Comparable[T]](val a: T, val b: T) {
  // [T <: Comparable[T]] 表示类型T必须是Comparable[T]接口的子类
  def bigger(): T = {
    // Comparable[T]接口有compareTo方法,只能比较字符串,如果想比较数字则需要隐式转换
    if (a.compareTo(b) > 0) a else b
  }
}
//class Pair02[T <% Comparable[T]](val a: T, val b: T){
// Int不是Comparable[T]的子类型,scala会将其隐式转换成间接实现了Comparable[T]接口的RichInt
class Pair02[T](val a: T, val b: T)(implicit elem: T => Comparable[T]) {
  def bigger(): T = {
    if (a.compareTo(b) > 0) a else b
  }
}
//class Pair03[T <% Ordered[T]](val a: T, val b: T){
// String不是Ordered[T]的子类型,scala会将其隐式转换成间接实现了Ordered[T]特质的RichString
class Pair03[T](val a: T, val b: T)(implicit elem: T => Ordered[T]) {
  def bigger(): T = {
    // Ordered[T]特质实现了Comparable[T]接口,有更直观的比较大小方法 > < >= <=
    if (a > b) a else b
  }
}
class Pair04[T : Ordering](val a: T, val b: T) {
  // 隐式参数
  def bigger(implicit order: Ordering[T]): T = {
    // Ordering特质实现了Comparator[T]接口,compare方法可以比较大小
    if (order.compare(a, b) > 0) a else b
  }
}

object Generics {
  def main(args: Array[String]): Unit = {
    // 创建Pair对象时没有指定泛型[T],scala会根据传入变量值自动推断返回值类型
    val p11: Pair01[String] = new Pair01("hadoop", "spark")
//    val p12: Pair01[Int] = new Pair01(100, 200)  // 报错：type mismatch
    println(p11.bigger())  // spark
    val p21: Pair02[String] = new Pair02("hadoop", "spark")
    val p22: Pair02[Int] = new Pair02(100, 200)
    println(p21.bigger())  // spark
    println(p22.bigger())  // 200
    val p31: Pair03[String] = new Pair03("hadoop", "spark")
    val p32: Pair03[Int] = new Pair03(100, 200)
    println(p31.bigger())  // spark
    println(p32.bigger())  // 200
    val p41: Pair04[String] = new Pair04("hadoop", "spark")
    val p42: Pair04[Int] = new Pair04(100, 200)
    println(p41.bigger)  // spark
    println(p42.bigger)  // 200
    // 创建泛型数组
    def mkArray[T : ClassTag](elems: T*): Array[T] = Array[T](elems: _*)
    val ints: Array[Int] = mkArray(11, 22)
    val strings: Array[String] = mkArray("aa","bb")
    val any: Array[Any] = mkArray(11, "aa")
  }
}
