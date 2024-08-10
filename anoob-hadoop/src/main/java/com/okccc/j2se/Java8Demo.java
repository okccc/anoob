package com.okccc.j2se;

import com.okccc.bean.Person;

import java.util.*;
import java.util.function.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @Author: okccc
 * @Date: 2020/9/26 10:06
 * @Desc: java8新特性
 *
 * lambda表达式
 * 只有一个未实现方法的接口叫函数式接口,通常会标注@FunctionalInterface注解
 * java内置四大核心函数式接口 Consumer<T> | Supplier<T> | Function<T,R> | Predicate<T>
 * 当匿名内部类实现函数式接口时,可以用lambda表达式简化代码 (参数列表) -> {抽象方法重写} 只有单个参数或单行代码时可以省略括号
 * 方法引用
 * 如果lambda体中只有一行代码,这行代码为方法的调用,且调用方法的参数列表和返回值与实现的抽象方法的参数列表和返回值保持一致
 * 那么可以继续简化为lambda的语法糖形式  方法引用(对象/类::方法名) | 构造器引用(类::new) | 数组引用(数组类型[]::new)
 *
 * Stream Api
 * 对数组和集合生成的元素序列进行一系列的流式操作
 * 创建流：可以通过数组/集合等数据源生成Stream对象
 * 转换流：数据源transform操作会生成新的Stream对象,直到action操作才会触发计算(懒加载)
 * 终止流：只执行一次,执行完流就关闭,继续执行后续代码会报错 java.lang.IllegalStateException: stream has already been operated upon or closed
 * Optional是一个容器类,代表一个值存在或不存在,可以替换null值避免空指针异常
 */
public class Java8Demo {
    public static void main(String[] args) {
        testLambda();
        testMethodRef();

        // 通过集合创建Stream(常用)
        ArrayList<Person> list = new ArrayList<>();
        list.add(new Person("grubby", 18));
        list.add(new Person("moon", 19));
        list.add(new Person("sky", 20));
//        createStream(list);
//        transformStream(list);
        actionStream(list);
    }

