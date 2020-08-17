package generic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

public class GenericDemo01 {
    public static void main(String[] args) {
        /**
         * 泛型:jdk1.5出现的安全机制
         * 好处:1、将运行时期的ClassCastException提前转到编译时期,确保类型安全
         *     2、可以避免强制类型转换
         * 使用:当类或接口操作的引用数据类型不确定的时候,就要用到泛型<>,其实就是接收具体引用数据类型的参数范围 
         * 通配符:?表示未知类型
         */     
        
        ArrayList<String> list = new ArrayList<String>();
        list.add("aaa");
        list.add("bbb");
        //   list.add(4);

        for (String s : list) {
            System.out.println(s);
        }
        
        System.out.println("---------我是分割线----------");
        
        TreeSet<Person> ts = new TreeSet<Person>(new ComparatorByName());
        ts.add(new Person("grubby",18));
        ts.add(new Person("moon",19));
        ts.add(new Person("fly",19));
        ts.add(new Person("grubby",18));

        for (Person p : ts) {
            System.out.println(p.getName() + "....." + p.getAge());
        }
    }
    
}
