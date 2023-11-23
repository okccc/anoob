package com.okccc.util;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author: okccc
 * @Date: 2021/11/4 上午11:07
 * @Desc: 线程池工具类：创建连接池和线程池都属于重量级操作,很消耗资源,可以使用单例模式只创建一次对象,静态代码块相当于饿汉式
 */
public class ThreadPoolUtil {

    // 声明线程池
    private static volatile ThreadPoolExecutor poolExecutor;

    // 创建线程池
    public static ThreadPoolExecutor getInstance() {
        // 效率问题：先判断对象是否存在,有就直接返回,不然每次进来都要上锁
        if (poolExecutor == null) {
            // 线程安全问题：CPU是逐条执行代码,线程执行具有随机性,谁抢到CPU谁执行,A和B都进来了,判断为空岂不是都要创建对象？所以要加锁
            synchronized (ThreadPoolUtil.class) {
                // A创建对象后释放锁,B拿到锁进来发现对象已存在,就不会重复初始化
                if (poolExecutor == null) {
                    System.out.println("---开辟线程池---");
                    poolExecutor = new ThreadPoolExecutor(
                            5,  // 初始线程数量
                            20,  // 最大线程数
                            300,  // 当线程池中空闲线程数量超过初始线程数量,会在指定时间后销毁
                            TimeUnit.SECONDS,  // 时间单位
                            new LinkedBlockingDeque<>(Integer.MAX_VALUE));  // 将任务放进队列,交给线程池中的线程执行
                }
            }
        }
        return poolExecutor;
    }

    public static void main(String[] args) {
        System.out.println(getInstance());
    }
}
