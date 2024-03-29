package com.okccc.j2se;

import com.okccc.bean.Person;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @Author: okccc
 * @Date: 2020/9/1 10:05
 * @Desc: java集合
 *
 * 集合是存储对象的容器,只能存引用数据类型,不能存基本数据类型
 * 集合长度是可变的,集合内部数据结构不同,有多种容器,不断向上抽取形成集合框架,Collection和Map是顶层接口
 *
 * 前缀名是数据结构,后缀名是集合体系
 * Collection
 *     |-- List
 *         |-- Vector       数组结构,查询增删都慢,线程同步
 *         |-- ArrayList    数组结构,查询快增删慢,线程不同步
 *         |-- LinkedList   链表结构,增删快查询慢,线程不同步
 *     |-- Set
 *         |-- HashSet      哈希表(数组+链表,结合两者优点),唯一性,元素重写hashCode()和equals()方法,线程不同步
 *             |-- LinkedHashSet  可排序
 *         |-- TreeSet      二叉树,可排序,两个接口Comparable和Comparator,线程不同步
 *
 * Map(键相同值覆盖)
 *     |-- HashTable  哈希表,唯一性,kv值不可为null,线程同步
 *         |-- Properties  存储kv对的配置文件信息
 *     |-- HashMap    哈希表,唯一性,kv值可以为null,线程不同步
 *     |-- TreeMap    二叉树,可对key排序,不同步
 *
 * HashSet如何保证唯一性？
 * 存储元素时先判断元素的hashcode()值是否相同,不同就直接添加,相同就继续判断元素的equals()方法,最终确定元素是否重复
 *
 * TreeSet如何保证唯一性？
 * 根据比较方法的结果,返回0表示元素相同
 *
 * TreeSet如何排序？
 * 自然排序：String/Integer/Double这些类都实现了Comparable接口重写compareTo方法,元素自身可以比较大小
 * 比较器排序：往TreeSet的构造函数传入实现了Comparator接口重写compare方法的子类对象,让集合具备比较大小功能
 *
 * HashMap如何解决hash冲突？
 * hash冲突：hash表的本质是数组,存放键值对Entry,两个不同key的hashcode值相同,后面key发现数组位置已经被前面key占了,导致冲突
 * HashMap采用链表法,发生碰撞时将对象存储到链表的下一个节点
 *
 * 增强for循环
 * 格式：for(类型 变量 : 数组or集合){}
 * 普通for不需要遍历目标,可以定义控制循环的增量和条件
 * 增强for必须有遍历目标(数组or集合),其实是一种简写形式
 * 遍历集合时,如果仅仅是获取元素就用增强for,如果要操作索引就用普通for
 *
 * 泛型：jdk1.5出现的类型安全机制
 * 对象实例化时不指定泛型默认是Object,泛型<T>限定具体引用类型,可以用在方法、类和接口中
 * 1.编译时会检查添加元素的类型,确保类型安全
 * 2.避免向下转型(强制类型转换) String - Object - String | String - String - String
 * 泛型不具备继承性
 * <?>表示任意类型的泛型,使用该通配符做泛型的集合只能读不能写除了null,因为不确定具体类型
 * <? extends E> 限定传递的参数类型只能是E类型及其子类,使用该通配符做泛型的集合只能读不能写除了null
 * <? super E> 限定传递的参数类型只能是E类型及其父类,使用该通配符做泛型的集合只能读不能写除了null和自身
 */
public class CollectionDemo {

    private static void testList() {
        // 创建ArrayList对象
        List<String> al = new ArrayList<>();
        // 添加
        al.add("aaa");
        al.add("ccc");
        al.add("bbb");
        System.out.println("al = " + al);  // al = [aaa, ccc, bbb]
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

        // 创建LinkedList对象
        LinkedList<String> ll = new LinkedList<>();
        // 添加
        ll.add("aaa");
        ll.addFirst("bbb");
        ll.addLast("ccc");
        System.out.println("ll = " + ll);  // ll = [bbb, aaa, ccc]
        // 获取元素(不删除)
        System.out.println(ll.getFirst());
        // 获取元素(然后删除)
        System.out.println(ll.removeFirst());
    }

    private static void testSet() {
        // 创建HashSet对象
        HashSet<String> hs = new HashSet<>();
        // 添加
        hs.add("aaa");
        hs.add("bbb");
        hs.add("ccc");
        hs.add("aaa");
        System.out.println("hs = " + hs);  // hs = [aaa, ccc, bbb]

        // 创建LinkedHashSet对象：在set接口哈希表的基础上添加了链表结构,所以在保证唯一性的同时还能有序
        LinkedHashSet<String> lhs = new LinkedHashSet<>();
        lhs.add("aaa");
        lhs.add("bbb");
        lhs.add("ccc");
        lhs.add("aaa");
        System.out.println("lhs = " + lhs);  // lhs = [aaa, bbb, ccc]

        // 创建TreeSet对象
        TreeSet<String> ts = new TreeSet<>();
        // 字符串本身具备比较大小功能
        ts.add("ccc");
        ts.add("bbb");
        ts.add("aaa");
        ts.add("aaa");
        System.out.println("ts = " + ts);  // ts = [aaa, bbb, ccc]
        // 往构造函数传入实现了Comparator接口的子类对象
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
        System.out.println("ts2 = " + ts2);  // ts2 = [fly: 20, grubby: 18, moon: 19]
    }

