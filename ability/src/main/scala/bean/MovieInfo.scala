package bean

import java.util

/**
 * Author: okccc
 * @date 2021/1/3 11:10 下午
 * Desc: 使用样例类封装index中的source对象
 */
case class MovieInfo(
                      id: Int,
                      name: String,
                      doubanScore: Double,
                      // es是java编写,定义集合类型时要用java.util.*不然读不到数据,后续为了方便转换数据结构可以隐式转换为scala集合
                      // "actorList" : [{"name" : "兽王", "id" : 1}]
                      actorList: util.List[util.Map[String, Any]]
                    )
