package basic

object S02_object {
  def main(args: Array[String]): Unit = {
    /*
     * Scala是结合面向对象和函数式编程的静态类型语言
     *
     * scala六大特征
     * 和java无缝混编：都基于JVM
     * 类型推测：自动推测类型
     * 高阶函数：函数可以作为其它函数的参数(匿名函数)和返回值(闭包->柯里化)
     * 模式匹配：类似于java中的switch
     * 特质trait：整合java中接口和抽象类的优点
     * 并发和分布式：Actor
     *
     * 数据类型
     * java基本数据类型：byte/short/int/long/float/double/char/boolean,为了方便操作基本数据类型的数值java将其封装成了对象,在对象中
     * 定义了更加丰富的属性和行为,对应的包装类 Byte/Short/Integer/Long/Double/Character/Boolean
     * scala没有基本数据类型,一切数据皆是对象,都是Any的子类,包括AnyVal(值类型)和AnyRef(引用类型)
     * Unit：是不带任何意义的值类型,所有函数必须有返回,所以Unit也是有用的返回类型
     * Nothing：是所有数据类型的子类型,是程序抛异常或者不能正常返回的信号
     * Null：是所有AnyRef类型的子类型,主要满足scala和其它jvm语言的互通性,scala本身几乎不用.
     * java中的null是关键字不是对象,对它调用任何方法都是非法的,因此在调用方法时要经常做null值检查以避免NullPointerException
     * scala中当变量或函数返回值可能为null的时候,建议使用Option[T]类型,包含Some[T]和None两个子类,这样调用者就知道此处可能存在null值
     * 静态语言会在编译时做类型检查,当你尝试在一个可能为null的值上调用方法时编译是不通过的,避免在运行时出现空指针异常
     *
     * scala类型检查和转换
     * 判断对象类型 isInstanceOf[T] 相当于java的 instanceOf T
     * 强制类型转换 asInstanceOf[T] 相当于java的 (T)obj
     * 获取类的对象 ClassOf[T]      相当于java的 T.class
     *
     * 构造函数
     * 主构造函数：scala面向函数编程,类的整个主体是主构造函数,可以带参数也可以不带参数
     * 辅构造函数：可以给类定义多个辅构造函数以提供创建对象的不同方法,用this关键字表示且函数体第一行必须调用主构造函数或先前某个辅构造函数
     * scala中的this关键字用于引用当前对象,可以使用this关键字调用变量、方法、构造函数
     *
     * 类和对象
     * 类是对象的抽象,对象是类的实例
     * 类只会被编译不会被执行,都是在object中执行
     * 类可以带参数单例对象不行,因为单例对象不是用new关键字实例化的,没机会给构造函数传递参数
     *
     * 静态
     * java中的静态随着类的加载而加载,优先于对象存在,所以静态成员可以由类名直接调用
     * 单例对象：scala完全面向对象没有静态概念,通过创建单例对象作为程序入口,单例对象的成员可以全局访问
     * 伴生对象：当单例对象和某个类共享名称时就被称为该类的伴生对象,伴生类和伴生对象可以相互访问私有成员
     * 独立对象：当单例对象没有与之相同名称的类时就是独立对象,通常用作scala应用入口或相关功能方法的工具类
     *
     * 继承和特质
     * java类只能单继承,接口可以多实现,接口不属于类的体系,接口中属性都是常量方法都是抽象,所以不存在菱形问题(B和C继承A,D继承B和C)
     * scala完全面向对象,所以不存在接口这个概念,当多个类具有相同特征时可以将这个特征独立出来,使用trait声明
     * 特质trait可以给对象动态扩展功能  class类名 extends 父类名 with 特质1 with 特质2 with 特质3
     */

    val b: Byte = 10
    val s: Short = 20
    val c: Char = 'a'  // char是单个字符
    val i1: Int = b + s
    val i2: Int = b + c
    println(i1, i2)  // 30, 107
  }
}

// scala中不加修饰符默认是public权限,private[包名]修饰的类只能在当前包下访问
class Person private[basic] {
  // val修饰的变量是只读的,相当于加了final关键字,只有get方法没有set方法,由于不可变所以初始化值必须显式指定而不能用下划线表示
  val id: Int = 1
  // var修饰的变量可读可写,既有get方法又有set方法,初始化值可用下划线表示,默认null或者0
  var name: String = _
  // private修饰的变量只有本类和其伴生对象可以访问,伴生类和伴生对象可以相互访问私有属性
  private var age: Int = _
  // private[this]修饰的变量是真正的私有化,只有本类可以访问,伴生对象也访问不了
  private[this] val gender: String = "male"
}
object Person {
  def main(args: Array[String]): Unit = {
    val p: Person = new Person
    println(p.id, p.name, p.age)  // (1,null,0)
    p.name = "grubby"
    p.age = 18
    println(p.id, p.name, p.age)  // (1,grubby,18)
  }
}

