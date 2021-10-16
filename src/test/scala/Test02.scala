import org.junit.Test

import scala.util.Random

class Test02 {

  @Test
  def testRandom(): Unit = {
    // 生成随机整数
    println(Random.nextInt(10))  // [0, 10)
    // 生成随机浮点数
    println(Random.nextDouble())  // [0.0, 1.0)
    // 生成随机字符串
    val alpha: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    println((1 to 10).map((_: Int) => alpha(Random.nextInt.abs % alpha.length)).mkString)
  }

  @Test
  def testStr(): Unit = {
    val str: String = "2020-12-14 14:39:23"
    println(str.substring(0, 10))
    println(str.substring(11, 13))
  }

}
