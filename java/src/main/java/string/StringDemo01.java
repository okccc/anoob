package string;

public class StringDemo01 {
    public static void main(String[] args) {
        /**
         * String类特点：字符串对象一旦初始化就无法改变,因为String类被final修饰
         * String(byte[] buf, int offset, int length)
         * String(char[] buf, int offset, int length)
         * ......
         */
        
//         String s1 = new String();
//         String s2 = "";
//         String s3 = null;
//         System.out.println(s1.equals(s2));
//         System.out.println(s1.equals(s3));

//         // String对象不可变
//         String s = "abc";
//         s = "bcd";
//         System.out.println(s);
//         s.replace('b', 'o');
//         System.out.println(s);
//         s = s.replace('b', 'o');
//         System.out.println(s);
        
//         // 字符串常量池
//         String s1 = "abc";                      // 在字符串常量池创建一个字符串对象
//         String s2 = new String("abc");          // 在堆内存创建两个对象,new和abc
//         String s3 = "abc";
//         System.out.println(s1==s2);             // 比较地址值
//         System.out.println(s1.equals(s2));      // String类重写了Object类的equals方法,这里是比较内容
//         System.out.println(s1==s3);             // 字符串常量池特点：池中没有就创建,有就直接用
        
        // 通过String类构造函数,将byte数组或char数组转换成字符串
        byte[] arr = {65,66,97,98,99};
        String s1 = new String(arr);
        String s2 = new String(arr,1,3);
        System.out.println("s1="+s1);   // s1=ABabc
        System.out.println("s2="+s2);   // s1=Bab
        
        char[] arr1 = {'a','w','m','q','s'};
        String s3 = new String(arr1);
        String s4 = new String(arr1,2,3);
        System.out.println("s3="+s3);      // s3=awmqs
        System.out.println("s4="+s4);      // s4=mqs
    }
}
