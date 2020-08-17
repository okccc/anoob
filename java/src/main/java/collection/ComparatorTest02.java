package collection;

import java.util.Comparator;

public class ComparatorTest02 implements Comparator{

    public int compare(Object o1, Object o2) {
        String s1 = (String)o1;
        String s2 = (String)o2;
        int temp = s1.length() - s2.length();
        //     先按字符串长度排序,如果长度相同,再按照字符串自己的compareTo方法比较大小(字典顺序)
        return temp==0?s1.compareTo(s2):temp;
    }
}