    private static void testMap() {
        // 创建HashMap对象
        HashMap<String, Integer> hm = new HashMap<>();
        // 添加
        hm.put("grubby", 18);
        hm.put("moon", 19);
        hm.put("sky", 20);
        hm.put("sky", 17);
        System.out.println("hm = " + hm);  // hm = {sky=17, moon=19, grubby=18}
        // 获取
        System.out.println(hm.getOrDefault("fly", 0));
        // 判断
        System.out.println(hm.isEmpty());
        System.out.println(hm.containsKey("ted"));
        // 遍历keySet
        for (String key : hm.keySet()) {
            Integer value = hm.get(key);
            System.out.println(key + ": " + value);
        }
        // 遍历entrySet
        for (Map.Entry<String, Integer> entry : hm.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();
            System.out.println(key + ": " + value);
        }

        // 创建TreeMap对象
        TreeMap<Person, String> tm = new TreeMap<>();
        tm.put(new Person("sky", 20), "hum");
        tm.put(new Person("moon", 19), "ne");
        tm.put(new Person("grubby", 18), "orc");
        System.out.println("tm = " + tm);  // tm = {grubby: 18=orc, moon: 19=ne, sky: 20=hum}
        // 遍历entrySet
        for (Map.Entry<Person, String> entry : tm.entrySet()) {
            Person key = entry.getKey();
            String value = entry.getValue();
            System.out.println(key.getName() + ": " + key.getAge() + ": " + value);
        }
    }

    public static void testProperties() throws IOException {
        // Properties是持久化的Map集合,可以将内存数据保存到本地,键和值都是String类型,映射关系(map) + 读写(io) = Properties
        Properties prop = new Properties();
        // 设置属性
        prop.setProperty("grubby", "orc");
        prop.setProperty("moon", "ne");

        // 关联输出流,将属性写入文件
        FileWriter fw = new FileWriter(new File("ability/input/war3.properties"), false);
        prop.store(fw, "war3");
        fw.close();

        // 关联输入流,从文件读取属性
        FileReader fr = new FileReader(new File("ability/input/war3.properties"));
        prop.load(fr);
        // 遍历集合,类似keySet
        Set<String> keys = prop.stringPropertyNames();
        for (String key : keys) {
            String value = prop.getProperty(key);
            System.out.println(key +": "+ value);
        }
        // 或者直接将属性列表打印在控制台
//        prop.list(System.out);
        fr.close();

//        // System类获取系统属性信息
//        Properties properties = System.getProperties();
//        properties.list(System.out);
//        // 获取系统默认分隔符,代码中一般定义成static final常量,这样就不用区分Windows/Linux,提高代码可移植性
//        System.out.println(System.getProperty("line.separator"));
//        // 获取系统当前时间
//        System.out.println(System.currentTimeMillis());
    }

    public static void testAppCount() throws IOException {
        // 需求：判断应用程序运行次数,超过3次就提示先去注册再使用,并终止程序
        // 分析：要使用计数器count,但是计数器是在内存中,每次程序启动会归零,所以需要持久化,且times=?是映射关系所以选择Properties
        File file = new File("ability/input/count.properties");
        if(!file.exists()) {
            file.createNewFile();
        }
        Properties prop = new Properties();
        prop.setProperty("times", "0");
        FileReader fr = new FileReader(file);
        prop.load(fr);
        int count = 0;
        String value = prop.getProperty("times");
        if(value != null) {
            count = Integer.parseInt(value);
            count++;
            if(count > 3) {
                throw new RuntimeException("免费次数已达上限,请注册后继续使用~");
            }
        }
        prop.setProperty("times", Integer.toString(count));
        FileWriter fw = new FileWriter(file);
        prop.store(fw, "modify");
        fr.close();
        fw.close();
    }

    public static void testCollections() {
        // Collections工具类构造函数私有化,方法都是静态,不需要创建对象直接类名调用
        List<String> list = new ArrayList<>();
        list.add("aaa");
        list.add("ccc");
        list.add("bbb");
        // 排序
        Collections.sort(list);
        System.out.println("sort: " + list);  // sort: [aaa, bbb, eee]
        // 反转
        Collections.reverse(list);
        System.out.println("reverse: " + list);  // reverse: [bbb, eee, aaa]
        // 二分查找
        System.out.println(Collections.binarySearch(list, "aaa"));  // 0
        // 洗牌
        Collections.shuffle(list);
        System.out.println("shuffle: " + list);  // shuffle: [bbb, aaa, eee]
        // 最大值
        System.out.println("max = " + Collections.max(list));  // max = eee
    }

    public static void wordCount() {
        // 需求：统计字符串中每个字符出现的次数 a(2) b(1)...
        String str = "fdga-vcb+1&sacdfs";
        // 分析：结果是映射关系并且是按字母排序的,考虑使用TreeMap结构
        TreeMap<Character, Integer> tm = new TreeMap<>();
        // 将字符串转成字符数组
        char[] arr = str.toCharArray();
        // 遍历数组,看字符是否存在于map集合的key
        for (char c : arr) {
            // 可以只筛选纯字母,字符包括字母、数字、符号
//            if (! (c >= 'a' & c <= 'z' || c >= 'A' & c <= 'Z')) {
//                continue;
//            }
            int count;
            if (! tm.containsKey(c)) {
                count = 1;
            } else {
                count = tm.get(c) + 1;
            }
            tm.put(c, count);
        }
        System.out.println(tm);  // {&=1, +=1, -=1, 1=1, a=2, b=1, c=2, d=2, f=2, g=1, s=2, v=1}
        // 将集合转成字符串
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Character, Integer> entry : tm.entrySet()) {
            sb.append(entry.getKey()).append("(").append(entry.getValue()).append(") ");
        }
        System.out.println(sb);  // &(1) +(1) -(1) 1(1) a(2) b(1) c(2) d(2) f(2) g(1) s(2) v(1)
    }

    public static void main(String[] args) throws IOException {
//        testList();
//        testSet();
        testMap();
//        testProperties();
//        testAppCount();
//        testCollections();
//        wordCount();
    }
}