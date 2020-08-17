package map;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

public class HashMapDemo {
    public static void main(String[] args) {
        /**
         * Map集合:1、双列集合,存储键值对
         *        2、键唯一,键相同值覆盖
         *        3、在映射关系中比较常用
         * 
         * |--HashTable:哈希结构,同步的,kv值不可以为 null
         *    |--Properties:存储kv对的配置文件信息
         * |--HashMap:哈希结构,不同步,kv值可以为null
         * |--TreeMap:二叉树结构,不同步,可以对key进行排序
         * 
         * HashMap是Map集合中最常用的一种
         */
        
        HashMap<Integer, String> hs = new HashMap<Integer,String>();
        // 添加
        hs.put(1, "grubby");
        hs.put(2, "moon");
        hs.put(3, "fly");
        hs.put(3, "sky");

        // 删除
//         System.out.println(hs.remove(2));
        // 判断
        System.out.println(hs.isEmpty());
        System.out.println(hs.containsKey(1));
        // 获取
        System.out.println(hs.get(3));
        
        System.out.println(hs);
        
        // 遍历方式一：keySet
        Set<Integer> ks = hs.keySet();
        Iterator<Integer> it = ks.iterator();
        while(it.hasNext()){
            Integer key = it.next();
            String value = hs.get(key);
            System.out.println(key+"....."+value);
        }
        
        // 遍历方式二：entrySet
        Set<Entry<Integer, String>> es = hs.entrySet();
        Iterator<Entry<Integer, String>> it2 = es.iterator();
        while(it2.hasNext()){
            Entry<Integer, String> e = it2.next();
            Integer key = e.getKey();
            String value = e.getValue();
            System.out.println(key+"....."+value);
        }
        
    }
}
