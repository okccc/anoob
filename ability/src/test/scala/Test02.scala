import org.junit.Test

import scala.util.Random

class Test02 {

  @Test
  def testRandom(): Unit = {
    println(Random.nextInt(10))
    println(Random.nextDouble())
  }
}
