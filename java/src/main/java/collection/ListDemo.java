package collection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class ListDemo {
    public static void main(String[] args) {
        /**
         * List集合:有序,可重复
         * 
         * 1、Vector内部是数组结构,是同步的；增删查询都很慢
         * 2、ArrayList内部是数组结构,不同步,查询快,增删慢
         * 3、LinkedList内部是链表结构,不同步,增删块,查询慢
         */
        List list = new ArrayList();
        //     添加
        list.add("aaa");
        list.add("bbb");
        list.add("ccc");
        System.out.println(list);
//             //     插入
//             list.add(1, "haha");
//             System.out.println(list);
//             //     删除
//             list.remove(3);
//             System.out.println(list);
//             //     修改
//             list.set(2, "xixi");
//             System.out.println(list);
//             //     获取
//             System.out.println(list.get(0));
//             System.out.println(list.size());
//             //     截取(包头不包尾)
//             System.out.println(list.subList(1, 3));
        
        /**
         * list两种遍历循环方式
         */
        //     方式一(list迭代器,list集合特有)
//             Iterator it = list.iterator();
//             while(it.hasNext()){
//             	Object obj = it.next();
//             	if(obj.equals("aaa")){
//             		//     Exception in thread "main" java.util.ConcurrentModificationException(并发修改异常)
//             		//     迭代器是依赖于集合存在的,集合添加元素,迭代器并不知道,所以迭代器循环遍历的时候,不能用集合操作数据,只能用迭代器自身
//             		list.add("haha");
//             	}
//             }
        ListIterator it = list.listIterator();
        while(it.hasNext()){
            Object obj = it.next();
            if(obj.equals("aaa")){
                it.set("hehe");
            }
        }
        //     还可以倒叙遍历
        while(it.hasPrevious()){
            System.out.println(it.previous());
        }
        //     方式二(list特有)
        for(int x=0;x<list.size();x++){
            System.out.println(list.get(x));
        }
    }
}