    private static void testLambda() {
        // 匿名内部类
        Consumer<String> c1 = new Consumer<String>() {
            @Override
            public void accept(String s) {
                System.out.println(s.toUpperCase());
            }
        };
        c1.accept("java");
        // lambda表达式
        Consumer<String> c2 = s -> System.out.println(s.toUpperCase());
        c2.accept("java");

        // 匿名内部类
        Supplier<String> s1 = new Supplier<String>() {
            @Override
            public String get() {
                return "python".toUpperCase();
            }
        };
        System.out.println(s1.get());
        // lambda表达式
        Supplier<String> s2 = () -> "python".toUpperCase();
        System.out.println(s2.get());

        // 匿名内部类
        Function<Double, Double> f1 = new Function<Double, Double>() {
            @Override
            public Double apply(Double d) {
                return Math.pow(d, 2);
            }
        };
        System.out.println(f1.apply(10d));
        // lambda表达式
        Function<Double, Double> f2 = d -> Math.pow(d, 2);
        System.out.println(f2.apply(10d));

        // 匿名内部类
        Predicate<String> p1 = new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.contains("j");
            }
        };
        System.out.println(p1.test("hello"));
        // lambda表达式
        Predicate<String> p2 = s -> s.contains("j");
        System.out.println(p2.test("hello"));
    }

    private static void testMethodRef() {
        // 匿名内部类
        Consumer<String> c1 = new Consumer<String>() {
            @Override
            public void accept(String s) {
                System.out.println(s);
            }
        };
        // lambda表达式
        Consumer<String> c2 = s -> System.out.println(s);
        // 方法引用(对象::普通方法)
        Consumer<String> c3 = System.out::println;
        c1.accept("hello");
        c2.accept("hello");
        c3.accept("hello");

        // 匿名内部类
        Comparator<Integer> com1 = new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o1.compareTo(o2);
            }
        };
        // lambda表达式
        Comparator<Integer> com2 = (o1, o2) -> Integer.compare(o1, o2);
        // 方法引用(类::静态方法)
        Comparator<Integer> com3 = Integer::compare;
        System.out.println(com1.compare(10, 20));
        System.out.println(com2.compare(10, 20));
        System.out.println(com3.compare(10, 20));

        // 匿名内部类
        Comparator<String> com01 = new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };
        // lambda表达式
        Comparator<String> com02 = (o1, o2) -> o1.compareTo(o2);
        // 方法引用(类::普通方法)
        Comparator<String> com03 = String::compareTo;
        System.out.println(com01.compare("okc", "orc"));
        System.out.println(com02.compare("okc", "orc"));
        System.out.println(com03.compare("okc", "orc"));

        // 匿名内部类
        Supplier<List<String>> s1 = new Supplier<List<String>>() {
            @Override
            public List<String> get() {
                return new ArrayList<>();
            }
        };
        // lambda表达式
        Supplier<List<String>> s2 = () -> new ArrayList<>();
        // 构造器引用(类::new)
        Supplier<List<String>> s3 = ArrayList::new;
        System.out.println(s1.get());
        System.out.println(s2.get());
        System.out.println(s3.get());

        // 匿名内部类
        Function<Integer, String[]> f1 = new Function<Integer, String[]>() {
            @Override
            public String[] apply(Integer i) {
                return new String[i];
            }
        };
        // lambda表达式
        Function<Integer, String[]> f2 = i -> new String[i];
        // 数组引用(数组[]::new)
        Function<Integer, String[]> f3 = String[]::new;
        System.out.println(Arrays.toString(f1.apply(3)));
        System.out.println(Arrays.toString(f2.apply(3)));
        System.out.println(Arrays.toString(f3.apply(3)));
    }

    private static void createStream(ArrayList<Person> list) {
        // 通过集合获取串行或并行流对象
        Stream<Person> s1 = list.stream();
        Stream<Person> s2 = list.parallelStream();
        // 执行流的action操作
        s1.forEach(System.out::println);

        // 通过数组创建Stream
        String[] arr = {"grubby", "moon", "sky"};
        Stream<String> s3 = Arrays.stream(arr);
        s3.forEach(System.out::println);

        // 通过一组序列创建Stream
        Stream<String> s4 = Stream.of("grubby", "moon", "sky");
        s4.forEach(System.out::println);

        // 生成无限流
        Stream<Double> s5 = Stream.generate(Math::random);
        s5.forEach(System.out::println);
    }

    private static void transformStream(ArrayList<Person> list) {
        // 通过集合获取流对象
        Stream<Person> s = list.stream();

        // 过滤、去重(filter/limit/skip/distinct)
//        s.filter(person -> person.getAge() < 20).forEach(System.out::println);
//        s.limit(2).forEach(System.out::println);
//        s.skip(2).forEach(System.out::println);
//        s.distinct().forEach(System.out::println);

        // 映射(map/flatMap)
//        s.map(Person::getName).forEach(System.out::println);
//        s.map(Person::getAge).forEach(System.out::println);
        // flatMap可以将Person中的每个元素映射成Stream对象
//        s.flatMap(new Function<Person, Stream<?>>() {
//            @Override
//            public Stream<?> apply(Person person) {
//                // 此处lambda体中调用方法的参数类型(String/int)和实现抽象方法的参数类型(Person)不一致,所以不能简写成方法引用
//                return Stream.of(person.getName(), person.getAge());
//            }
//        }).forEach(System.out::println);
//        s.flatMap(new Function<Person, Stream<?>>() {
//            @Override
//            public Stream<?> apply(Person person) {
//                // 可以先将具体代码抽取成方法,使参数列表和返回值保持一致,这样就能使用方法引用了
//                return personToStream(person);
//            }
//        }).forEach(System.out::println);
//        s.flatMap(Java8Demo::personToStream).forEach(System.out::println);

        // 排序(sorted/sorted(Comparator))
//        s.sorted().forEach(System.out::println);
        s.sorted(new Comparator<Person>() {
            @Override
            public int compare(Person o1, Person o2) {
                // 此处lambda体中调用方法的参数类型和实现抽象方法的参数类型不一致,所以不能简写成方法引用
//                return Integer.compare(o2.getAge(), o1.getAge());
                return compareAge(o1, o2);
            }
        }).forEach(System.out::println);
    }

    private static void actionStream(ArrayList<Person> list) {
        // 通过集合获取流对象
        Stream<Person> s = list.stream();

        // 聚合(count/max/min/reduce)
//        System.out.println(s.count());
//        Optional<Person> max = s.max((o1, o2) -> o1.getAge() - o2.getAge());
//        Optional<Person> min = s.min((o1, o2) -> o1.getAge() - o2.getAge());
//        System.out.println(min);
        // reduce通常结合map使用进行字符串拼接,数字加减等
        Optional<String> reduce = s.map(Person::getName).reduce((s1, s2) -> s1.concat(", ").concat(s2));
        System.out.println(reduce);  // Optional[grubby, moon, sky]
//        Optional<Integer> sum = s.map(Person::getAge).reduce(Integer::sum);
//        System.out.println(sum);  // Optional[57]

        // Optional类
        Optional<Person> o1 = Optional.of(new Person("okc", 19));
        System.out.println(o1);  // Optional[Person[name: okc, age: 19, idcard: null]]
        Optional<Object> o2 = Optional.ofNullable(null);
        System.out.println(o2);  // Optional.empty
        Object o3 = o2.orElse(new Person("orc", 20));
        System.out.println(o3);  // Person[name: orc, age: 20, idcard: null]
    }

    private static Stream<Object> personToStream(Person person) {
        return Stream.of(person.getName(), person.getAge());
    }

    private static int compareAge(Person o1, Person o2) {
        return Integer.compare(o2.getAge(), o1.getAge());
    }
}