package com.okccc.j2se;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@SuppressWarnings("unused")
public class FunctionDemo {
    public static void main(String[] args) throws ParseException {
        /*
         * public：权限必须是最大的
         * static：JVM调用主函数是不需要对象的,直接用主函数所属类名调用即可
         * void：主函数没有具体返回值
         * main：jvm识别的固定名字
         * String[] args：主函数的参数列表,数组类型的参数,里面的元素都是字符串类型,args是arguments
         *
         * System.out.println(args);           // [Ljava.lang.String;@3781efb9
         * System.out.println(args.length);    // 0
         * System.out.println(args[0]);        // java.lang.ArrayIndexOutOfBoundsException: 0
         *
         * break：跳出当前循环
         * continue：跳出本次循环,继续下次循环
         * return：退出当前执行的函数,如果是main函数就退出整个程序
         * do while和while的区别在于不管条件是否满足,do while循环体至少执行一次
         */

//        rectangle();
//        triangle01();
//        triangle02();
        printCFB();
        dateDiff();
        System.out.println(fibonacci(15));
    }

    // 求和
    public static int add(int a, int b) {return a + b;}
    public static double add(double a, double b) {return a + b;}
    public static int add(int a, int b, int c) {return a + b +c;}
    // 判断是否相等
    public static boolean equal(int a, int b) {return a==b;}
    // 比较大小
    public static int getMax(int a, int b) {return Math.max(a, b);}

    // 根据考试成绩获取学生分数对应的等级
    public static char getLevel(int num){
        char res;
        if(num >= 90 && num <= 100){
            res = 'A';
        }else if(num >= 60 && num <= 89){
            res = 'B';
        }else{
            res = 'C';
        }
        return res;
    }
    
    // 画矩形
    public static void rectangle(){
        // 外循环控制行数
        for(int x = 1; x <= 4; x++){
            // 内循环控制每行列数
            for(int y = 1; y <= 5; y++){
                System.out.print("*");
            }
            // 打印完一行换到下一行
            System.out.println();
        }
    }

    // 画正三角形
    public static void triangle01(){
        for(int x = 1; x <= 4; x++){
            for (int y = 1; y <= x; y++){
                System.out.print("*");
            }
            System.out.println();
        }
    }

    // 画倒三角形
    public static void triangle02(){
        for(int x = 1; x <= 4; x++){
            for (int y = x; y <= 4; y++){
                System.out.print("*");
            }
            System.out.println();
        }
    }

    // 打印乘法表
    public static void printCFB(){
        for(int x = 1; x <= 9; x++){
            for(int y = 1; y <= x; y++){
                System.out.print(y + "*" + x + "=" + y * x + "\t");
            }
            System.out.println();
        }
    }
    
    // 计算日期差值
    public static void dateDiff() throws ParseException {
        String str1 = "2020-09-07";
        String str2 = "2020-03-25";
        // 获取日期格式器
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        // 解析字符串
        final Date date1 = sdf.parse(str1);
        final Date date2 = sdf.parse(str2);
        System.out.println(date1 +" | "+ date2);  // Mon Sep 07 00:00:00 CST 2020 | Wed Mar 25 00:00:00 CST 2020
        // 转换成毫秒值,只有毫秒值可以加减乘除
        final long time1 = date1.getTime();
        final long time2 = date2.getTime();
        System.out.println(time1 +" | "+ time2);  // 1599408000000 | 1585065600000
        final long time = Math.abs(time1 - time2);
        System.out.println(time/(1000*60*60*24));
        // 将long类型的时间戳转换成日期
        System.out.println(sdf.format(new Date(time1)));
    }

    public static void calculate() {
        // 向上取整
        System.out.println(Math.ceil(12.33));
        // 向下取整
        System.out.println(Math.floor(12.33));
        // 四舍五入
        System.out.println(Math.round(12.33));
        // a的b次方
        System.out.println(Math.pow(2, 3));
        // 绝对值
        System.out.println(Math.abs(-12));
        // 0~1随机数
        System.out.println(Math.random());
    }

    public static void runtime() throws IOException, InterruptedException {
        // 获取Runtime对象,操纵当前程序运行的环境
        Runtime r = Runtime.getRuntime();
        // exec方法返回进程
        Process proc = r.exec("notepad.exe");
        // 线程休眠
        Thread.sleep(3000);
        // 杀掉进程
        proc.destroy();
    }

    public static int fibonacci(int i) {
        // 斐波那契数列
        if(i == 1 | i == 2) {
            return 1;
        } else {
            return fibonacci(i - 1) + fibonacci(i - 2);
        }
    }

}