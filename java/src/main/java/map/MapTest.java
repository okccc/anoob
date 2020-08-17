package map;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class MapTest {
    public static void main(String[] args) {
        /**
         * 需求:获取"fdgavcbsacdfs"字符串中每个字母出现的次数,打印结果a(2)b(1)
         * 
         * 分析:从打印结果判断出应该是映射关系--->Map集合
         *     字母本身是有顺序的--->TreeMap
         *     
         * 步骤:1、将字符串变成字符数组
         *     2、定义TreeMap<Character,Integer>集合
         *     3、遍历数组,获取每个元素,将字母作为key去查Map集合这个表,存在就将对应的value值+1,不存在就作为key添加
         *     4、将Map集合再转换成结果要表示的字符串
         *     
         * 注意:字母!=字符    字母26个   字符包括字母、数字、符号
         */
        
        String str = "fdga-vcb+1&sacdfs";
        System.out.println(str);
        String result = getCharCount(str);
        System.out.println(result);
    }

    private static String getCharCount(String str) {
        // 1、将字符串变成字符数组
        char[] c = str.toCharArray();
        // 2、定义TreeMap集合
        Map<Character, Integer> tm = new TreeMap<Character,Integer>();
        // 3、遍历数组,判断字符是否在Map集合中存在
        for (int i = 0; i < c.length; i++) {
            // 可以筛选下是否是英文字母
//             if(!(c[i] >= 'a' && c[i] <= 'z' || c[i] >= 'A' && c[i] <= 'Z')){
//                 continue;
//             }
            int count;
            if(!tm.containsKey(c[i])){
                count = 1;
            }else{
                count = tm.get(c[i]) +1;
            }
            tm.put(c[i], count);
        }
        // 4、将结果转换成字符串
        return mapToString(tm);
    }

    private static String mapToString(Map<Character, Integer> tm) {
        // 定义字符串缓冲区
        StringBuilder sb = new StringBuilder();
        // 迭代Map集合
        Iterator<Entry<Character, Integer>> it = tm.entrySet().iterator();
        while(it.hasNext()){
            Entry<Character, Integer> e = it.next();
            // 将kv添加到字符串缓冲区中
            sb.append(e.getKey()+"("+e.getValue()+")");
        }
        return sb.toString();
    }
}
