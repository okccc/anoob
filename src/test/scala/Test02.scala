import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
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

  @Test
  def testRDD(): Unit = {
    val conf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("RDD")
    val sc: SparkContext = new SparkContext(conf)

//    val arrayBuffer: ArrayBuffer[JSONObject] = new ArrayBuffer[JSONObject]()
//    val map: util.Map[String, Object] = new util.HashMap[String, Object]()
//    map.put("orc", "grubby")
//    map.put("ne", "moon")
//    val jsonObj: JSONObject = new JSONObject()
//    jsonObj.fluentPutAll(map)
//    arrayBuffer.append(jsonObj)
//    println("aaa = " + arrayBuffer)
//    val rdd: RDD[JSONObject] = sc.makeRDD(arrayBuffer)
//    println(rdd.foreach((j: JSONObject) => println("bbb = " + j)))

    val fileRDD: RDD[String] = sc.textFile("input/aaa.txt", minPartitions = 2)
    fileRDD.foreach(println)
    val lineRDD: RDD[String] = fileRDD.flatMap((line: String) => line.split(" "))
    lineRDD.foreach(println)
  }

  @Test
  def testScala(): Unit = {
    // 扁平化案例
    val list_any: List[Any] = List(1,List(2,3),4,List(5,6))
    val list_any_new: List[Int] = list_any.flatMap((any: Any) => {
      any match {
        case any: Int => List(any)
        case any: List[Int] => any
      }
    })
    println(list_any_new)  // List(1, 2, 3, 4, 5, 6)
  }
}
