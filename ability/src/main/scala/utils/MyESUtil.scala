package utils

import io.searchbox.client.config.HttpClientConfig
import io.searchbox.client.{JestClient, JestClientFactory}
import io.searchbox.core.{DocumentResult, Get, Index, Search, SearchResult}
import org.elasticsearch.index.query.{BoolQueryBuilder, MatchQueryBuilder, QueryBuilder, TermQueryBuilder}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder
import org.elasticsearch.search.sort.SortOrder

import java.util
import java.util.Properties
import scala.collection.mutable

/**
 * @author okccc
 * @date 2020/12/31 9:59 上午
 * @desc 操作es的工具类
 */
object MyESUtil {

  // 声明jest客户端工厂
  private var jestClientFactory: JestClientFactory = _

  /**
   * 从jest客户端工厂获取jest客户端
   * @return JestClient
   */
  def getJestClient: JestClient = {
    if (jestClientFactory == null) {
      jestClientFactory = build()
    }
    val jestClient: JestClient = jestClientFactory.getObject
    jestClient
  }

  /**
   * 创建jest客户端工厂
   * @return JestClientFactory
   */
  def build(): JestClientFactory = {
    // 1.创建jest客户端工厂
    val factory: JestClientFactory = new JestClientFactory
    // 3.获取es地址
    val prop: Properties = MyPropertiesUtil.load("config.properties")
    val es_server: String = prop.getProperty("es.server")
    // 2.工厂属性配置,HttpClientConfig使用了Builder模式
    factory.setHttpClientConfig(new HttpClientConfig
    .Builder(es_server)  // es地址
    .maxTotalConnection(10)  // 最大连接数
    .connTimeout(10000)  // 连接超时
    .readTimeout(1000)  // 读取超时
    .multiThreaded(true)  // 开启多线程
    .build()
    )
    factory
  }

  /**
   * 往index中插入document
   */
  def putIndex(): Unit = {
    // 1.获取jest客户端
    val jestClient: JestClient = getJestClient
    // 4.创建样例类对象
    val actorList: util.ArrayList[util.Map[String, Any]] = new util.ArrayList[util.Map[String, Any]]()
    val hashMap: util.HashMap[String, Any] = new util.HashMap[String, Any]()
    hashMap.put("id", 1)
    hashMap.put("name", "兽王")
    actorList.add(hashMap)
    val movie: Movie = Movie(1, "魔兽争霸3", 9.5, actorList)
    // 3.封装Index对象,将样例类对象作为Builder的参数传入,底层会将其转换为json字符串
    val index: Index = new Index.Builder(movie).index("movie_cn").`type`("_doc").id("1").build()
    // 2.执行index操作
    // 查看源码发现execute方法需传入Action<T>接口,F4查找发现有Index/Get/Update/Delete等实现类,并且这些类都使用了Builder模式
    jestClient.execute(index)
    // 5.关闭连接
    jestClient.close()
  }

  /**
   * 根据index名称和doc_id查询document
   * @param index 索引名称
   * @param id 文档id
   * @return 单个文档结果
   */
  def getIndexById(index: String, id: String): String = {
    // 1.获取jest客户端
    val jestClient: JestClient = getJestClient
    // 3.封装Get对象,将index名称和文档id作为Builder的参数传入
    val get: Get = new Get.Builder(index, id).build()
    // 2.执行get操作
    val result: DocumentResult = jestClient.execute(get)
    // 4.获取命中结果
    val str: String = result.getJsonString
    // 5.关闭连接
    jestClient.close()
    str
  }

  /**
   * 根据index名称和query条件查询document
   * @param index 索引名称
   * @return 多个文档结果组成的列表
   */
  def getIndexByQuery(index: String): List[util.Map[String, Any]] = {
    // 1.获取jest客户端
    val jestClient: JestClient = getJestClient
    // kibana中编写的条件查询字符串
//    val queryStr: String =
//      """
//        |{
//        |  "query": {
//        |    "bool": {
//        |      "must": [
//        |        {"match": {"name": "red"}}
//        |      ],
//        |      "filter": [
//        |        {"term": {"actorList.name.keyword": "zhang yi"}}
//        |      ]
//        |    }
//        |  },
//        |  "from": 0,
//        |  "size": 20,
//        |  "sort": [
//        |    {"doubanScore": {"order": "desc"}}
//        |  ],
//        |  "highlight": {
//        |    "fields": {"name": {}}
//        |  }
//        |}
//        |""".stripMargin

    // 4.封装条件查询字符串,手动拼接字符串不便于维护,应该使用面向对象的方式,通过类来构建查询字符串(这块代码可以抽取出来)
    val sourceBuilder: SearchSourceBuilder = new SearchSourceBuilder
    // 该字符串主要包含"query" "from" "size" "sort" "highlight"几个部分
    // 查看源码发现query方法需传入QueryBuilder接口,F4查找实现类,"query"下面包含"bool","must","filter"等,都有相对应的java类或方法
    sourceBuilder
      .query(new BoolQueryBuilder()
        .must(new MatchQueryBuilder("name", "red"))
        .filter(new TermQueryBuilder("actorList.name.keyword", "zhang yi"))
      )
      .from(0)
      .size(10)
      .sort("doubanScore", SortOrder.DESC)
      .highlighter(new HighlightBuilder().field("name"))
    // 将SearchSourceBuilder对象转换成字符串
    val queryStr: String = sourceBuilder.toString()

    // 3.封装Search对象,将条件查询字符串作为Builder的参数传入
    val search: Search = new Search.Builder(queryStr).addIndex(index).build()
    // 2.执行search操作
    val result: SearchResult = jestClient.execute(search)
    // 5.获取命中结果
    val hitList: util.List[SearchResult#Hit[util.Map[String, Any], Void]] = result.getHits(classOf[util.Map[String, Any]])
    // 只取命中结果的"_source"部分,为了方便转换数据结构,将java集合隐式转换为scala集合,scala有着强大的函数功能
    import scala.collection.JavaConverters._
    val hit_list: mutable.Buffer[SearchResult#Hit[util.Map[String, Any], Void]] = hitList.asScala
    val source_list: List[util.Map[String, Any]] = hit_list.map((sr: SearchResult#Hit[util.Map[String, Any], Void]) => sr.source).toList
    // 6.关闭连接
    jestClient.close()
    source_list
  }

  def main(args: Array[String]): Unit = {
    val jest: JestClient = getJestClient
    // 测试连接
    println(jest)
    // 测试插入数据
    putIndex()
    // 测试查询数据
    println(getIndexById("movie_cn", "2"))
    println(getIndexByQuery("movie_index"))
  }
}

// 使用样例类封装index中的source对象
case class Movie(
                id: Int,
                name: String,
                doubanScore: Double,
                // es是java编写,所以在定义集合类型时要用java.util.*不然读不到数据,后续使用时为了方便操作数据结构可以隐式转换为scala集合类型
                actorList: util.List[util.Map[String, Any]]
                )
