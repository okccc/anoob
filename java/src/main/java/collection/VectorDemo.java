package collection;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;

public class VectorDemo {
    public static void main(String[] args) {
        /**
         * Vector集合(很少用)
         */
        Vector v = new Vector();
        v.add("aaa");
        v.add("bbb");
        v.add("ccc");
        System.out.println(v);
        
        /**
         * vector两种遍历方式
         */
        //     方式一(迭代器)
        Iterator it = v.iterator();
        while(it.hasNext()){
            System.out.println(it.next());
        }
        //     方式二(枚举)
        Enumeration elements = v.elements();
        while(elements.hasMoreElements()){
            System.out.println(elements.nextElement());
        }
    }
}
