package j2se;

import java.io.File;
import java.io.FileReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@SuppressWarnings("unused")
public class ReflectDemo {
    public static void main(String[] args) throws Exception {
        /*
         * 反射：动态获取类的结构信息,创建对象,获取属性,调用方法
         * 类什么时候会被加载？
         * new创建该类对象 | 调用类中静态成员 | 加载该类的子类 | 反射
         * 静态加载：编译期加载类,如果类不存在则编译报错,依赖性太强
         * 动态加载(反射)：运行期加载类,类不存在编译不报错,降低依赖性,可以将类的描述写到配置文件,在各种框架中很常用
         * 实现反射机制的4个类：Class,Field,Method,Constructor
         */

//        getClassObject();
//        getClassField();
//        getClassMethod();
//        getClassConstructor();
//        getClassOther();
        test01();
        test02();
    }

    private static void getClassObject() throws Exception {
        // 获取Class类对象的三种方式
        // 1.使用类加载器,传入类/接口字符串
        Class<?> c1 = Class.forName("java.lang.String");
        System.out.println(c1.getName());  // java.lang.String
        System.out.println(c1.getSimpleName());  // String
        // 2.使用静态属性.class
        Class<String> c2 = String.class;
        // 3.使用Object类的getClass()方法
        Class<? extends String> c3 = "abc".getClass();
    }

    private static void getClassField() throws Exception {
        // 通过反射获取类的属性
        Class<?> c = Class.forName("j2se.Person");
//        Field[] fields = c.getFields();  // 只能获取public修饰的属性
        Field[] fields = c.getDeclaredFields();  // 获取所有属性
        for (Field field : fields) {
            // 修饰符
            String modifier = Modifier.toString(field.getModifiers());
            // 属性类型
            String type = field.getType().getSimpleName();
            // 属性名
            String name = field.getName();
            System.out.println(modifier +" "+ type +" "+ name);
        }
    }

