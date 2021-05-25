package com.okccc.util

import io.searchbox.client.config.HttpClientConfig
import io.searchbox.client.{JestClient, JestClientFactory}
import io.searchbox.core.{Bulk, BulkResult, DocumentResult, Get, Index, Search, SearchResult}
import org.elasticsearch.index.query.{BoolQueryBuilder, MatchQueryBuilder, TermQueryBuilder}
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder
import org.elasticsearch.search.sort.SortOrder
import java.util
import java.util.Properties

import com.okccc.spark.MovieInfo

import scala.collection.mutable

/**
 * Author: okccc
 * Date: 2020/12/31 9:59 上午
 * Desc: 操作es的工具类
 */
object ESUtil {

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
    val prop: Properties = PropertiesUtil.load("config.properties")
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
   * 往index中插入单个document
   * @param indexName 索引名称
   */
  def putIndex(indexName: String): Unit = {
    // 1.获取jest客户端
    val jestClient: JestClient = getJestClient

    // 3.封装Index对象,将样例类对象作为Builder的参数传入,底层会将其转换为json字符串,id可以不写会随机生成文档id
    val index: Index = new Index.Builder(generateSource()).index(indexName).`type`("_doc").id("1").build()

    // 2.执行index插入操作
    // 查看源码发现execute方法需传入Action<T>接口,F4查找发现有Index/Get/Update/Delete等实现类,并且这些类都使用了Builder模式
    // java代码此处需要try catch不然会编译异常,scala没有所谓的编译异常所以不需要
    jestClient.execute(index)

    // 4.关闭连接
    jestClient.close()
  }

  /**
   * 往index中批量插入document
   * @param indexName 索引名称
   * @param docList 文档列表,String是文档编号保证数据的幂等性,Any是具体文档通常对应java bean
   */
  def bulkIndex(indexName: String, docList: List[(String, Any)]): Unit = {
    // 1.获取jest客户端
    if(docList != null && docList.nonEmpty) {
      val jestClient: JestClient = getJestClient

      // 3.封装Bulk对象
      val builder: Bulk.Builder = new Bulk.Builder
      // 遍历文档列表
      for ((doc_id, doc) <- docList) {
        // 每个doc都是Index对象
        val index: Index = new Index.Builder(doc).index(indexName).`type`("_doc").id(doc_id).build()
        // 将Index对象添加到Bulk对象中
        builder.addAction(index)
      }
      val bulk: Bulk = builder.build()

      // 2.执行bulk批量操作
      val result: BulkResult = jestClient.execute(bulk)
      val items: util.List[BulkResult#BulkResultItem] = result.getItems
      println("往es的 " + indexName + " 索引中插入了 " + items.size() + " 条文档")

      // 4.关闭连接
      jestClient.close()
    }
  }

  /**
   * 根据index名称和docId查询单个document
   * @param indexName 索引名称
   * @param docId 文档id
   * @return 单个文档结果
   */
  def getIndexById(indexName: String, docId: String): String = {
    // 1.获取jest客户端
    val jestClient: JestClient = getJestClient

    // 3.封装Get对象,将index名称和文档id作为Builder的参数传入
    val get: Get = new Get.Builder(indexName, docId).build()

    // 2.执行get查询操作
    val result: DocumentResult = jestClient.execute(get)

    // 4.关闭连接
    jestClient.close()
    result.getJsonString
  }

  /**
   * 根据index名称和query条件批量查询document
   * @param indexName 索引名称
   * @return 多个文档结果组成的列表
   */
  def getIndexByQuery(indexName: String): List[util.Map[String, Any]] = {
    // 1.获取jest客户端
    val jestClient: JestClient = getJestClient

    // 3.封装Search对象,将条件查询字符串作为Builder的参数传入
    val search: Search = new Search.Builder(generateQueryStr()).addIndex(indexName).build()

    // 2.执行search查询操作
    val result: SearchResult = jestClient.execute(search)
    // 获取命中结果,es是java写的所以返回的集合也是java集合
    val hitList: util.List[SearchResult#Hit[util.Map[String, Any], Void]] = result.getHits(classOf[util.Map[String, Any]])
    // 只取命中结果的"_source"部分,为了方便转换数据结构,将java集合隐式转换为scala集合,scala有很多强大的函数
    import scala.collection.JavaConverters._
    val hit_list: mutable.Buffer[SearchResult#Hit[util.Map[String, Any], Void]] = hitList.asScala
    val source_list: List[util.Map[String, Any]] = hit_list.map((sr: SearchResult#Hit[util.Map[String, Any], Void]) => sr.source).toList

    // 4.关闭连接
    jestClient.close()
    source_list
  }

  def generateSource(): Object = {
    // 创建样例类对象
    val actorList: util.ArrayList[util.Map[String, Any]] = new util.ArrayList[util.Map[String, Any]]()
    val hashMap: util.HashMap[String, Any] = new util.HashMap[String, Any]()
    hashMap.put("id", 1)
    hashMap.put("name", "grubby")
    actorList.add(hashMap)
    val movie: MovieInfo = MovieInfo(1, "war3", 9.5, actorList)
    movie
  }

  def generateQueryStr(): String = {
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

    // 手动拼接不便于维护,应该使用面向对象的方式,通过类来封装查询字符串对象
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
    queryStr
  }

  def main(args: Array[String]): Unit = {
    val jest: JestClient = getJestClient
    // 测试连接
    println(jest)
    // 测试插入数据
    putIndex("movie_cn")
    // 测试查询数据
    println(getIndexById("movie_cn", "2"))
    println(getIndexByQuery("movie_index"))
  }
}
