package reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectDemo {
    public static void main(String[] args) throws ClassNotFoundException, Exception {
        /**
         * 反射:java程序在JVM运行时可以动态获取任意一个类的属性和方法,而不需要在编译时期预先明确对象
         *     注意:是运行时期而不是编译时期
         * 优点:大大提高程序扩展性,可以将类的描述写到配置文件,避免将程序写死到代码里,在各种框架里较常用
         * 
         * 实现反射机制的4个类:Class,Constructor,Field,Method
         */
        
        getClass01();
        getClass02();
        getClass03();
    }

    /**
     * 方式三:只要给定类名的字符串(package.class)即可
     *      static Class<?> forName(String className)
     * @throws ClassNotFoundException 
     * @throws IllegalAccessException 
     * @throws InstantiationException 
     */
    private static void getClass03() throws ClassNotFoundException, Exception {
        
        // 1、用Class对象通过字符串名称找到该类,加载进内存,产生Class对象
        Class clazz = Class.forName("reflect.Person");
        System.out.println(clazz);
        
        // 2、用 Class对象获取该类,newInstance()相当于用new实例化一个带空参构造的类
        Object obj1 = clazz.newInstance();
        System.out.println(obj1);
        
        // 如果没有空参构造,就要先获取Constructor对象,通过构造器对象获取类
        Constructor constructor = clazz.getConstructor(String.class,int.class);
        Object obj2 = constructor.newInstance("grubby",18);
        System.out.println(obj2);
        
        // 3、用Class对象获取该类Field对象
        Field field = clazz.getDeclaredField("name");
        // 取消私有字段的访问检查权限,暴力访问
        field.setAccessible(true);
        // 通过Field对象操作Object
        field.set(obj2, "moon");
        System.out.println(field.get(obj2));
        
        // 4、用Class对象获取该类Method对象
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            System.out.println(method);
        }
        
        Method method = clazz.getDeclaredMethod("show", null);
        System.out.println(method);
        method.invoke(obj2, null);
        
        
    }

    /**
     * 方式二:任何数据类型都具备静态属性.class来获取对应的class对象
     */
    private static void getClass02() {
        Class clazz1 = Person.class;
        Class clazz2 = Person.class;
        System.out.println(clazz1==clazz2);
    }

    /**
     * 方式一:Object对象的getClass()方法
     *      特点:必须先明确具体类并创建对象
     */
    private static void getClass01() {
        Person p1 = new Person();
        Person p2 = new Person();
        System.out.println(p1.getClass()==p2.getClass());
    }
}
