package com.okccc.realtime.utils;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Author: okccc
 * Date: 2021/11/4 上午11:07
 * Desc: 线程池工具类
 */
public class ThreadPoolUtil {

    // 声明线程池
    private static ThreadPoolExecutor pool;

    /**
     * 使用单例模式创建线程池
     * corePoolSize       初始线程数量
     * maximumPoolSize    最大线程数
     * keepAliveTime      当线程池中空闲线程数量超过初始线程数量,会在指定时间后销毁
     * unit               时间单位
     * workQueue          要执行的任务队列
     */
    public static ThreadPoolExecutor getInstance() {
        // 先判断对象是否存在,是就直接返回,不用每次进来都判断锁
        if (pool == null) {
            // 同步代码块保证线程安全
            synchronized (ThreadPoolExecutor.class) {
                if (pool == null) {
                    System.out.println("---开辟线程池---");
                    pool = new ThreadPoolExecutor(4, 20, 300, TimeUnit.SECONDS,
                            new LinkedBlockingDeque<>(Integer.MAX_VALUE));
                }
            }
        }
        return pool;
    }

    public static void main(String[] args) {
        System.out.println(getInstance());
    }
}
