package com.okccc.j2se;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * @Author: okccc
 * @Date: 2021/11/12 下午2:16
 * @Desc: java异常及处理机制
 */
public class ExceptionDemo {
    /**
     * 异常体系结构
     * java.lang.Throwable
     *     |---- java.lang.Error: java虚拟机无法解决的严重问题,比如jvm系统内部错误、资源耗尽等严重情况,一般不编写针对性的代码进行处理
     * 	   |---- java.lang.Exception: 可以进行异常的处理
     *         |---- 编译时异常(checked)必须处理,不然编译不通过
     * 		       |---- IOException
     * 			       |---- FileNotFoundException
     * 			   |---- ClassNotFoundException
     *         |---- 运行时异常(unchecked, RuntimeException)
     *             |---- NullPointerException
     *             |---- ArrayIndexOutOfBoundsException
     *             |---- ClassCastException
     *             |---- NumberFormatException
     *             |---- InputMismatchException
     *             |---- ArithmeticException
     *
     * 异常处理机制
     * "抛"：程序执行过程中出现异常时会生成对应的异常类对象并将此对象抛出,后面代码不会执行,异常对象可以是系统自动生成也可以是手动生成并throw
     * "抓"：通过try-catch或者throws处理异常,继续执行后面代码
     * 1.捕获异常
     * try {
     *     // 可能出现异常的代码,子类重写父类方法时,父类有异常子类必须抛出相同异常或其子类,父类没有异常子类也不能抛异常只能try-catch
     * } catch (异常类型1 变量名1) {
     *     // 处理异常1,catch语句匹配到异常类型并处理后,就会跳出当前try-catch结构继续执行后面代码
     * } catch (异常类型2 变量名2) {
     *     // 处理异常2,多个catch语句的异常类型如果有子父类关系子类必须在前面,不然匹配不到
     * } finally {
     *     // 无论是否异常最终都会执行的代码,比如数据库连接、输入输出流、网络编程socket这些资源jvm是无法回收的,需要手动释放
     * }
     *
     * 2.抛出异常
     * throws将程序可能出现的异常类型写在方法声明处,执行方法出现该异常时就会被抛出,后面代码不会执行
     * try-catch真正将异常处理掉了,throws是将异常抛给调用者处理,调用者可以继续往上抛给main方法,再不处理就会抛给jvm,在控制台打印错误堆栈日志
     * 比如开发MVC框架时,一般Dao层会往上抛给Service层,Service层再往上抛给View层,View是展示层直接面向用户不能再抛了必须得try catch处理掉
     *
     * throw和throws区别
     * throw是手动生成异常,后面跟异常对象,定义在方法体中(不常用)
     * throws是异常处理的第二种方式,后面跟异常类型,定义在方法声明中
     */

    private static final Logger logger = LoggerFactory.getLogger(ExceptionDemo.class);

    public static void main(String[] args) throws UnsupportedEncodingException {

//        main(args);  // 栈溢出：java.lang.StackOverflowError
//        Integer[] arr = new Integer[1024 * 1024 * 1024];  // 堆溢出：java.lang.OutOfMemoryError: Java heap space

        Integer a = 10;
        Integer b = 0;
        try {
            int i = a / b;
        } catch (ArithmeticException e) {
            e.printStackTrace();  // 在控制台打印错误堆栈信息,本地调试用
//            logger.error("error", e);  // 线上代码还是要记录下日志信息,方便排错
        } finally {
            System.out.println("aaa");
        }
        // try-catch捕获异常后下面代码继续执行
        System.out.println("bbb");

        String str = "%22os_name%22%3A%22android%22";
        System.out.println(URLDecoder.decode(str, "sb"));  // java.io.UnsupportedEncodingException: sb
        // throws抛出异常后下面代码不会执行
        System.out.println("ccc");
    }

}
