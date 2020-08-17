package collection;

import java.util.LinkedList;
import java.util.ListIterator;

public class LinkedListDemo {
    public static void main(String[] args) {
        /**
         * LinkedList集合(不常用)
         */
        LinkedList list = new LinkedList();
        //     添加
        list.add("aaa");
        list.addFirst("bbb");
        list.addLast("ccc");
        System.out.println(list);
        
        //     获取元素(不删除)
        System.out.println(list.getFirst());
        System.out.println(list);
        //     获取元素(然后删除)
        System.out.println(list.removeFirst());
        System.out.println(list);
     
        /**
         * LinkedList三种遍历方式
         */
        ListIterator it = list.listIterator();
        while(it.hasNext()){
            System.out.println(it.next());
        }
        
        for(int x=0;x<list.size();x++){
            System.out.println(list.removeFirst());
        }
        
        while(!list.isEmpty()){
            System.out.println(list.removeFirst());
        }
    }
}
