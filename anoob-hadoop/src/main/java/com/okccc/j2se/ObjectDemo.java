package com.okccc.j2se;

/**
 * @Author: okccc
 * @Date: 2021/11/15 15:30
 * @Desc: java面向对象
 *
 * java程序运行时要在内存中分配空间并划分不同区域,每片区域的数据处理方式不一样
 * jvm内存结构
 * 栈内存：存放基本类型的变量和引用类型(对象)的引用,调用方法时会在栈开辟一块空间,每个线程包含一个栈区且数据是私有的别的栈不能访问
 *        栈是先进后出,比如在main方法里调add方法,main在栈的最底层上面是add,方法执行完就弹栈释放内存,生命周期短运行速度快
 * 堆内存：存放所有new出来的数组和对象,jvm只有一个堆区,被所有线程共享,当没有引用指向该对象时会变成垃圾,但是仍然占着内存,在随后
 *        某个不确定的时间被垃圾回收器释放掉,这也是java占内存的原因,生命周期不确定运行速度慢(动态分配内存大小)
 * 方法区：存放类、静态变量、字符串常量池等持久不变的数据,jvm只有一个方法区,被所有线程共享
 * 寄存器：在处理器内部,是最快的存储区域,数量极其有限,由编译器根据需求分配,在程序中感知不到寄存器的存在
 * 本地方法区：调用系统底层方法
 *
 * 八大基本类型：byte、short、int、long、float、double、boolean、char
 * int i = 10;  i是变量,局部变量在栈内存,成员变量在堆内存
 * public static final int N1 = 10;  N1是常量,在常量池
 *
 * 五大引用类型：数组、类、接口、枚举、注解
 * 面向过程：强调功能行为
 * 面向对象：强调具备功能的对象,具有封装、继承、多态三大特征
 * 类与对象：类是对象的抽象,对象是类的实例,类中成员包括变量、方法、构造器
 * 类的格式：修饰符 class 类名 {
 *             成员变量(属性);
 *             成员方法(行为);
 *         }
 * 匿名对象：创建对象后没有显式的赋给变量名,只能调用一次,加速垃圾回收过程
 *
 * 类中成员的权限修饰符：private < 缺省(default) < protected < public  类的权限修饰符只能是public或default
 * 修饰符      本类内部  同一个包  不同包子类  任何地方
 * private     yes
 * default     yes     yes
 * protected   yes     yes      yes
 * public      yes     yes      yes       yes
 *
 * 1.变量
 * 格式：修饰符 数据类型 变量名 = 初始化值;
 * 成员变量和局部变量区别
 * a.声明的位置不同：成员变量定义在类中,局部变量定义在语句、方法、构造器、代码块中
 * b.权限修饰符不同：成员变量可以使用修饰符,局部变量不可以使用修饰符,其访问权限取决于所在方法的修饰符
 * c.内存中位置不同：成员变量在堆内存的对象中,有默认初始化值;局部变量在栈内存的方法中,没有默认初始化值,调用前必须显式初始化
 * 类的属性赋值先后顺序：默认初始化 - 显式初始化 - 构造器初始化 - 对象.方法/对象.属性赋值
 *
 * 2.方法
 * 格式：修饰符 返回值类型 方法名 (参数列表) {
 *          方法体;
 *          return;  // 结束方法并将返回值返回给调用者,没有具体返回值时用void类型,return可以省略
 *      }
 * 两个明确: 返回值类型、参数列表(可以是可变个数：数据类型 ... 变量名)
 * 方法重载(overload)：在同一个类中,方法名相同,参数类型或参数个数不同,方法重载与修饰符以及返回值类型无关
 * 方法中的参数传递机制：值传递！
 * 如果形参是基本数据类型,则将变量指向的数据值传递给形参
 * 如果形参是引用数据类型,则将变量指向的地址值传递给形参
 *
 * 3.构造器
 * 格式：修饰符 类名 (参数列表) {初始化语句;}
 * 构造器用来创建对象并初始化对象属性,没有返回值类型,只调用一次,系统默认提供无参构造器,也可以自己声明构造器提供不同的创建对象方式
 * 同一个类的多个构造器之间构成方法重载,如果显式提供了类的构造器则系统不再提供默认空参构造
 *
 * 封装与隐藏
 * 将类的属性私有化,隐藏实现细节,对外提供公共的get/set方法获取和设置属性值,控制成员变量的访问,提高程序健壮性
 *
 * 关键字
 * this：代表当前对象,谁调用this所在方法就代表谁,可以调用变量、方法、构造器,当类中成员变量和局部变量重名时使用this关键字区分
 * import：导入指定包的类和接口,位于package和class之间,如果是java.lang包或者当前包下的类和接口可以省略import
 * package：将相同结构或类型的的类放在同一个包中,便于管理
 * jdk常用包
 * java.lang - 包含java的核心类,String/Math/Integer/Random/System/Thread ...
 * java.util - 包含常用工具类,Arrays/Collections/Date/Properties/Random/UUID 接口的集合框架类 Collection/Map
 * java.sql - 包含JDBC数据库编程相关的类和接口,DriverManager/Connection/PreparedStatement/ResultSet ...
 * java.net - 包含网络编程相关的类和接口,Socket/URL/InetAddress/DatagramPacket ...
 * java.io - 包含多种输入输出功能的类,InputStream/OutputStream/Reader/Writer ...
 * java.text - 包含格式化相关的类,Format/DateFormat/SimpleDateFormat ...
 *
 * static
 * 使用场景：如果要使用对象特有数据必须定义成非静态创建对象调用,否则可以定义成静态
 * 静态随着类的加载而加载,优先于对象存在,可以由类名直接调用
 * 静态只能访问静态,非静态访问一切(静态随着类加载,非静态只能被对象调用,前面的看不到后面的,后面的能看到前面的)
 * 静态中不能使用this和super关键字(静态随着类加载,优先于对象存在)
 * 静态只能修饰成员变量,不能修饰局部变量(静态随着类加载,优先于方法存在)
 * 成员变量和静态变量区别
 * a.生命周期不同：成员变量随对象存在而存在,对象被回收就释放;静态变量随类存在而存在,类消失就消失
 * b.调用方法不同：成员变量只能被对象调用,也叫实例变量;静态变量可以被类名直接调用,也叫类变量
 * c.存储位置不同：局部变量在栈内存;成员变量在堆内存的对象中(对象特有数据);静态变量在方法区(共享数据)
 *
 * 静态代码块：随着类的加载而加载,只执行一次,给类初始化
 * 构造代码块：类中的独立代码块,每次创建对象都会调用,给所有对象初始化
 * 构造函数：给特定对象针对性初始化
 * 执行顺序：静态代码块 > 构造代码块 > 构造函数
 *
 * 继承
 * 1.提高代码复用性
 * 2.让类与类产生关系,是多态的前提
 * java是单继承多实现,不能多继承(菱形问题：如果多个父类中有同名方法,会出现调用的不确定性)
 * 当要使用一个继承体系时：查看顶层类,了解该体系最基本的功能,创建体系中的最子类对象,完善功能
 * 方法重写：当子父类出现同名方法时会运行子类方法,子类权限必须大于父类才能重写,静态只能重写静态
 *
 * super
 * 当本类中成员变量和局部变量同名时,用this关键字区分,this代表一个本类对象的引用
 * 当子父类中成员变量同名时,用super关键字区分,super代表一个父类空间
 * 子类构造函数的第一行有个默认的隐式语句super()访问父类空参构造,也可以super(...)调用父类带参构造,super语句必须放在第一行,
 * 因为父类初始化要先执行,如果子类构造函数使用this调用本类其它构造函数那么super就不存在,因为this和super都必须放在第一行做初始化
 * 子类为什么要访问父类构造函数呢：因为子类继承父类,在使用父类内容之前,得先看看他是怎么初始化的吧
 *
 * 对象实例化过程
 * Person p = new Person();
 * 1.jvm读取指定路径下的Person.class文件并加载进内存,如果有父类就先加载父类
 * 2.在堆内存开辟空间,分配地址,默认初始化
 * 3.子类构造函数的super()会先调用父类空参构造初始化
 * 4.子类显式初始化
 * 5.子类构造代码块初始化
 * 6.子类构造函数初始化
 * 7.初始化完毕后将地址值赋给引用变量
 * (静态代码块) > 创建对象 > 默认初始化 > 显示初始化 > 构造代码块初始化 > 构造函数初始化  (初始化都是先父类后子类)
 *
 * 抽象类
 * 1.抽象类可以定义抽象方法也可以定义非抽象方法,但抽象方法只能在抽象类中
 * 2.抽象方法只有声明没有实现,所以抽象类不能实例化,因为调用抽象方法没有意义
 * 3.抽象类的子类必须重写所有抽象方法才能实例化,不然该子类还是抽象类
 *
 * abstract不能和哪些关键字共存？
 * final：抽象方法必须由子类重写才有意义,而final类不能被继承
 * private：抽象方法必须由子类重写才有意义,加private子类就访问不到了
 * static：静态表示可以用类名直接调用方法,然而调用抽象方法没有意义,必须由子类重写后对子类实例化调用
 *
 * 抽象类有构造函数,给子类初始化
 * 抽象类本身一定是父类,因为需要子类重写其抽象方法后对子类实例化
 * 抽象类可以不定义抽象方法,比如AWT适配器对象就是这种类,目的就是不让该类创建对象(不常用)
 *
 * final
 * final：是一个修饰符,修饰的东西都不可变,类不能被继承、方法不能被重写、变量是常量所以通常加static且名字大写
 * finally：异常处理最后用来释放资源的代码块,常见于IO流和数据库连接
 * finalize：Object类用于垃圾回收的方法
 *
 * 接口
 * 接口不属于类的体系,没有变量也没有构造函数只有抽象方法,默认public abstract修饰,可以看成是特殊的抽象类
 * 接口是多实现的,因为接口中都是抽象方法,不管实现的多个接口有多少同名方法,子类都必须重写,不存在调用的不确定性问题
 * 接口解决了java单继承的弊端,可以降低耦合度,提高代码扩展性
 * 抽象类和接口都是向上抽取而来,都是abstract所以不能实例化,而且接口连构造函数都没有
 * 抽象类需要被继承,定义通用功能,接口需要被实现,定义扩展功能
 * default关键字：实现接口必须重写所有抽象方法很不方便,于是java8做了妥协支持默认实现可以不重写(SpringMVC的WebMvcConfigurer接口)
 *
 * 多态
 * 前提：要有继承或实现关系
 * 优点：提高代码扩展性和健壮性
 * 向上转型：父类或接口的引用指向子类对象,将子类对象提升为父类类型,隐藏子类从而限制使用子类的特有方法
 * 向下转型：将父类或接口的引用强制转换成子类类型,从而继续使用子类的特有方法
 * a.List<String> list = new ArrayList<String>();
 * b.ArrayList<String> list = new ArrayList<String>();
 * List集合通常用写法a而不是写法b,面向接口编程,这样list调用的都是List接口的方法,如果程序需要用到LinkedList类的方法,只要改成
 * List<String> list = new LinkedList<String>();其他代码都不用动,如果代码里已经用了ArrayList类的方法,改动就很麻烦了
 *
 * 内部类
 * 内部类可以直接访问外部类,外部类必须创建内部类的对象才能访问内部类
 * 内部类可以简写成匿名内部类,匿名内部类必须继承一个外部类或者实现一个接口
 * 格式：在方法里面直接new 父类or接口<T>(){子类内容}
 * 抽象类和接口不能实例化,{}里面内容是子类方法重写,所以匿名内部类创建的其实是子类对象,然后再调用子类重写后的方法
 * 场景：抽象类和接口的子类都可以用匿名内部类实现,尤其是类很小或只用一次的时候,单独创建类很浪费可以通过匿名内部类实现
 * 案例：TreeSet<T>()构造函数传入Comparator<T>比较器、多线程实现Runnable接口
 *
 * 枚举
 * java中的特殊类,使用enum关键字代替class,通常表示一组常量,常量之间用逗号分隔,比如一年四季/一星期七天/东南西北...
 *
 * 注解
 * 用于修饰java中的数据(类/属性/方法/构造器...),不改变程序逻辑,但可以被编译器解析并做相应处理
 * 添加@Target(ElementType.{TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE}) 表示作用范围
 * 添加@Retention(RetentionPolicy.SOURCE) 表示保留策略 SOURCE | RUNTIME
 * public @interface SuppressWarnings {}  // 抑制代码中的编译警告
 */
