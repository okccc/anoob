package collection;

import java.util.Iterator;
import java.util.LinkedHashSet;

public class LinkedHashSetDemo {
    public static void main(String[] args) {
        /**
         * LinkedHashSet:在set接口哈希表的基础上添加了链表结构,所以在保证唯一性的同时还能有序
         */
        LinkedHashSet lhs = new LinkedHashSet();
        lhs.add("aaa");
        lhs.add("bbb");
        lhs.add("ccc");
        lhs.add("aaa");
        System.out.println(lhs);
        
        //     迭代器循环遍历
        Iterator it = lhs.iterator();
        while(it.hasNext()){
            System.out.println(it.next());
        }
    }
}
