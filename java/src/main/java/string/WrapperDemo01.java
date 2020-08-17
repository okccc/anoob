package string;

public class WrapperDemo01 {
    public static void main(String[] args) {
        /**
         * 基本数据类型对象包装类：为了方便操作基本数据类型的数值,将其封装成对象,在对象中定义属性和行为丰富该数据操作
         *                   描述该对象的类就是基本数据类型对象包装类
         * byte         Byte
         * short        Short
         * int          Integer
         * long         Long
         * float        Float
         * double       Double
         * char         Character
         * boolean      Boolean
         * 该包装对象主要用于基本类型和字符串的转换
         * 1、基本类型--->字符串
         *    StringBuilder()
         * 2、字符串--->基本类型
         *    parseInt()
         */
        // 最大值最小值
        System.out.println(Integer.MIN_VALUE);
        System.out.println(Integer.MAX_VALUE);
        // 十进制--->二进制
        System.out.println(Integer.toBinaryString(8));
        // 十进制--->八进制
        System.out.println(Integer.toOctalString(8));
        // 十进制--->十六进制
        System.out.println(Integer.toHexString(8));
        System.out.println(Integer.toString(8, 16));
        // 其他进制--->十进制
        System.out.println(Integer.parseInt("3c",16));
        // Integer两个构造函数
        Integer a = new Integer(22);
        Integer b = new Integer("22");
        // Integer的equals方法
        System.out.println(a.equals(b));
        // Integer的compare方法
        System.out.println(a.compareTo(b));
        
        /**
         * JDK1.5后自动拆装箱
         */
        Integer i = 4;// i = new Integer(4);  自动将int封装成Integer,简化书写
        i = i + 5;// i = new Integer(i.intValue() + 5);  自动将i拆成int类型再做运算
        
        Integer m = new Integer(33);
        Integer n= new Integer(33);         
        System.out.println(m==n);          // false
        System.out.println(m.equals(n));   // true
        // JDK1.5自动装箱,如果装箱的是一个字节,那么该数据会被共享而不需要重新开辟空间
        Integer o = 128;
        Integer p = 128;
        Integer x = 127;
        Integer y = 127;
        System.out.println(o==p);           // false
        System.out.println(o.equals(p));    // true
        System.out.println(x==y);           // true
        System.out.println(x.equals(y));    // true
    }
}