public class ObjectDemo {
    public static void main(String[] args) {
        show(new Cat());
        show(new Dog());
        System.out.println(Season.SPRING);
    }

    // 演示对象创建
    public static class User {
        private String name;
        private int age;
        public static void main(String[] args) {
            User u1 = new User();
            u1.show();  // null: 0
            User u2 = new User("grubby");
            u2.show();  // grubby: 0
            User u3 = new User("moon", 19);
            u3.show();  // moon: 19
            boolean b = u2.compare(u3);
            System.out.println(b);  // false
        }
        public User() {
        }
        public User(String name) {
            // this可以调用构造器,但是必须在第一行,初始化动作要先执行
            this();
            this.name = name;
        }
        public User(String name, int age) {
            // 当类中成员变量和局部变量重名时,使用this区分,this代表当前对象,谁调用this所在方法就代表谁
            this.name = name;
            this.age = age;
        }
        @Override
        public String toString() {
            return "User{" + "name='" + name + '\'' + ", age=" + age + '}';
        }
        public void show() {
            System.out.println(this.name + ": " + this.age);
        }
        public boolean compare(User user) {
            return this.name.equals(user.name) && this.age == user.age;
        }
    }

    // 演示代码块
    public static class People {
        public static void main(String[] args) {
            People p1 = new People();
            p1.eat();
            People p2 = new People();
            p2.eat();
        }
        // 静态代码块
        static {
            System.out.println("static code");
        }
        // 构造代码块
        {
            System.out.println("construct code");
            eat();
        }
        private void eat(){
            System.out.println("eat...");
        }
    }

