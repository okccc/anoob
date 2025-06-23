package com.okccc.util;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import org.bson.Document;

/**
 * @Author: okccc
 * @Date: 2023/2/21 15:41
 * @Desc: 读写mongodb的工具类
 */
public class MongoUtil {

    private static MongoClient mongoClient;
    static {
        // 数据库地址 mongodb://${username}:${password}@${host}:${port}/${db}
        // 在uri中显式指定authSource告诉MongoDB驱动用哪个数据库做认证,如果cdc用户是在admin里创建的而不是在WAR3里,就会出现认证失败
        String uri = "mongodb://cdc:cdc@localhost:27017/WAR3?authSource=admin";
        try {
            // 创建mongo客户端
//            mongoClient = new MongoClient(new MongoClientURI(uri));
            mongoClient = MongoClients.create(uri);
            // 静态代码块随着类加载而加载,只执行一次,查看打印次数可以验证,main方法结束后会立即释放内存,下次调用会再次执行静态代码块
            System.out.println("create mongo client success!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 通用查询
     */
    public static void common() {
        // 遍历所有库
        for (String databaseName : mongoClient.listDatabaseNames()) {
            System.out.println(databaseName);
        }

        // 遍历库的所有集合
        MongoDatabase db = mongoClient.getDatabase("WAR3");
        for (String collectionName : db.listCollectionNames()) {
            System.out.println(collectionName);
        }

        // 遍历集合的所有文档
        MongoCollection<Document> collection = db.getCollection("users");
        System.out.println(collection.countDocuments());
        for (Document document : collection.find()) {
            System.out.println(document);
        }

        // 遍历集合的索引
        ListIndexesIterable<Document> listIndexes = collection.listIndexes();
        for (Document listIndex : listIndexes) {
            System.out.println("listIndex = " + listIndex);
        }
    }

    /**
     * 根据_id查询
     */
    public static String getValue(String dbName, String collectionName, String id, String key) {
        // 选择库
        MongoDatabase db = mongoClient.getDatabase(dbName);
        // 选择集合
        MongoCollection<Document> collection = db.getCollection(collectionName);
        // 条件查询
        FindIterable<Document> documents = collection.find(Filters.eq("_id", id));
        // _id是唯一键,所以迭代器只有一条记录
        MongoCursor<Document> iterator = documents.iterator();
        String res = null;
        while (iterator.hasNext()) {
            Document document = iterator.next();
            res =  document.getString(key);
        }
        return res;
    }

    /**
     * 根据非_id单条件查询
     */
    public static String getValue(String dbName, String collectionName, String field1, String value1, String field2) {
        // 选择库
        MongoDatabase db = mongoClient.getDatabase(dbName);
        // 选择集合
        MongoCollection<Document> collection = db.getCollection(collectionName);
        // 条件查询
        FindIterable<Document> documents = collection.find(Filters.eq(field1, value1));
        // 遍历迭代器
        MongoCursor<Document> iterator = documents.iterator();
        String res = null;
        while (iterator.hasNext()) {
            Document document = iterator.next();
            res = document.getString(field2);
        }
        return res;
    }

    /**
     * 根据非_id多条件查询(and)
     */
    public static String getValueByAnd(String dbName, String collectionName, String field1, String value1, String field2, String value2, String field3) {
        // 选择库
        MongoDatabase db = mongoClient.getDatabase(dbName);
        // 选择集合
        MongoCollection<Document> collection = db.getCollection(collectionName);
        // 条件查询
        FindIterable<Document> documents = collection.find(Filters.and(Filters.eq(field1, value1), Filters.eq(field2, value2)));
        // 遍历迭代器
        MongoCursor<Document> iterator = documents.iterator();
        String res = null;
        while (iterator.hasNext()) {
            Document document = iterator.next();
            res = document.getString(field3);
        }
        return res;
    }

    /**
     * 根据非_id多条件查询(or)
     */
    public static String getValueByOr(String dbName, String collectionName, String field1, String value1, String field2, String value2, String field3) {
        // 选择库
        MongoDatabase db = mongoClient.getDatabase(dbName);
        // 选择集合
        MongoCollection<Document> collection = db.getCollection(collectionName);
        // 条件查询,使用or要注意该字段值不能为空
        FindIterable<Document> documents = collection.find(Filters.or(Filters.eq(field1, value1), Filters.eq(field2, value2)));
        // 遍历迭代器
        MongoCursor<Document> iterator = documents.iterator();
        String res = null;
        while (iterator.hasNext()) {
            Document document = iterator.next();
            res = document.getString(field3);
        }
        return res;
    }

    public static void main(String[] args) {
        System.out.println(getValue("WAR3", "users", "grubby", "mobile"));
    }
}