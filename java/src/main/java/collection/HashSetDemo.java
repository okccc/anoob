package collection;

import java.util.HashSet;
import java.util.Iterator;

public class HashSetDemo {
    public static void main(String[] args) {
        /**
         * Set集合:无序,唯一
         * Set接口的方法和Collection接口保持一致
         * Set集合如何保证唯一性:HashSet集合的数据结构是哈希表,存储元素的时候先判断元素的hashcode()值是否相同,不同就直接添加；
         *                 相同的话再继续判断元素的equals()方法,最终确定元素是否重复
         */
        HashSet hs = new HashSet();
//             hs.add("aaa");
//             hs.add("bbb");
//             hs.add("ccc");
//             hs.add("bbb");
//             hs.add("hehe");
//             hs.add("xixi");
//             hs.add("haha");
//             System.out.println(hs);
//             Iterator it = hs.iterator();
//             while(it.hasNext()){
//                 System.out.println(it.next());
//             }
        
        Person p1 = new Person("grubby",18);
        Person p2 = new Person("sky",18);
        Person p3 = new Person("moon",19);
        Person p4 = new Person("grubby",18);
        hs.add(p1);
        hs.add(p2);
        hs.add(p3);
        hs.add(p4);
        //     System.out.println(hs);
        
        //     迭代器遍历循环
        Iterator it = hs.iterator();
        while(it.hasNext()){
        	Person p = (Person) it.next();
        	//     System.out.println(p.getName()+"....."+p.getAge());
        }
    }
}
