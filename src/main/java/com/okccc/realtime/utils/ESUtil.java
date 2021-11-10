package com.okccc.realtime.utils;

import com.okccc.realtime.common.MyConfig;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.*;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: okccc
 * Date: 2021/10/27 下午2:41
 * Desc: es工具类
 */
public class ESUtil {

    // 声明jest客户端工厂
    private static JestClientFactory jestClientFactory = null;

    // 从jest客户端工厂获取jest客户端
    public static JestClient getJestClient() throws IOException {
        if (jestClientFactory == null) {
            jestClientFactory = build();
        }
        return jestClientFactory.getObject();
    }

    public static JestClientFactory build() throws IOException {
        // 创建jest客户端工厂
        JestClientFactory factory = new JestClientFactory();
        // 工厂属性配置,HttpClientConfig使用了构造者模式
        factory.setHttpClientConfig(new HttpClientConfig
                .Builder(MyConfig.ES_SERVER)
                .maxTotalConnection(10)
                .connTimeout(10000)
                .readTimeout(1000)
                .multiThreaded(true)
                .build()
        );
        return factory;
    }

    // 往index中插入单个document
    public static void putIndex(String indexName) throws IOException {
        // 获取jest客户端
        JestClient jestClient = getJestClient();
        // 封装Index对象,将样例类对象作为Builder的参数传入,底层会将其转换为json字符串,id可以不写会随机生成文档id
        Index index = new Index.Builder(generateSource()).index(indexName).type("_doc").id("1").build();
        // 执行index插入操作,java代码此处需要try catch不然会编译异常,scala没有所谓的编译异常所以不需要
        try {
            // 查看源码发现execute方法需传入Action<T>接口,F4查找发现有Index/Get/Update/Delete等实现类,并且这些类都使用了Builder模式
            jestClient.execute(index);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭连接
            jestClient.close();
        }
    }

    /**
     * 往index中批量插入document
     * @param indexName 索引名称
     * @param docs Map<doc_id, doc>文档集合,doc_id是文档编号保证数据的幂等性,doc是具体文档通常对应java bean
     */
    public static void bulkIndex(String indexName, Map<String, Object> docs) throws IOException {
        if(docs != null && docs.size() > 0) {
            // 获取jest客户端
            JestClient jestClient = getJestClient();
            // 封装Bulk对象
            Bulk.Builder builder = new Bulk.Builder();
            // 遍历文档列表
            for (Map.Entry<String, Object> entry : docs.entrySet()) {
                // 每个doc都是Index对象
                Index index = new Index.Builder(entry.getValue()).index(indexName).type("_doc").id(entry.getKey()).build();
                // 将Index对象添加到Bulk对象中
                builder.addAction(index);
            }
            Bulk bulk = builder.build();

            try {
                // 执行bulk批量操作
                BulkResult result = jestClient.execute(bulk);
                List<BulkResult.BulkResultItem> items = result.getItems();
                System.out.println("往es的 " + indexName + " 索引中插入了 " + items.size() + " 条文档");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 关闭连接
                jestClient.close();
            }
        }
    }

    // 根据index名称和docId查询单个document
    public static String getIndexById(String indexName, String docId) throws IOException {
        // 获取jest客户端
        JestClient jestClient = getJestClient();
        // 封装Get对象,将index名称和文档id作为Builder的参数传入
        Get get = new Get.Builder(indexName, docId).build();
        // 执行get查询操作
        try {
            DocumentResult result = jestClient.execute(get);
            return result.getJsonString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 4.关闭连接
            jestClient.close();
        }
        return null;
    }

    // 根据index名称和query条件批量查询document
    public static List<Map<String, Object>> getIndexByQuery(String indexName) throws IOException {
        ArrayList<Map<String, Object>> list = new ArrayList<>();
        // 获取jest客户端
        JestClient jestClient = getJestClient();
        // 封装Search对象,将条件查询字符串作为Builder的参数传入
        Search search= new Search.Builder(generateQueryStr()).addIndex(indexName).build();
        try {
            // 执行search查询操作
            SearchResult result = jestClient.execute(search);
            // 获取命中结果
            List<SearchResult.Hit<Map, Void>> hitList = result.getHits(Map.class);
            // 只取命中结果的"_source"部分
            for (SearchResult.Hit<Map, Void> hit : hitList) {
                list.add(hit.source);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭连接
            jestClient.close();
        }
        return list;
    }

    public static Object generateSource() {
        // 创建样例类对象
        ArrayList<Map<String, Object>> actorList = new ArrayList<>();
        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("id", 1);
        hashMap.put("name", "grubby");
        actorList.add(hashMap);
        return new MovieInfo(1, "war3", 9.5, actorList);
    }

    public static String generateQueryStr() {
        // kibana中编写的条件查询字符串
//    String queryStr =
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
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
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
                .highlighter(new HighlightBuilder().field("name"));
        // 将SearchSourceBuilder对象转换成字符串
        return sourceBuilder.toString();
    }

    // 使用POJO类封装es的index中的source对象
    public static class MovieInfo {
        public Integer id;
        public String name;
        public Double doubanScore;
        public List<Map<String, Object>> actorList;  // [{"name" : "兽王", "id" : 1}]

        public MovieInfo() {
        }

        public MovieInfo(Integer id, String name, Double doubanScore, List<Map<String, Object>> actorList) {
            this.id = id;
            this.name = name;
            this.doubanScore = doubanScore;
            this.actorList = actorList;
        }

        @Override
        public String toString() {
            return "MovieInfo{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", doubanScore=" + doubanScore +
                    ", actorList=" + actorList +
                    '}';
        }
    }

    public static void main(String[] args) throws IOException {
        JestClient jestClient = getJestClient();
        // 测试连接
        System.out.println(jestClient);
        // 测试插入数据
        putIndex("movie_cn");
        // 测试查询数据
        System.out.println(getIndexById("movie_cn", "2"));
        System.out.println(getIndexByQuery("movie_index"));
    }

}
