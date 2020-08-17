package string;

public class StringDemo02 {
    public static void main(String[] args) {
        /**
         * 字符串常用功能:
         * 
         * 字节数组、字符数组、字符串数组--->字符串:new String(...)或者遍历+StringBuilder
         * 字符串--->字符串数组:split(String regex)
         * 字符串--->字节数组:getBytes()
         * 字符串--->字符数组:toCharArray()
         */
        
//         String s = "abcdaefg";
//         // 1、获取
//         System.out.println(s.length());
//         System.out.println(s.charAt(2));
//         System.out.println(s.indexOf('e'));
//         // 如果index=-1表示该字符不存在
//         System.out.println(s.indexOf('k'));
//         // 返回指定字符第一次出现的索引,以指定的索引开始搜索
//         System.out.println(s.indexOf('a', 5));
//         System.out.println(s.lastIndexOf('a'));
//         // 字符串截取,包头不包尾
//         System.out.println(s.substring(1, 3));
//         // 从指定索引位置截到末尾
//         System.out.println(s.substring(1));
        
        // 2、转换
        String s1 = "ab,cd,ef";
        
        // 将字符串切割成字符串数组
//         String[] arr = s1.split(",");
//         for (int i = 0; i < arr.length; i++) {
//             System.out.println(arr[i]);
//         }
        
        // 将字符串转换成字符数组 
        char[] arr1 = s1.toCharArray();
        System.out.println(arr1);
        for (int i = 0; i < arr1.length; i++) {
            System.out.println(arr1[i]);
        }
        
        // 将字符串转换成字节数组
//         byte[] arr2 = s1.getBytes();
//         for (int i = 0; i < arr2.length; i++) {
//             System.out.println(arr2[i]);
//         }
        
//         // 大小写转换
//         System.out.println(s1.toUpperCase());
//         // 字符替换
//         String s2 = s1.replace('a', 'o');
//         System.out.println(s1==s1.replace('a', 'o'));
//         // 去空格
//         System.out.println(" a b c ".trim());
        
//         // 3、判断
//         String s2 = "abcdefg";
//         // equals方法
//         System.out.println(s2.equals("ABCDEFG"));
//         System.out.println(s2.equalsIgnoreCase("ABCDEFG"));
//         // contains方法
//         System.out.println(s2.contains("cd"));
//         // 判断开头和结尾元素
//         System.out.println(s2.startsWith("abc"));
//         System.out.println(s2.endsWith("efg"));
//         
//         // 4、比较
//         String s3 = "adc";
//         String s4 = new String("hehe");
//         // 按字典顺序比较大小,返回一个整数,如果equals方法返回true,那么compareTo方法返回0
//         System.out.println(s3.compareTo("abc"));
//         // intern方法：返回字符串对象的规范化表示,如果池中有就返回,没有就添加到池中
//         System.out.println(s3==s3.intern());     // true
//         System.out.println(s4==s4.intern());     // false
    }
}
