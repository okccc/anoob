package map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class CollectionsDemo {
    public static void main(String[] args) {
        /**
         * Collections:集合框架工具类,没有构造函数,无法创建对象,都是静态方法
         */
        
        List<String> list = new ArrayList<String>();
        list.add("abc");
        list.add("haha");
        list.add("bcd");
        list.add("zzz");
        list.add("swqdf");
        System.out.println(list);
        
        // 1、sort
        // 根据元素自然顺序排序
        Collections.sort(list);
        System.out.println(list);
        // 根据自定义比较器顺序排序
        Collections.sort(list, new ComparatorByLength());
        System.out.println(list);
        
        // 2、search
        int index = Collections.binarySearch(list, "zzz");
        System.out.println(index);
        
        // 3、max
        String max = Collections.max(list);
        String max2 = Collections.max(list,new ComparatorByLength());
        System.out.println(max+"..."+max2);
        
        // 4、reverse
        Collections.reverse(list);
        System.out.println(list);
        
        // 强制反转已经实现了Comparable或者Comparator接口的对象顺序
//         Set<String> set = new TreeSet<String>(Collections.reverseOrder());
        Set<String> set = new TreeSet<String>(Collections.reverseOrder(new ComparatorByLength()));
        set.add("abc");
        set.add("haha");
        set.add("bcd");
        set.add("zzz");
        set.add("swqdf");
        System.out.println(set);
        
        // 5、shuffle
        Collections.shuffle(list);
        System.out.println(list);
        
        // 6、fill
        Collections.fill(list, "ccc");
        System.out.println(list);
        
    }
}
