package generic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class GenericDemo05 {
    public static void main(String[] args) {
        /**
         * 泛型限定:? extends E:接收E类型或E类型的子类(E是上限)
         *        ? super E:接收E类型或E类型的父类(E是下限)
         */
        
        ArrayList<Person> a1 = new ArrayList<Person>();
        a1.add(new Person("grubby",18));
        a1.add(new Person("fly",17));
        
        ArrayList<Student> a2 = new ArrayList<Student>();
        a2.add(new Student("moon",19));
        a2.add(new Student("sky",20));
        
        ArrayList<String> a3 = new ArrayList<String>();
        a3.add("haha");
        a3.add("hehe");
        
        printCollection(a1);
        printCollection(a2);
        //   printCollection(a3);(编译报错)
        printCollection2(a1);
        printCollection2(a2);
        //   printCollection2(a3);(编译报错)
        
        ArrayList<Worker> a4 = new ArrayList<Worker>();
        a4.add(new Worker("infi",19));
        a4.add(new Worker("thooo",19));
        
        //   存元素要用上限(Person类)存,不会出现安全隐患
        a1.addAll(a2);
        //   a1.addAll(a3);(编译报错)
        a1.addAll(a4);
        //   a2.addAll(a1);(编译报错)
        //   a2.addAll(a4);(编译报错)
        //   a4.addAll(a1);(编译报错)
        //   a4.addAll(a2);(编译报错)
        System.out.println(a1);
    }

    //   ? extends E
    public static void printCollection(Collection<? extends Person> coll) {
        Iterator<? extends Person> it = coll.iterator();
        while(it.hasNext()){
            System.out.println(it.next());
        }
    }
    
    //   ? super E
    public static void printCollection2(Collection<? super Student> coll) {
        Iterator<? super Student> it2 = coll.iterator();
        while(it2.hasNext()){
            System.out.println(it2.next());
        }
    }
    
    
    
    
}
