package com.okccc.j2se;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * @Author: okccc
 * @Date: 2021/11/12 15:22:16
 * @Desc: java异常及处理机制
 *
 * 异常体系结构
 * java.lang.Throwable
 *     |---- java.lang.Error: java虚拟机无法解决的严重问题,比如jvm系统内部错误、资源耗尽等严重情况,一般不编写针对性的代码进行处理
 * 	   |---- java.lang.Exception: 可以处理的异常
 *         |---- 编译时异常(checked)必须处理不然编译不通过,尽量捕获不要抛给上层
 *             |---- SQLException
 * 		       |---- IOException
 * 			       |---- FileNotFoundException
 * 			   |---- ClassNotFoundException
 *         |---- 运行时异常(unchecked, RuntimeException),可以抛给调用者尽可能晚的处理异常
 *             |---- NullPointerException
 *             |---- ArrayIndexOutOfBoundsException
 *             |---- ClassCastException
 *             |---- NumberFormatException
 *             |---- InputMismatchException
 *             |---- ArithmeticException
 *
 * 捕获异常,代码继续执行
 * try {
 *     // 可能出现异常的代码,子类重写父类方法时,父类有异常子类必须抛出相同异常或其子类,父类没有异常子类也不能抛异常只能try-catch
 * } catch (异常类型1 变量名1) {
 *     // 处理异常1,catch语句匹配到异常类型并处理后,就会跳出当前try-catch结构继续执行后面代码
 * } catch (异常类型2 变量名2) {
 *     // 处理异常2,多个catch语句的异常类型如果有子父类关系子类必须在前面,不然匹配不到
 * } finally {
 *     // 无论是否异常最终都会执行的代码,比如数据库连接、输入输出流、网络编程socket这些资源jvm无法回收,需要手动释放
 * }
 *
 * 抛出异常,程序终止
 * throws是在方法声明处,后面跟异常类型,将异常抛给调用者处理,调用者可以继续往上抛给main方法,再不处理就会抛给jvm在控制台打印错误堆栈日志
 * 比如开发MVC框架时,Mapper -> Service -> Controller逐层往上抛,视图直接面向用户不能再抛了必须try catch处理
 * throw是在方法体中,后面跟异常对象,两者作用是一样的,都是为了终止程序
 *
 * 总结：异常捕获或抛出取决于当前程序的设计需求,如果想跳出当前异常继续往下执行就捕获,如果想在更高层对异常集中处理就抛出
 */
public class ExceptionDemo {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionDemo.class);

    public static void main(String[] args) throws UnsupportedEncodingException {

//        main(args);  // 栈溢出：java.lang.StackOverflowError
//        Integer[] arr = new Integer[1024 * 1024 * 1024];  // 堆溢出：java.lang.OutOfMemoryError: Requested array size exceeds VM limit

        int a = 10;
        int b = 0;
        try {
            int i = a / b;
        } catch (ArithmeticException e) {
//            e.printStackTrace();  // 在控制台打印错误堆栈信息,本地调试用
//            logger.error("error", e);  // 线上代码还是要记录下日志信息,方便排错
            throw new ArithmeticException(e.getMessage());
        } finally {
            // finally代码块无论如何都会执行
            System.out.println("aaa");
        }

        // try-catch捕获异常后下面代码继续执行,但如果throw手动抛出异常则程序终止
        System.out.println("bbb");

        // throws抛出异常后下面代码不会执行
        URLDecoder.decode("%22os_name%22%3A%22android%22", "sb");
        System.out.println("ccc");
    }

    public static void testTryCatch() {
        long start = System.nanoTime();
        int a = 0;
        for (int i = 0; i < 1000000; i++) {
            a++;
        }
        long end1 = System.nanoTime();
        System.out.println(end1 - start);  // 没有try catch耗时2ms

        for (int i = 0; i < 1000000; i++) {
            try {
                a++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        long end2 = System.nanoTime();
        System.out.println(end2 - end1);  // 有try catch但不抛异常耗时2ms,对性能几乎没有影响

        for (int i = 0; i < 1000000; i++) {
            try {
                a++;
//                int num = 10 / 0;
                throw new Exception();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        long end3 = System.nanoTime();
        System.out.println(end3 - end2);  // 有try catch且抛出异常耗时5200毫秒,性能差了几千倍
    }
}
