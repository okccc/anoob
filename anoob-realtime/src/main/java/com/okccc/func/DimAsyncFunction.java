package com.okccc.func;

import com.alibaba.fastjson.JSONObject;
import com.okccc.util.DimUtil;
import com.okccc.util.ThreadPoolUtil;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

/**
 * @Author: okccc
 * @Date: 2023/8/9 16:38:31
 * @Desc: 发送异步请求进行维度关联
 *
 * 输入数据可能是订单宽表流、支付宽表流或其它宽表流,所以使用泛型<T>设计成通用类
 * 模板方法设计模式：在父类中定义某个功能的核心算法骨架(步骤),具体实现延迟到子类完成,每个子类都可以有自己的不同实现(难点1)
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/asyncio/#async-io-api
 */
public abstract class DimAsyncFunction<T> extends RichAsyncFunction<T, T> implements DimJoinFunction<T> {

    // 具体维度表由调用者作为构造参数传入
    public String tableName;

    // 声明线程池,这里使用面向接口编程,向上抽取降低耦合度,是多态的表现形式(难点2)
//    ThreadPoolExecutor poolExecutor;
    private ExecutorService executorService;

    public DimAsyncFunction(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        // 初始化线程池对象
        executorService = ThreadPoolUtil.getInstance();
    }

    @Override
    public void asyncInvoke(T input, ResultFuture<T> resultFuture) {
        // 1.从线程池中获取线程发送异步请求
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                // 2.获取维度关联的key,这里并不知道调用对象是谁,不知道怎么实现就先定义成抽象方法,由具体子类实现(难点3)
                String key = getKey(input);
                // 3.查询维度数据
                JSONObject dimInfo = DimUtil.getDimInfoWithCache(tableName, key);
                // 4.将维度信息补充到流对象,和上面一样,这里并不知道调用对象是谁,也定义成抽象方法
                if (dimInfo != null) {
                    join(input, dimInfo);
                }
                // 5.获取与外部系统的交互结果,并发送给ResultFuture的回调函数
                resultFuture.complete(Collections.singleton(input));
            }
        });
        // 查看池中线程使用情况
        System.out.println(executorService);
    }

    @Override
    public void timeout(T input, ResultFuture<T> resultFuture) throws Exception {
        System.out.println("timeout: " + input);
    }

    // 抽象方法可以进一步向上抽取放到接口当中

    // 获取维度关联的key
//    public abstract String getKey(T input);

    // 将维度信息补充到L流对象
//    public abstract void join(T input, JSONObject dimInfo);
}
