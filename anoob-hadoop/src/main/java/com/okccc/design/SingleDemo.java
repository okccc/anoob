package com.okccc.design;

/**
 * @Author: okccc
 * @Date: 2020/12/31 2:09 下午
 * @Desc: 双重校验锁DCL(Double Check Lock)解决单例模式懒汉式的线程安全问题
 *
 * 1.在该类使用new创建一个本类对象
 * 2.私有化构造函数不允许其它程序创建对象
 * 3.对外提供静态get方法让其它程序可以获取该对象
 *
 * Arrays/Collections/Math/System等工具类将构造函数私有化,类中全部是静态方法,类名直接调用
 * Runtime类将构造函数私有化,对外提供getRuntime()方法获取单例对象,访问类中的非静态方法
 *
 * public class Demo {
 *     public static void main(String[] args) {
 *         Demo demo = new Demo();
 *     }
 * }
 * javac Demo.java  # javac命令将源代码文件Demo.java编译成字节码文件Demo.class
 * javap -c Demo.class  # javap命令反编译字节码文件
 * Compiled from "Demo.java"
 * public class Demo {
 *   public Demo();
 *     Code:
 *        0: aload_0
 *        1: invokespecial #1                  // Method java/lang/Object."<init>":()V
 *        4: return
 *
 *   public static void main(java.lang.String[]);
 *     Code:
 *        0: new           #2                  // class Demo
 *        3: dup
 *        4: invokespecial #3                  // Method "<init>":()V
 *        7: astore_1
 *        8: return
 * }
 */
public class SingleDemo {

    // 饿汉式：类一加载就创建好对象,不存在线程安全问题
//    public static SingleDemo singleDemo = new SingleDemo();
//
//    private SingleDemo() {}
//
//    public static SingleDemo getInstance() {
//        return singleDemo;
//    }

    // 懒汉式：要用的时候再创建对象(延迟加载)
    public static volatile SingleDemo singleDemo;

    private SingleDemo() {}

    public static SingleDemo getInstance() {
        // 效率问题：先判断对象是否存在,有就直接返回,不然每次进来都要上锁
        if (singleDemo == null) {
            // 线程安全问题：CPU是逐条执行代码,线程执行具有随机性,谁抢到CPU谁执行,A和B都进来了,判断为空岂不是都要创建对象？所以要加锁
            synchronized (SingleDemo.class) {
                // A创建对象后释放锁,B拿到锁进来发现对象已存在,就不会重复初始化
                if (singleDemo == null) {
                    singleDemo = new SingleDemo();
                }
            }
        }
        return singleDemo;
    }
}