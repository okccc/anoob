import org.junit.Test

import scala.util.Random

class Test02 {

  @Test
  def testRandom(): Unit = {
    println(Random.nextInt(10))
    println(Random.nextDouble())
  }

  @Test
  def testStr(): Unit = {
    val str: String = "2020-12-14 14:39:23"
    println(str.substring(0, 10))
    println(str.substring(11, 13))
  }
}
