import java.util.{Date, UUID}

import commons.utils.{DateUtils, NumberUtils}
import org.joda.time.{DateTime, LocalDate}
import scala.util.Random

object Test {
  def main(args: Array[String]): Unit = {
    val uuid: String = UUID.randomUUID().toString
    println(uuid)
    val time: String = DateTime.now().toString("yyyy-MM-dd")
    println(time)
    val str: String = DateUtils.getPlusDay(5)
    println(str)
    val str1: String = DateUtils.getMinusDay(1)
    println(str1)
//    val formatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
//    val time1: DateTime = formatter.parseDateTime("2019-07-03 13:53:21")
//    println(time1)
    val date: Date = DateUtils.parseTime("2020-01-09 12:30:24")
    println(date)
    val str2: String = DateUtils.formatTime(new Date())
    println(str2)
    val i: Int = DateUtils.timeDiff("2020-01-09 12:30:24", "2020-01-09 12:31:54")
    println(i)
    val i1: Int = DateUtils.dateDiff("2020-05-03", "2020-04-12")
    println(i1)
    val str3: String = DateUtils.getDateHour("2020-08-12 12:30:12")
    println(str3)
    println(DateUtils.getMinusDay(2))
    println(DateUtils.getMinusWeek(2))
    println(DateUtils.getMinusMonth(2))
    println(DateUtils.getMinusYear(2))
    println(DateTime.now().dayOfMonth().withMaximumValue().toString("yyyy-MM-dd"))
    println(LocalDate.now().dayOfMonth().withMinimumValue())

    println(DateUtils.getFirstDateOfMonth)
    println(DateUtils.getFirstDateOfWeek)
    println(DateUtils.getFirstDateOfYear)

    println(DateUtils.getLastDateOfMonth)
    println(DateUtils.getLastDateOfWeek)
    println(DateUtils.getLastDateOfYear)

    val d: Double = NumberUtils.formatDouble(100.01321031321, 2)
    println(d)

    val i2: Int = new Random().nextInt(10)
    println(i2)


    val b: Byte = 10
    val s: Short = 20
    val c: Char = 'a'
    val i3: Int = b + s
    val i4: Int = b + c
    println(i3, i4)

  }
}
