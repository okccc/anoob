package com.okccc.realtime.app.func;

import com.alibaba.fastjson.JSONObject;
import com.okccc.realtime.util.DimUtil;
import com.okccc.realtime.util.ThreadPoolUtil;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

/**
 * @Author: okccc
 * @Date: 2021/11/5 下午4:38
 * @Desc: 维度表的异步查询
 */

// 输入数据可能是订单宽表流、支付宽表流或其它宽表流,所以使用泛型<T>设计成通用类
// 模板方法设计模式：在父类中定义某个功能的核心算法骨架,具体实现延迟到子类完成,每个子类都可以有自己的实现
// flink异步IO官方文档：https://ci.apache.org/projects/flink/flink-docs-release-1.13/zh/docs/dev/datastream/operators/asyncio/
public abstract class DimAsyncFunction<T> extends RichAsyncFunction<T, T> implements DimJoinFunction<T> {

    // 具体维度表由调用者传入
    public String tableName;

    public DimAsyncFunction(String tableName) {
        this.tableName = tableName;
    }

    // 声明线程池,ThreadPoolExecutor类实现了ExecutorService接口,这里使用面向接口编程的方式
//    ThreadPoolExecutor threadPoolExecutor;
    public ExecutorService executorService;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // 实例化线程池对象
        executorService = ThreadPoolUtil.getInstance();
    }

    // 使用多线程的方式发送异步请求
    @Override
    public void asyncInvoke(T input, ResultFuture<T> resultFuture) {
        // 通过线程池对象创建线程
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                // 从流对象获取维度关联的key,这里暂时还不知道key是啥,可以设置成抽象方法,由抽象类的子类进行方法重写实现具体逻辑
                String key = getKey(input);
//                System.out.println("维度关联的key是：" + key);
                // 根据表名和key去维度表查询维度数据
                JSONObject dimInfo = DimUtil.getDimInfoWithCache(tableName, key);
                if (dimInfo != null) {
                    // 将维度数据赋值给流对象的属性,这里暂时还不知道要给输入流对象的哪些属性赋值,同样可以设置成抽象方法
                    join(input, dimInfo);
                }
                // 将处理结果往下游传递
                resultFuture.complete(Collections.singleton(input));
            }
        });
    }

//    // 抽象方法可以进一步抽取出来放到接口当中
//    // 获取key的抽象方法
//    public abstract String getKey(T input);
//    // 给流对象的属性赋值的抽象方法
//    public abstract void join(T input, JSONObject dimInfo);
}