package com.okccc.j2se;

import com.okccc.bean.Person;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;

/**
 * @Author: okccc
 * @Date: 2020/9/21 12:09
 * @Desc: java反射
 *
 * 反射：动态加载类,创建未知类的对象并获取属性和调用方法
 * 类什么时候会被加载？
 * new创建该类对象 | 调用类中静态成员 | 加载该类的子类 | 反射
 * 静态加载：编译期加载类,类不存在则编译报错,依赖性太强
 * 动态加载：运行期加载类,类不存在编译不报错,降低依赖性,可以将类的描述写到配置文件,在各种框架中很常用
 * 实现反射机制的4个类：Class,Field,Method,Constructor
 *
 * 为什么new比反射效率高？
 * 1.编译时优化：new创建对象在编译时就确定对象的类型和构造函数,而反射要等到运行时才知道到底要加载哪个类,会有额外性能损耗
 * 2.缓存和重用：new创建对象可以放入缓存重用,避免重复创建和销毁对象,而反射每次都要动态创建新的对象
 */
public class ReflectDemo {

    /**
     * 获取Class类对象的三种方式
     */
    private static void getObject() throws Exception {
        // 1.使用类加载器,传入类/接口字符串
        Class<?> c1 = Class.forName("java.lang.String");
        System.out.println(c1.getName());  // java.lang.String
        System.out.println(c1.getSimpleName());  // String
        // 2.使用静态属性.class
        Class<String> c2 = String.class;
        // 3.使用Object类的getClass()方法
        Class<? extends String> c3 = "abc".getClass();
    }

    /**
     * 通过反射获取类的属性
     */
    private static void getField() throws Exception {
        Class<?> clazz = Class.forName("com.okccc.bean.Person");
//        Field[] fields = clazz.getFields();  // 只能获取public修饰的属性
        Field[] fields = clazz.getDeclaredFields();  // 获取所有属性,但是private修饰的属性要先获取访问权限
        for (Field field : fields) {
            // 修饰符
            String modifier = Modifier.toString(field.getModifiers());
            // 属性类型
            String type = field.getType().getSimpleName();
            // 属性名
            String name = field.getName();
            System.out.println(modifier + " " + type + " " + name);
        }
    }

    /**
     * 通过反射获取类的方法
     */
    private static void getMethod() throws Exception {
        Class<?> clazz = Class.forName("com.okccc.bean.Person");
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            // 修饰符
            String modifier = Modifier.toString(method.getModifiers());
            // 返回类型
            String type = method.getReturnType().getSimpleName();
            // 方法名
            String name = method.getName();
            // 参数列表
            Class<?>[] parameterTypes = method.getParameterTypes();
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append(parameterTypes[i].getSimpleName());
                if (i == parameterTypes.length - 1) {
                    continue;
                }
                sb.append(",");
            }
            sb.append(")");
            System.out.println(modifier + " " + type + " " + name + sb);
        }
    }

    /**
     * 通过反射获取类的构造器
     */
    private static void getConstructor() throws Exception {
        Class<?> clazz = Class.forName("com.okccc.bean.Person");
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            // 修饰符
            String modifier = Modifier.toString(constructor.getModifiers());
            // 构造方法名
            String name = clazz.getSimpleName();
            // 参数列表
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < parameterTypes.length; i++) {
                sb.append(parameterTypes[i].getSimpleName());
                if (i == parameterTypes.length-1) {
                    continue;
                }
                sb.append(",");
            }
            sb.append(")");
            System.out.println(modifier + " " + name + sb);
        }
    }

    // 通过反射获取类的其它结构：包、父类、接口、泛型、注解
    private static void getOther() throws Exception {
        Class<?> clazz = Class.forName("com.okccc.bean.Person");
        // 获取包
        Package pack = clazz.getPackage();
        System.out.println(pack.getName());  // com.okccc.bean
        // 获取父类
        Class<?> superclass = clazz.getSuperclass();
        System.out.println(superclass.getSimpleName());  // Object
        // 获取所有接口
        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> inter :interfaces){
            System.out.println(inter.getSimpleName());  // Serializable Comparable
        }
        // 获取所有注解,只能获取 @Retention(RetentionPolicy.RUNTIME)比如@Deprecated, SOURCE只保留在源码层面
        Annotation[] annotations = clazz.getAnnotations();
        for (Annotation annotation : annotations) {
            System.out.println(annotation.annotationType().getSimpleName());
        }
        // 获取泛型父类
        Type genericSuperclass = clazz.getGenericSuperclass();
        System.out.println(genericSuperclass.getTypeName());  // java.lang.Object
        // 获取所有泛型接口
        Type[] genericInterfaces = clazz.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            System.out.println(genericInterface.getTypeName());  // java.io.Serializable  java.lang.Comparable<com.okccc.bean.Person>
        }
        // 泛型擦除：泛型是在编译期检查元素类型,并且只作用于编译期,而反射是作用于运行期,此时泛型已不存在
        ArrayList<String> l1 = new ArrayList<>();
        ArrayList<Double> l2 = new ArrayList<>();
        System.out.println(l1.getClass() == l2.getClass());  // true
    }

    private static void example() throws Exception {
        // 使用类加载器加载类
        Class<?> clazz = Class.forName("com.okccc.bean.Person");
        System.out.println(clazz);
        // 调用Class类的newInstance()方法,实例化一个带空参构造的对象(推荐)
        Person p1 = (Person) clazz.newInstance();
        System.out.println(p1);
        // 如果该类没有空参构造,需使用Constructor类的newInstance(Object ... initargs)方法
        Constructor<?> constructor = clazz.getDeclaredConstructor(String.class, int.class, String.class);
        Person p2 = (Person) constructor.newInstance("grubby", 18, "123456");
        System.out.println(p2);

        // 获取属性
        Field f1 = clazz.getDeclaredField("name");
        Field f2 = clazz.getDeclaredField("SERIAL_VERSION_UID");
        // 由于类中字段是private的,要先获取访问权限,不然报错 java.lang.IllegalAccessException: Class com.okccc.j2se.ReflectDemo can not access a member of class com.okccc.pojo.Person with modifiers "private"
        f1.setAccessible(true);
        f2.setAccessible(true);
        f1.set(p1, "aaa");
        System.out.println(f1.get(p1));
        System.out.println(f2.get(p1));

        // 调用方法
        Method m1 = clazz.getDeclaredMethod("toString");
        Method m2 = clazz.getDeclaredMethod("hashCode");
        System.out.println(m1.invoke(p1));
        System.out.println(m2.invoke(p1));
    }

    public static void main(String[] args) throws Exception {
        // new创建对象
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100000000; i++) {
            ReflectDemo reflectDemo = new ReflectDemo();
        }
        long end = System.currentTimeMillis();
        System.out.println("new耗时：" + (end - start));

        // 反射创建对象
        for (int i = 0; i < 100000000; i++) {
            ReflectDemo reflectDemo = ReflectDemo.class.newInstance();
        }
        long end2 = System.currentTimeMillis();
        System.out.println("反射耗时：" + (end2 - end));
    }
}