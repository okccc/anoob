package com.okccc.design;

/**
 * @Author: okccc
 * @Date: 2020/12/31 2:09 下午
 * @Desc: java构造者模式：解决多参数构造方法的初始化问题
 *
 * 1.在类的内部创建静态内部类Builder
 * 2.Builder类的属性和外部类保持一致,由Builder类实现属性的setXxx()方法,并最终提供build()方法返回外部类对象
 * 优点：通过Builder类一步一步构建复杂对象,可以输入任意组合的参数,避免多参数构造方法重载出错,还不用写大量构造器
 *
 * es/spark/flink都大量使用了构造者模式
 * io.searchbox.client.config.HttpClientConfig
 * io.searchbox.core.Index/Get/Update/Delete/Search
 * org.apache.spark.sql.SparkSession
 * org.apache.flink.connector.kafka.source.KafkaSource/sink.KafkaSink
 * org.apache.flink.connector.file.src.FileSource/sink.FileSink
 * org.apache.flink.connector.jdbc.JdbcExecutionOptions/JdbcConnectionOptions
 * org.apache.flink.connector.elasticsearch.sink.Elasticsearch7SinkBuilder
 * org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig
 * org.apache.flink.api.common.state.StateTtlConfig
 */
public class BuilderDemo {

    private final String name;
    private final String mobile;
    private final String email;

    public BuilderDemo(String name, String mobile, String email) {
        this.name = name;
        this.mobile = mobile;
        this.email = email;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public static class Builder {
        private String name;
        private String mobile;
        private String email;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setMobile(String mobile) {
            this.mobile = mobile;
            return this;
        }

        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        public BuilderDemo build() {
            return new BuilderDemo(name, mobile, email);
        }
    }

    public static void main(String[] args) {
        BuilderDemo demo01 = BuilderDemo.builder().setName("grubby").setMobile("111").setEmail("orc@qq.com").build();
        BuilderDemo demo02 = new BuilderDemo.Builder().setName("moon").setMobile("222").setEmail("ne@qq.com").build();
        System.out.println(demo01.getName() + " - " + demo01.getMobile() + " - " + demo01.getEmail());
        System.out.println(demo02.getName() + " - " + demo02.getMobile() + " - " + demo02.getEmail());
    }
}

// 普通模式
//public class User01 {
//    private String name;
//    private String mobile;
//    private String email;
//
//    public User01(String name, String mobile) {
//        this.name = name;
//        this.mobile = mobile;
//    }
//
//    // 当构造方法的参数个数和参数类型完全一样时,方法重载会报错：'User01(String, String)' is already defined
//    public User01(String name, String email) {
//        this.name = name;
//        this.email = email;
//    }
//}