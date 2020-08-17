package map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ForEachDemo {
    public static void main(String[] args) {
        /**
         * foreach语句 ：
         * 格式：for(类型  变量  : 数组/集合){
         * 
         *     }
         *     
         * 普通for循环和增强 for循环区别：
         * 普通for不需要遍历目标,可以定义控制循环的增量和条件,对语句执行很多次
         * 增强for必须有遍历目标(数组/集合),其实是一种简写形式
         * 
         * 如果仅仅是遍历获取元素可以使用增强for,如果要操作索引就用普通for 
         */
        
        List<String> list = new ArrayList<String>();
        list.add("abc");
        list.add("haha");
        list.add("bcd");
        list.add("zzz");
        list.add("swqdf");
        System.out.println(list);
        
        for (String s : list) {
            System.out.println(s);
        }
        
        System.out.println("------------------------我是分割线-----------------------");
        
        Map<String, Integer> hm = new HashMap<String,Integer>();
        hm.put("grubby", 1);
        hm.put("moon", 2);
        hm.put("sky", 3);
        hm.put("fly", 4);
        System.out.println(hm);
        
        for (String key : hm.keySet()) {
            Integer value = hm.get(key);
            System.out.println(key+"..."+value);
        }
        
        for (Entry<String, Integer> e : hm.entrySet()) {
            String key = e.getKey();
            Integer value = e.getValue();
            System.out.println(key+"..."+value);
        }
    }
}
