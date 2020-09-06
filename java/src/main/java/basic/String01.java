package basic;

import java.util.Arrays;

public class String01 {
    public static void main(String[] args) {
        /*
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
         */

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

        // 字符串 -> 字节(符)数组
        String s9 = "abcdef";
        byte[] bytes = s9.getBytes();
        System.out.println(Arrays.toString(bytes));  // [97, 98, 99, 100, 101, 102]
        // 字节(符)数组 -> 字符串
        byte[] arr = {65, 66, 97, 98, 99};
        String s7 = new String(arr);
        System.out.println(s7);  // ABabc

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
}
