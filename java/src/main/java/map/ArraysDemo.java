package map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public class ArraysDemo {
    public static void main(String[] args) {
        /**
         * Arrays:集合框架工具类,没有构造函数,无法创建对象,都是静态方法
         * 
         * 数组转集合:asList()方法
         * 
         * 好处:1、数组本身功能有限,转成集合可以使用更多功能
         *     2、因为数组本身长度固定,所以集合的增删方法这里不能用
         *     3、如果数组中元素是基本数据类型,那么会将整个数组作为集合的元素存储；如果数组中元素是对象,那么会将数组中的元素作为集合中的元素存储
         */           
        
//         int[] arr = {12,23,34,45};
//         List<int[]> list = Arrays.asList(arr);
//         System.out.println(list);
//         System.out.println(list.size());
//         
//         String[] arr2 = {"aaa","bbb","ccc"};
//         List<String> list2 = Arrays.asList(arr2);
//         boolean b = list2.contains("bbb");
//         System.out.println(b);
//         // java.lang.UnsupportedOperationException
//         list2.add("haha");
//         System.out.println(list2);
        
        /**
         * 集合转数组:toArray(这里传入指定类型的数组)方法,可以对集合中元素的操作方法进行限定
         */
        List<String> list = new ArrayList<String>();
        list.add("abc");
        list.add("haha");
        list.add("bcd");
        list.add("zzz");
        list.add("swqdf");
        System.out.println(list);
        
        String[] str_arr = list.toArray(new String[list.size()]);
        System.out.println(Arrays.toString(str_arr));
        System.out.println(str_arr);
        System.out.println(str_arr[0]);
    }
}
