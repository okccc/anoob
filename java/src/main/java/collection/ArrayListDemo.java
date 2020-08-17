package collection;

import java.util.ArrayList;
import java.util.Iterator;

public class ArrayListDemo {
    public static void main(String[] args) {
        /**
         * ArrayList集合: List集合中最常用的一种
         */
        
        ArrayList list = new ArrayList();
        
        Person p1 = new Person("grubby",18);
        Person p2 = new Person("moon",19);
        Person p3 = new Person("sky",20);
        Person p4 = new Person("grubby",18);
        
        //     添加对象
        list.add(p1);
        list.add(p2);
        list.add(p3);
        System.out.println(list);
        
        //     遍历循环
        Iterator it = list.iterator();
        while(it.hasNext()){
//           System.out.println(((Person) it.next()).getName()+"....."+((Person) it.next()).getAge());//     同一个语句不能写多个next()
            Person p = (Person) it.next();
            System.out.println(p.getName()+"....."+p.getAge());
        }
        
        /**
         * 定义功能去重ArrayList中重复元素
         * 思路：定义一个临时集合temp,遍历当前ArrayList所有元素 ,判断temp中是否包含该元素,没有就添加
         */
        ArrayList a = new ArrayList();
//             a.add("aaa");
//             a.add("bbb");
//             a.add("ccc");
//             a.add("aaa");
//             a.add("ddd");
        a.add(p1);
        a.add(p2);
        a.add(p3);
        a.add(p4);
        System.out.println(a);
        a = removeSameElement(a);
        System.out.println(a);
    }

    private static ArrayList removeSameElement(ArrayList a) {
        //     1、定义一个临时集合temp
        ArrayList temp = new ArrayList();
        //     2、对a进行迭代
        Iterator it = a.iterator();
        while(it.hasNext()){
            //     3、获取每个元素
//                 Object obj = it.next();
            Person p = (Person)it.next();
            //     4、判断temp中是否包含该元素,没有就添加
//                 if(!temp.contains(obj)){
//                     temp.add(obj);
//                 }
            if(!temp.contains(p)){
                temp.add(p);
            }
        }
        return temp;
    }
}
