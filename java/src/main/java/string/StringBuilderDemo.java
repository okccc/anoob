package string;

public class StringBuilderDemo {
    public static void main(String[] args) {
        /**
         * 考虑到字符串缓冲区可能会被多线程操作：
         * StringBuffer(jdk1.0)是线程同步的,安全,通常用于多线程
         * StringBuilder(jdk1.5)是线程不同步的,不安全,通常用于单线程,高效(不用每次操作都判断锁)           
         * 
         * JDK升级:1、简化书写
         *        2、提高效率
         *        3、提高安全性
         */
        
        // 需求：将int数组变成String数组
        int[] arr = {12,23,34,45,56};
        String s1 = arrayToString(arr);
        System.out.println(s1);
        String s2 = arrayToString2(arr);
        System.out.println(s2);
       
    }
    
    // 方法一：直接拼接字符串
    private static String arrayToString(int[] arr) {
        String str = "[";
        for (int i = 0; i < arr.length; i++) {
            // 这里每次都会在字符串常量池创建一个新的字符串常量,而sb是在字符串缓冲区里操作,最后返回一个完整字符串,所以用sb比较好
            if(i!=arr.length-1){
                str = str + arr[i] + ", ";
            }else{
                str = str + arr[i] + "]";
            }
        }
        return str;
    }

    // 方法二：往字符串缓冲区添加数组元素,最后以字符串返回 
    private static String arrayToString2(int[] arr) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < arr.length; i++) {
            if(i!=arr.length-1){
                sb.append(arr[i]+", ");
            }else{
                sb.append(arr[i]+"]");
            }
        }
        return sb.toString();
    }

    
}
