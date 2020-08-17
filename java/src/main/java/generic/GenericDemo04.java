package generic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Iterator;

public class GenericDemo04 {
    public static void main(String[] args) {
        /**
         * 需求:如果有多个不同类型的集合需要迭代,为了提高代码复用率,可以考虑用泛型将iterator方法封装
         */
        
        ArrayList<Integer> a = new ArrayList<Integer>();
        a.add(23);
        a.add(34);
        a.add(45);
        
        HashSet<String> hs = new HashSet<String>();
        hs.add("aaa");
        hs.add("bbb");
        hs.add("ccc");
        hs.add("aaa");
        
        TreeSet<Person> ts = new TreeSet<Person>();
        ts.add(new Person("grubby",18));
        ts.add(new Person("moon",19));
        ts.add(new Person("fly",19));
        ts.add(new Person("grubby",18));
        
        printCollection(a);
        printCollection(hs);
        printCollection(ts);
    }

    //   List和Set都属于Collection,可以向上抽取用Collection<?>表示
    public static void printCollection(Collection<?> coll) {
        Iterator<?> it = coll.iterator();
        while(it.hasNext()){
            System.out.println(it.next().toString());
        }
    }
}