    // 演示多态
    public interface Animal {
        void eat();
    }
    public static class Cat implements Animal {
        @Override
        public void eat() {
            System.out.println("cat eats fish");
        }
    }
    public static class Dog implements Animal {
        @Override
        public void eat() {
            System.out.println("dog eats meat");
        }
    }
    public static void show(Animal animal) {
        // 使用多态时要注意代码的健壮性,instanceof用于判断对象的具体类型
        if (animal instanceof Cat) {
            // 向下转型
            Cat c = (Cat) animal;
            c.eat();
        } else {
            Dog d = (Dog) animal;
            d.eat();
        }
    }

    // 演示内部类
    public interface Inter {
        void method01();
        void method02();
    }
    public static class Outer{
        public static void main(String[] args) {
            Outer outer = new Outer();
            outer.inner();
        }
        // 直接new父类或接口
        void inner(){
            Inter inter = new Inter() {
                @Override
                public void method01() {
                    System.out.println("override method01");
                }
                @Override
                public void method02() {
                    System.out.println("override method02");
                }
            };
            inter.method01();
            inter.method02();
        }
    }

    // 演示枚举
    enum Season {
        // 由于构造函数、访问方法都省略了,常量必须放在第一行
        SPRING, SUMMER, AUTUMN, WINTER
        // 内部实现：类似单例模式,先在本类创建好一组对象,然后私有化构造函数,对外提供这一组对象的访问方法(常量)
//    public static final Season SPRING = new Season();
//    public static final Season SUMMER = new Season();
//    public static final Season AUTUMN = new Season();
//    public static final Season WINTER = new Season();
//    private Season() {}
    }
}