// 主构造函数的参数列表放在类名后面,private修饰的构造函数只有其伴生对象才能访问,别的单例对象无法访问
class User private(val name: String, var age: Int, skill: Int) {
  // 未修饰的参数无法直接使用,需添加get方法获取该参数值
  def getSkill: Int = skill
  var gender: String = _
  // 可以在主构造函数中声明辅构造函数,用this关键字表示
  def this(name: String, age: Int, skill: Int, gender: String) = {
    // 辅构造函数第一行必须调用主构造函数或已存在的某个辅构造函数,初始化对象
    this(name, age, skill)
    this.gender = gender
  }
}
object User {
  def main(args: Array[String]): Unit = {
    // 技巧：创建对象时如果有两行提示说明有辅构造函数
    // 通过主构造函数创创建对象
    val u1: User = new User("grubby", 18, 90)
    println(u1.name, u1.age, u1.getSkill, u1.gender)  // (grubby,18,90,null)
    // 通过辅构造函数创建对象
    val u2: User = new User("moon", 19, 85, "male")
    println(u2.name, u2.age, u2.getSkill, u2.gender)  // (moon,19,85,female)
  }
}

// 普通类：需手动实现一些常用方法,比如在伴生对象中定义apply/unapply方法
sealed class Orc (var name: String, var age: Int)
// 样例类：scala编译器会自动生成类的toString/equals/copy/hashCode方法,自动创建伴生对象并实现apply/unapply方法,构造参数默认val修饰
// 密封类：用于模式匹配的类最好标记为sealed,只允许在当前单元定义其子类,这样在match时只关注已知子类即可而不用担心在别的单元出现该密封类的子类
sealed case class Elf (name: String, age: Int)
object Orc {
  // apply()：相当于构造函数,接收构造参数创建对象,通常用于实例化伴生类
  def apply(name: String, age: Int): Orc = new Orc(name, age)
  // unapply()：是apply的反向操作,从现有对象中提取值,通常用于模式匹配
  // 为了避免NullPointerException,Scala经常以Option作为返回值类型,包含两个子类Some和None
  def unapply(o: Orc): Option[(String, Int)] = {
    if (o == null) None
    else Some(o.name, o.age)
  }
}
object Player {
  def main(args: Array[String]): Unit = {
    // 普通类创建对象
    val o1: Orc = Orc("grubby", 18)
    val o2: Orc = Orc("grubby", 18)
    println(o1.name, o1.age)  // (grubby,18)
    println(o1 == o2)  // false
    o1.name = "fly"
    o1 match {
      // 模式匹配会自动调用对象的unapply方法,从对象中提取值
      case Orc(name, age) => println(name, age)  // (fly,18)
      case _ => None
    }
    // 样例类创建对象
    val e1: Elf = Elf("moon", 19)
    val e2: Elf = Elf("moon", 19)
    println(e1.name, e1.age)  // moon,19
    // Comparison: Instances of case classes are compared by structure and not by reference
    println(e1 == e2)  // true
    // 函数式编程(FP)从不改变数据结构,这意味着构造参数默认是val修饰
//    e1.name = "sky"
    // 函数式编程(FP)从不改变数据结构,你只需要在克隆对象的过程中提供要修改的字段名称
    val e3: Elf = e1.copy("ted")
    // 样例类的最大优势是支持模式匹配,模式匹配也是函数式编程(FP)的主要特征之一
    e1 match {
      case Elf(name, age) => println(name, age)  // moon,19
      case _ => None
    }
  }
}

// 抽象类
abstract class Animal {
  // 抽象属性和方法(只声明不初始化)
  var name: String
  def eat()
  // 普通属性和方法
  var age: Int = 18
  def run(): Unit = {
    println("running...")
  }
}

// 特质
trait Behavior {
  var gender: String
  def sleep()
}

// 子类继承父类并实现特质
class Dog extends Animal with Behavior {
  // 重写父类和特质的抽象成员
  override var name: String = "sky"
  override def eat(): Unit = println("eating...")
  override var gender: String = "male"
  override def sleep(): Unit = println("sleeping...")
}

object Dog {
  def main(args: Array[String]): Unit = {
    // 创建子类对象
    val d: Dog = new Dog
    println(d.name, d.age, d.gender)  // (sky,18,male)
    d.eat()  // eating
    d.run()  // running
    d.sleep()  // sleeping
  }
}