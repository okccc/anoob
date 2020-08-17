package collection;

import java.util.Iterator;
import java.util.TreeSet;

public class TreeSetDemo {
    public static void main(String[] args) {
        /**
         * TreeSet:数据结构是二叉树,可以对Set集合的元素进行排序
         *         唯一性:根据比较方法的结果,返回0表示元素相同,不添加
         *         
         * 如何排序的呢？
         * 1、自然排序:让元素本身具备比较功能,实现Comparable接口,覆盖compareTo方法
         * 2、比较器排序:让集合具备比较功能,自定义一个类实现Comparator接口,覆盖compare方法,然后将该类对象作为TreeSet的带参构造传入(较常用)
         */
        TreeSet ts = new TreeSet(new ComparatorTest01());
//             ts.add("aaa");
//             ts.add("ppp");
//             ts.add("mmm");
//             ts.add("bbb");
        Person p1 = new Person("grubby",18);
        Person p2 = new Person("moon",20);
        Person p3 = new Person("grubby",19);
        Person p4 = new Person("fly",19);
        ts.add(p1);
        ts.add(p2);
        ts.add(p3);
        ts.add(p4);
        //     打印排序后的集合
        System.out.println(ts);
        
        Iterator it = ts.iterator();
        while(it.hasNext()){
            Person p = (Person)it.next();
            System.out.println(p.getName()+"....."+p.getAge());
        }
        
        /**
         * 需求：对字符串长度排序
         */
        TreeSet ts1 = new TreeSet(new ComparatorTest02());
        ts1.add("aasdas");
        ts1.add("aaa");
        ts1.add("bb");
        ts1.add("cccc");
        System.out.println(ts1);
        
        Iterator it1 = ts1.iterator();
        while(it1.hasNext()){
            //     String类实现了Comparable接口,这里默认是按字典顺序排序,如果按长度排需要自定义比较器
            System.out.println(it1.next());
        }
    }
}
