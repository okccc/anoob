package map;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public class LinkedHashMapDemo {
    public static void main(String[] args) {
        
        LinkedHashMap<Integer, String> lhm = new LinkedHashMap<Integer,String>();
        lhm.put(1, "grubby");
        lhm.put(2, "moon");
        lhm.put(3, "fly");
        lhm.put(4, "ted");
        System.out.println(lhm);
        
        Iterator<Entry<Integer, String>> it = lhm.entrySet().iterator();
        while(it.hasNext()){
            Entry<Integer, String> e = it.next();
            System.out.println(e.getKey()+"....."+e.getValue());
        }
    }
}
