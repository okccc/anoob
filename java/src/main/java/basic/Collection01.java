package basic;

import java.util.*;

public class Collection01 {
    public static void main(String[] args) {
        /*
         * 集合是存储对象的容器,只能存引用数据类型,不能存基本数据类型
         * 集合长度是可变的,集合内部数据结构不同,有多种容器,不断向上抽取形成集合框架,Collection和Map是顶层接口
         *
         * Collection
         *     |-- List(有序,可重复)
         *         |-- Vector       数组结构,查询增删都慢,同步的
         *         |-- ArrayList    数组结构,查询快增删慢,不同步
         *         |-- LinkedList   链表结构,增删快查询慢,不同步
         *     |-- Set(无序,唯一)
         *         |-- HashSet      哈希表,唯一性,元素重写hashCode()和equals()方法
         *             |-- LinkedHashSet  可排序
         *         |-- TreeSet      二叉树,可排序,两个接口Comparable和Comparator
         * 前缀名是数据结构,后缀名是集合体系
         * HashSet如何保证唯一性？
         * 存储元素时先判断元素的hashcode()值是否相同,不同就直接添加,相同就继续判断元素的equals()方法,最终确定元素是否重复
         * TreeSet如何保证唯一性？
         * 根据比较方法的结果,返回0表示元素相同
         * TreeSet如何排序？
         * 自然排序：让元素本身具备比较功能,实现Comparable接口,覆盖compareTo方法
         * 比较器排序：让集合具备比较功能,自定义类实现Comparator接口重写compare方法,然后将该类对象作为TreeSet的带参构造传入(常用)
         *
         * Map(键相同值覆盖)
         *     |-- HashTable  哈希表,kv值不可为null,同步的
         *         |-- Properties  存储kv对的配置文件信息
         *     |-- HashMap    哈希表,kv值可以为null,不同步
         *     |-- TreeMap    二叉树,可对key排序,不同步
         *
         * 增强for循环
         * 格式：for(类型 变量 : 数组or集合){}
         * 普通for不需要遍历目标,可以定义控制循环的增量和条件
         * 增强for必须有遍历目标(数组or集合),其实是一种简写形式
         * 如果仅仅是遍历获取元素就用增强for,如果要操作索引就用普通for
         */

        // ArrayList
        ArrayList<String> al = new ArrayList<>();
        // 添加
        al.add("aaa");
        al.add("bbb");
        al.add("ccc");
        System.out.println(al);  // [aaa, bbb, ccc]
        // 插入
        al.add(1, "ddd");
        // 修改
        al.set(2, "eee");
        // 删除
        al.remove(1);
        // 获取
        System.out.println(al.get(1));
        // 截取
        System.out.println(al.subList(1, 2));
        // 遍历
        for (String s : al) {
            System.out.println(s);
        }
        // Collections工具类构造函数私有化,方法都是静态,不需要创建对象直接类名调用
        Collections.reverse(al);
        System.out.println("reverse: " + al);
        Collections.sort(al);
        System.out.println("sort: " + al);
        al.sort(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                int tmp = o1.length() - o2.length();
                return tmp == 0 ? o1.compareTo(o2) : tmp;
            }
        });
        System.out.println("sort: " + al);
        System.out.println(Collections.binarySearch(al, "aaa"));
        Collections.shuffle(al);
        System.out.println("shuffle: " + al);
        String max = Collections.max(al, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                int tmp = o1.length() - o2.length();
                return tmp == 0 ? o1.compareTo(o2) : tmp;
            }
        });
        System.out.println("max = " + max);

        // LinkedList
        LinkedList<String> ll = new LinkedList<>();
        // 添加
        ll.add("aaa");
        ll.addFirst("bbb");
        ll.addLast("ccc");
        System.out.println(ll);  // [bbb, aaa, ccc]
        // 获取元素(不删除)
        System.out.println(ll.getFirst());
        // 获取元素(然后删除)
        System.out.println(ll.removeFirst());

        // HashSet
        HashSet<String> hs = new HashSet<>();
        // 添加
        hs.add("aaa");
        hs.add("bbb");
        hs.add("ccc");
        hs.add("aaa");
        System.out.println(hs);  // [aaa, ccc, bbb]

        // LinkedHashSet：在set接口哈希表的基础上添加了链表结构,所以在保证唯一性的同时还能有序
        LinkedHashSet<String> lhs = new LinkedHashSet<>();
        lhs.add("aaa");
        lhs.add("bbb");
        lhs.add("ccc");
        lhs.add("aaa");
        System.out.println(lhs);  // [aaa, bbb, ccc]

        // TreeSet
        TreeSet<String> ts = new TreeSet<>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                // 先比较字符串长度,相同就再比较字符串字典顺序
                int tmp = o1.length() - o2.length();
                return tmp == 0 ? o1.compareTo(o2) : tmp;
            }
        });
        ts.add("aaa");
        ts.add("bbb");
        ts.add("ccc");
        ts.add("aaa");
        System.out.println(ts);  // [aaa, bbb, ccc]

        TreeSet<Person> ts2 = new TreeSet<>(new Comparator<Person>() {
            @Override
            public int compare(Person o1, Person o2) {
                // 先比较名字是否相同,相同就再比较年龄大小
                int tmp = o1.getName().compareTo(o2.getName());
                return tmp == 0 ? o1.getAge() - o2.getAge() : tmp;
            }
        });
        ts2.add(new Person("grubby", 18));
        ts2.add(new Person("moon", 19));
        ts2.add(new Person("fly", 20));
        ts2.add(new Person("grubby", 18));
        System.out.println(ts2);  // [fly: 20, grubby: 18, moon: 19]

        // HashMap
        HashMap<Integer, String> hm = new HashMap<>();
        // 添加
        hm.put(1, "grubby");
        hm.put(2, "moon");
        hm.put(3, "sky");
        hm.put(3, "fly");
        System.out.println(hm);  // {1=grubby, 2=moon, 3=fly}
        // 获取
        System.out.println(hm.getOrDefault(1, "-"));
        // 判断
        System.out.println(hm.isEmpty());
        System.out.println(hm.containsKey(4));
        // 遍历keySet
        for (Integer key : hm.keySet()) {
            String value = hm.get(key);
            System.out.println(key + ": " + value);
        }
        // 遍历entrySet
        for (Map.Entry<Integer, String> entry : hm.entrySet()) {
            Integer key = entry.getKey();
            String value = entry.getValue();
            System.out.println(key + ": " + value);
        }

        // TreeMap
        TreeMap<Person, String> tm = new TreeMap<>();
        tm.put(new Person("grubby", 18), "荷兰");
        tm.put(new Person("moon", 19), "棒子");
        tm.put(new Person("sky", 20), "天朝");
        System.out.println(tm);  // {grubby: 18=荷兰, moon: 19=棒子, sky: 20=天朝}
        for (Map.Entry<Person, String> entry : tm.entrySet()) {
            Person key = entry.getKey();
            String value = entry.getValue();
            System.out.println(key.getName() + ": " + key.getAge() + ": " + value);
        }
    }
}
