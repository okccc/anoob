package collection;

import java.util.Comparator;

public class ComparatorTest01 implements Comparator{
    public static void main(String[] args) {
        /**
         * 自定义比较器实现Comparator接口
         */
    }

    public int compare(Object o1, Object o2) {
        
        //     向下转型 
        Person p1 = (Person)o1;
        Person p2 = (Person)o2;
        //     按名字排序,名字相同再按年龄排
        int temp = p1.getName().compareTo(p2.getName());
        return temp==0?p1.getAge() - p2.getAge():temp;
        
    }
}
