package collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class CollectionDemo {
    public static void main(String[] args) {
        /**
         * 集合:对象用于封装特有数据,对象多了需要存储,个数不确定时要用集合容器存储
         * 特点:1、存储对象的容器
         *     2、长度可变
         *     3、不能存储基本数据类型
         *     集合因为内部数据结构不同,有多种容器,不断向上抽取形成集合框架,Collection是顶层接口
         *     
         * iterator():迭代器
         * 注意:迭代器对象依赖于具体容器,因为每个容器数据结构不同,该迭代器对象是在容器内部实现的
         *     Iterator接口是对所有Collection容器进行元素获取的公共接口
         *     
         * Collection集合如何选择？
         * 是否要唯一 ？
         * 是--->Set
         *       是否要排序？
         *       是--->TreeSet
         *       否-->-HashSet
         *       想要存取顺序一致(有序)--->LinkedHashSet
         * 否--->List
         *       增删多--->LinkedList
         *       查询多--->ArrayList
         * 
         * List
         *     |--ArrayList
         *     |--LinkedList
         * Set
         *     |--HashSet
         *     |--TreeSet
         * 前缀名表示数据结构,后缀名表示所属体系
         * 
         * Array:数组,查询快,有索引
         * Link:链表,增删快,add、getFirst、removeFirst
         * Hash:哈希表,唯一性,元素覆盖hashCode()和equals()方法
         * Tree:二叉树,可排序,两个接口Comparable和Comparator
         * 
         * 上述集合容器都是不同步的
         */
        
        Collection col = new ArrayList();
        //     添加
        col.add("aaa");
        col.add("bbb");
        col.add("ccc");
        System.out.println(col);
        //     长度
        System.out.println(col.size());
        //     判断是否为空
        System.out.println(col.isEmpty());
        //     删除
        col.remove("aaa");
        System.out.println(col);
        //     包含
        System.out.println(col.contains("bbb"));
        //     清空
        col.clear();
        System.out.println(col);
        
        Collection c1 = new ArrayList();
        Collection c2 = new ArrayList();
        c1.add("aaa");
        c1.add("bbb");
        c1.add("ccc");
        c2.add("bbb");
        c2.add("ddd");
        c2.add("eee");
        //     addAll
        c1.addAll(c2);
        System.out.println(c1);
        //     removeAll(在集合c1中将两者的交集删掉)
        System.out.println(c1.removeAll(c2));
        //     containsAll
        System.out.println(c1.containsAll(c2));
        //     retainAll(取两者交集)
        System.out.println(c1.retainAll(c2));
        
        //     Iterator迭代器
        Collection coll = new ArrayList();
        coll.add("abc");
        coll.add("bcd");
        coll.add("cde");
        coll.add("efg");
        //     while循环
        Iterator iterator = coll.iterator();
        while(iterator.hasNext()){
            System.out.println(iterator.next());
        }
        //     for循环
        for(Iterator iterator2 = coll.iterator();iterator2.hasNext();){
            System.out.println(iterator2.next());
        }
    }
}
