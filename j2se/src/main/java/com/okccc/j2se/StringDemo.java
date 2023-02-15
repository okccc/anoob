package com.okccc.j2se;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: okccc
 * @Date: 2020/7/14 20:06
 * @Desc: java字符串
 *
 * String
 * 字符串是不可变的,因为String类被final修饰
 * 字符串使用很频繁,为了避免产生大量String对象,java设计了字符串常量池,池中没有就创建,有就直接用
 * 两种创建对象方式
 * String s1 = "abc";
 * jvm会先检查字符串常量池有没有"abc",有就直接返回引用地址,没有就在常量池开辟一块内存空间存放"abc",栈中的s1指向的是常量池的对象
 * String s2 = new String("abc");
 * jvm会先在堆开辟一块内存空间创建空字符串,然后检查字符串常量池有没有"abc",有就直接将空字符串指向它,没有就在常量池开辟一块内存空间
 * 存放"abc"并将堆的空字符串指向它,栈中的s2指向的是堆的对象,其实是创建了两个对象,一个在堆一个在常量池,通常使用方式一性能更好
 * ==比较地址值,equals比较内容
 *
 * Integer
 * 为了方便操作基本数据类型的值,将其包装成类,在类中定义属性和行为
 * byte/short/int/long/float/double/char/boolean
 * Byte/Short/Integer/Long/Float/Double/Character/Boolean,使用方式和String类类似
 * jdk1.5自动拆装箱
 * Integer i = 10  // 将基本类型变量用包装类接收会自动装箱,调用了Integer.valueOf()方法
 * int a = i | int b = i/10 | if(i>10)  // 将包装类赋值给基本类型、运算、比较时都会自动拆箱
 *
 * 三大容器：数组、集合、字符串缓冲区
 * 字符串缓冲区：长度可变、可存储不同数据类型、最终转成字符串使用
 * StringBuffer(jdk1.0)线程安全(synchronized)
 * StringBuilder(jdk1.5)线程不安全,效率高
 * 区别：String存放字符串常量,StringBuilder存放字符串变量
 *
 * 正则表达式操作字符串：匹配、查找、替换
 * * : 零次或多次,等同于{0,}
 * ? : 零次或一次,等同于{0,1}
 * + : 一次或多次,等同于{1,}
 * ^ : 字符串开头,如果在[]内表示取反
 * $ : 字符串结尾
 * . : 匹配除\n以外的任意单个字符
 * [] : 匹配内容
 * {} : 限定次数  {n}刚好n次  {n,}至少n次  {n,m}至少n次至多m次
 * () : 子表达式  组: ((A)(B(C))) \1表示匹配到的第一个子串,\2表示匹配到的第二个子串
 * \ : 转义下一个字符,在字符串里要写双斜杠\\
 *
 * \d : 匹配任意数字,等同于[0-9]
 * \D : 匹配任意非数字,等同于[^0-9]
 * \w : 匹配任意字符,等同于[a-zA-Z0-9_]
 * \W : 匹配任意非字符,等同于[^a-zA-Z0-9_]
 * \s : 匹配任意空白字符,等同于[\t\n\r\f]
 * \S : 匹配任意非空字符,等同于[^\t\n\r\f]
 * \b : 匹配任意边界,例如：er\b与never中er匹配,但与verb中er不匹配
 * \B : 匹配任意非边界
 *
 * 在线正则表达式: http://tool.oschina.net/regex/
 * 参考文档：http://www.runoob.com/python3/python3-reg-expressions.html
 */
public class StringDemo {

    public static void string() {
        // 创建字符串
        String s1 = "abc";
        String s2 = "abc";
        String s3 = new String("abc");
        System.out.println(s1==s2);  // true
        System.out.println(s1.equals(s2));  // true
        System.out.println(s1==s3);  // false
        System.out.println(s1.equals(s3));  // true
        // intern()方法返回字符串对象的规范表示,指向的是字符串常量池
        System.out.println(s1==s3.intern());  // true
        // 字符串相加
        String str1 = "str";
        String str2 = "ing";
        String str3 = "str" + "ing";
        String str4 = str1 + str2;
        String str5 = "string";
        // 字符串如果是常量相加,先拼接得到新的常量,然后在常量池中找,有就直接返回没有就创建
        System.out.println(str3==str5);  // true
        // 字符串如果是变量相加,会在堆中创建新的对象
        System.out.println(str4==str5);  // false

        // 字符串 -> 字节(符)数组
        String s9 = "abcdef";
        byte[] bytes = s9.getBytes();
        System.out.println(Arrays.toString(bytes));  // [97, 98, 99, 100, 101, 102]
        // 字节(符)数组 -> 字符串
        byte[] arr = {65, 66, 97, 98, 99};
        String s7 = new String(arr);
        System.out.println(s7);  // ABabc
    }

    public static void integer() {
        // 自动装箱调用的是Integer类的valueOf()方法,查看源码发现其内部类IntegerCache缓存了[-128,127]范围的值,超出该范围才会new Integer()创建对象
        Integer i1 = 127;
        Integer i2 = 127;
        System.out.println(i1==i2);  // true
        Integer i3 = 128;
        Integer i4 = 128;
        System.out.println(i3==i4);  // false
        int i5 = 127;
        int i6 = 128;
        // 当和int类型的值做比较时都会转换成int类型,此时就是单纯比较数值大小而不是地址值
        System.out.println(i1==i5);  // true
        System.out.println(i3==i6);  // true

        // 字符串 -> 基本类型
        System.out.println(Integer.parseInt("123"));
        // 基本类型 -> 字符串
        String s = Integer.toString(123);

        // 最大值最小值
        System.out.println(Integer.MIN_VALUE);
        System.out.println(Integer.MAX_VALUE);
        // 十进制 -> 二进制
        System.out.println(Integer.toBinaryString(8));
        // 十进制 -> 八进制
        System.out.println(Integer.toOctalString(8));
        // 十进制 -> 十六进制
        System.out.println(Integer.toHexString(8));
        // 其他进制 -> 十进制
        System.out.println(Integer.parseInt("3c", 16));
    }

    public static void stringBuffer() {
        // 将int数组转换成String数组
        int[] arr1 = {11, 22, 33, 44, 55};
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i : arr1) {
            if (i != arr1[arr1.length-1]) {
                sb.append(i).append(", ");
            } else {
                sb.append(i).append("]");
            }
        }
        System.out.println(sb);
        System.out.println(sb.toString());
    }

    private static void regex() {
        // 将正则封装成对象
        Pattern p1 = Pattern.compile("1[3,5,8]\\d{9}");
        Pattern p2 = Pattern.compile("([a-z]+) ([a-z]+)");
        // 匹配字符串获取匹配器
//        Matcher m = p1.matcher("32893219731923712");
        Matcher m = p2.matcher("hello java hello python");
        while (m.find()){
            // 获取匹配的子序列
            System.out.println(m.group());
            // 获取初始索引和结尾索引
            System.out.println(m.start() +"~"+ m.end());
        }
        // 屏蔽电话号码中间四位数
        String str = "13818427154";
        str = str.replaceAll("(\\d{3})\\d{4}(\\d{3})", "$1****$2");
        System.out.println(str);  // 138****7154
    }

    public static void main(String[] args) {
//        string();
//        integer();
//        stringBuffer();
        regex();
    }
}