    private static void getClassMethod() throws Exception {
        // 通过反射获取类的方法
        Class<?> c = Class.forName("j2se.Person");
        Method[] methods = c.getDeclaredMethods();
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
                String paraName = parameterTypes[i].getSimpleName();
                sb.append(paraName);
                if (i == parameterTypes.length-1) {
                    continue;
                }
                sb.append(", ");
            }
            sb.append(")");
            System.out.println(modifier +" "+ type +" "+ name + sb);
        }
    }

    private static void getClassConstructor() throws Exception {
        // 通过反射获取类的构造器
        Class<?> c = Class.forName("j2se.Person");
        Constructor<?>[] constructors = c.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            // 修饰符
            String modifier = Modifier.toString(constructor.getModifiers());
            // 构造方法名
            String name = c.getSimpleName();
            // 参数列表
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < parameterTypes.length; i++) {
                String paraName = parameterTypes[i].getSimpleName();
                sb.append(paraName);
                if (i == parameterTypes.length-1) {
                    continue;
                }
                sb.append(", ");
            }
            sb.append(")");
            System.out.println(modifier +" "+ name + sb);
        }
    }

    private static void getClassOther() throws Exception {
        // 通过反射获取类的其它结构：包、父类、接口、泛型、注解
        Class<?> c = Class.forName("j2se.Person");
        // 获取包
        Package pack = c.getPackage();
        System.out.println(pack.getName());  // basic
        // 获取父类
        Class<?> superclass = c.getSuperclass();
        System.out.println(superclass.getSimpleName());  // Object
        // 获取所有接口
        Class<?>[] interfaces = c.getInterfaces();
        for (Class<?> inter :interfaces){
            System.out.println(inter.getSimpleName());  // Serializable Comparable
        }
        // 获取所有注解,只能获取 @Retention(RetentionPolicy.RUNTIME)比如@Deprecated, SOURCE只保留在源码层面
        Annotation[] annotations = c.getAnnotations();
        for (Annotation annotation : annotations) {
            System.out.println(annotation.annotationType().getSimpleName());
        }
        // 获取泛型父类
        Type genericSuperclass = c.getGenericSuperclass();
        System.out.println(genericSuperclass.getTypeName());  // java.lang.Object
        // 获取所有泛型接口
        Type[] genericInterfaces = c.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            System.out.println(genericInterface.getTypeName());  // java.io.Serializable  java.lang.Comparable<j2se.Person>
        }
        // 泛型擦除：泛型是在编译期检查元素类型,并且只作用于编译期,而反射是作用于运行期,此时泛型已不存在
        List l1 = new ArrayList();
        List<String> l2 = new ArrayList<>();
        System.out.println(l1.getClass() == l2.getClass());  // true
    }

    private static void test01() throws Exception {
        // 通过反射创建类的对象
        Class<?> c = Class.forName("j2se.Person");
        System.out.println(c);  // class j2se.Person
        // 1.调用Class类的newInstance()方法,实例化一个带空参构造的类(推荐)
        Object o = c.newInstance();
        System.out.println(o);  // null: 0: null
        // 2.如果该类没有空参构造,需使用Constructor类的newInstance(Object ... initargs)方法
        Constructor<?> constructor = c.getDeclaredConstructor(String.class, int.class, String.class);
        // 暴力破解
        Object o1 = constructor.newInstance("grubby", 18, "123456");
        System.out.println(o1);  // grubby: 18: 123456

        // 获取属性
        Field f1 = c.getDeclaredField("name");
        Field f2 = c.getDeclaredField("SERIAL_VERSION_UID");
        // 暴力破解,直接访问会报错 java.lang.IllegalAccessException: Class j2se.ReflectDemo can not access a member of class j2se.Person with modifiers "private"
        f1.setAccessible(true);
        f2.setAccessible(true);
        f1.set(o, "aaa");
        System.out.println(f1.get(o));  // aaa
        System.out.println(f2.get(o));  // 5898267155926398171

        // 调用方法
        Method m1 = c.getDeclaredMethod("toString");
        Method m2 = c.getDeclaredMethod("hashCode");
        System.out.println(m1.invoke(o));  // aaa: 0: null
        System.out.println(m2.invoke(o));  // 96321
    }

    private static void test02() throws Exception {
        // 案例：电脑开始只有主板,主板对外提供接口,可以添加网卡和声卡等
        MainBoard mb = new MainBoard();
        mb.run();
        // 创建属性集合
        Properties prop = new Properties();
        // 关联输入流,从文件读取属性
        FileReader fr = new FileReader(new File("ability/input/pci.properties"));
        prop.load(fr);
        // 遍历集合
        Set<String> keys = prop.stringPropertyNames();
        for (String key : keys) {
            String value = prop.getProperty(key);
            System.out.println(value);  // j2se.NetCard, j2se.SoundCard
            // 通过反射加载类
            Class<?> c = Class.forName(value);
//            // 创建该类对象
//            Object o = c.newInstance();
//            // 获取对象方法并调用
//            Method m1 = c.getDeclaredMethod("open");
//            Method m2 = c.getDeclaredMethod("close");
//            m1.invoke(o);
//            m2.invoke(o);
            // 创建该类对象,并向上转型为接口类型
            PCI p = (PCI) c.newInstance();
            // 主板调用添加接口功能
            mb.invokePCI(p);
        }
    }

}

// 主板
class MainBoard {
    // 主板自身功能
    public void run(){
        System.out.println("我是主板");
    }

    // 主板对外提供添加接口功能
    public void invokePCI(PCI p){
        if (p != null) {
            p.open();
            p.close();
        }
    }
}

// 网卡、声卡等组件都具备开启关闭功能,向上抽取成接口
interface PCI {
    void open();
    void close();
}

// 网卡
class NetCard implements PCI {
    @Override
    public void open() {
        System.out.println("net open");
    }

    @Override
    public void close() {
        System.out.println("net close");
    }
}

// 声卡
class SoundCard implements PCI{
    @Override
    public void open() {
        System.out.println("sound open");
    }

    @Override
    public void close() {
        System.out.println("sound close");
    }
}