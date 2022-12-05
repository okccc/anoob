package com.okccc.realtime.util;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

/**
 * Author: okccc
 * Date: 2022/2/11 10:38 上午
 * Desc: es工具类
 */
public class ESUtil {

    private static final RestHighLevelClient restClient;

    // 创建es客户端
    static {
        // 不要密码
        restClient = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));
        // 需要密码
//        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
//        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("readonly", "readonly"));
//        restClient = new RestHighLevelClient(
//                RestClient
//                        .builder(new HttpHost("192.168.152.11", 9200, "http"))
//                        .setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
//                            @Override
//                            public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
//                                return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
//                            }
//                        })
//        );
    }

    /**
     * 索引操作
     */
    public static void doIndex() throws IOException {
        // 创建索引
        CreateIndexRequest createRequest = new CreateIndexRequest("user");
        CreateIndexResponse createResponse = restClient.indices().create(createRequest, RequestOptions.DEFAULT);
        System.out.println(createResponse.isAcknowledged());

        // 查询索引
        GetIndexRequest getRequest = new GetIndexRequest("user");
        GetIndexResponse getResponse = restClient.indices().get(getRequest, RequestOptions.DEFAULT);
        System.out.println(getResponse.getAliases());
        System.out.println(getResponse.getMappings());
        System.out.println(getResponse.getSettings());

        // 删除索引
        DeleteIndexRequest deleteRequest = new DeleteIndexRequest("user");
        AcknowledgedResponse deleteResponse = restClient.indices().delete(deleteRequest, RequestOptions.DEFAULT);
        System.out.println(deleteResponse.isAcknowledged());

        // 关闭es客户端
        restClient.close();
    }

    /**
     * 插入文档
     */
    public static void insertDoc() throws IOException {
        // 创建IndexRequest对象
        IndexRequest indexRequest = new IndexRequest();
        // 指定_index和_id,索引会自动创建,不写id会自动生成uuid
        indexRequest.index("user").id("003");
        // 生成POJO类对象并将其转换成json字符串
        User user = new User("grubby", "男", 18);
        // json常用解析库：jackson(SpringMVC)、fastjson(Alibaba)、gson(Google)
        String jsonStr = JSON.toJSONString(user);
        // 添加_source,必须是json格式,不然报错The number of object passed must be even but was [1],是因为没传第二个参数
        indexRequest.source(jsonStr, XContentType.JSON);
        // es客户端执行index操作
        IndexResponse indexResponse = restClient.index(indexRequest, RequestOptions.DEFAULT);
        System.out.println(indexResponse.getResult());  // CREATED/UPDATED
        // 关闭es客户端
        restClient.close();
    }

    /**
     * 修改文档
     */
    public static void updateDoc() throws IOException {
        // 创建UpdateRequest对象
        UpdateRequest updateRequest = new UpdateRequest();
        // 指定_index和_id
        updateRequest.index("user").id("01");
        // 修改doc
        updateRequest.doc(XContentType.JSON, "age", 20);
        // es客户端执行update操作
        UpdateResponse updateResponse = restClient.update(updateRequest, RequestOptions.DEFAULT);
        System.out.println(updateResponse.getResult());  // UPDATED
        // 关闭es客户端
        restClient.close();
    }

    /**
     * 删除文档
     */
    public static void deleteDoc() throws IOException {
        // 创建DeleteRequest对象
        DeleteRequest deleteRequest = new DeleteRequest();
        // 指定_index和_id
        deleteRequest.index("user").id("01");
        // es客户端执行delete操作
        DeleteResponse deleteResponse = restClient.delete(deleteRequest, RequestOptions.DEFAULT);
        System.out.println(deleteResponse.getResult());  // DELETED
        // 关闭es客户端
        restClient.close();
    }

    /**
     * 批量处理
     */
    public static void bulkDoc() throws IOException {
        // 创建BulkRequest对象
        BulkRequest bulkRequest = new BulkRequest();
        // 添加批量操作,包括插入、修改、删除
        bulkRequest.add(new IndexRequest().index("user").id("001").source(XContentType.JSON, "name", "aaa", "age", 19));
        bulkRequest.add(new UpdateRequest().index("user").id("001").doc(XContentType.JSON, "age", 20));
        bulkRequest.add(new DeleteRequest().index("user").id("001"));
        // es客户端执行bulk操作
        BulkResponse bulkResponse = restClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        System.out.println(bulkResponse.getTook());  // 7ms
        // 关闭es客户端
        restClient.close();
    }

    /**
     * 主键查询
     */
    public static void getDoc() throws IOException {
        // 创建GetRequest对象
        GetRequest getRequest = new GetRequest();
        // 指定_index和_id
        getRequest.index("user").id("01");
        // es客户端执行get操作
        GetResponse getResponse = restClient.get(getRequest, RequestOptions.DEFAULT);
        System.out.println(getResponse.getSourceAsString());  // {"age":19,"name":"grubby","sex":"男"}
        // 关闭es客户端
        restClient.close();
    }

    /**
     * 条件查询(可结合postman使用)
     */
    public static void searchDoc() throws IOException {
        // 创建SearchRequest对象
        SearchRequest searchRequest = new SearchRequest("war3");
        // 构造查询条件
        SearchSourceBuilder builder = new SearchSourceBuilder();

        // 查询所有
        builder.query(QueryBuilders.matchAllQuery());

        // 单字段查询,es字符串有text(分词)和keyword(不分词)两种类型,match会将查询条件进行分词,多个词条间是or关系
//        builder.query(QueryBuilders.matchQuery("title", "小米"));

        // 多字段查询
//        builder.query(QueryBuilders.multiMatchQuery("手机", "title", "category"));

        // 短语查询,相当于%like%
//        builder.query(QueryBuilders.matchPhraseQuery("title", "米"));

        // 单字段精准查询
//        builder.query(QueryBuilders.termQuery("name", "grubby"));

        // 多字段精准查询
//        builder.query(QueryBuilders.termsQuery("name", "grubby", "moon"));

        // 容错查询
//        builder.query(QueryBuilders.fuzzyQuery("name", "moo").fuzziness(Fuzziness.AUTO));

        // 范围查询
//        builder.query(QueryBuilders.rangeQuery("age").gte(18).lt(20));

        // 条件查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        boolQuery.must(QueryBuilders.matchQuery("race", "orc"));
        boolQuery.mustNot(QueryBuilders.matchQuery("name", "fly"));
        boolQuery.should(QueryBuilders.matchQuery("age", 19));
//        builder.query(boolQuery);

        // 简单聚合
        MaxAggregationBuilder aggregationBuilder = AggregationBuilders.max("ageStats").field("age");
//        builder.aggregation(aggregationBuilder);

        // 分组聚合
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders.terms("ageGroup").field("age");
//        builder.aggregation(termsAggregationBuilder);

        // 过滤字段
        String[] includes = {};
        String[] excludes = {"age"};
        builder.fetchSource(includes, excludes);
        // 排序,可以单字段也可以多字段
//        builder.sort("age", SortOrder.DESC);
        // 分页,数据量很大时默认只显示第一页,from当前页索引从0开始,size每页显示条数,from = (pageNum - 1) * size
//        builder.from(0).size(5);

        // 将查询条件添加到请求对象
        searchRequest.source(builder);
        // es客户端执行search操作
        SearchResponse searchResponse = restClient.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(searchResponse.getTook());  // 2ms
        // 遍历结果集
        SearchHits hits = searchResponse.getHits();
        System.out.println(hits.getTotalHits());  // 1 hits
        for (SearchHit hit : hits) {
            System.out.println(hit.getSourceAsString());  // {"age":18,"name":"grubby","sex":"男"}
        }
        // 关闭es客户端
        restClient.close();
    }

    @Data
    @AllArgsConstructor
    public static class User {
        private String name;
        private String sex;
        private Integer age;
    }

    public static void main(String[] args) throws Exception {
//        doIndex();
//        insertDoc();
//        updateDoc();
//        deleteDoc();
//        bulkDoc();
//        getDoc();
        searchDoc();
    }
}
