package com.okccc.db;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
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
        String uri = "mongodb://root:root@localhost:27017/WAR3";
        try {
            // 创建mongo客户端
            mongoClient = new MongoClient(new MongoClientURI(uri));
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
        // 查询所有库
        for (String databaseName : mongoClient.listDatabaseNames()) {
            System.out.println(databaseName);
        }

        // 查询库的所有集合
        MongoDatabase db = mongoClient.getDatabase("WAR3");
        for (String collectionName : db.listCollectionNames()) {
            System.out.println(collectionName);
        }

        // 查询集合的所有文档
        MongoCollection<Document> collection = db.getCollection("users");
        System.out.println(collection.countDocuments());
        for (Document document : collection.find()) {
            System.out.println(document);
        }
    }

    /**
     * 根据_id查询
     */
    public static String getDocumentById(String dbName, String collectionName, String value, String key) {
        // 选择库
        MongoDatabase db = mongoClient.getDatabase(dbName);
        // 选择集合
        MongoCollection<Document> collection = db.getCollection(collectionName);
        // 条件查询
        FindIterable<Document> documents = collection.find(Filters.eq("_id", value));
        // _id是唯一键,所以迭代器只有一条记录
        MongoCursor<Document> iterator = documents.iterator();
        while (iterator.hasNext()) {
            Document document = iterator.next();
            return document.getString(key);
        }
        return null;
    }

    public static void main(String[] args) {
        System.out.println(getDocumentById("WAR3", "users", "grubby", "mobile"));
    }
}