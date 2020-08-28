package basic;

public class ObjectDemo {
    public static void main(String[] args) {
        /*
         * java程序运行时要在内存中分配空间并划分不同区域,每片区域的数据处理方式不一样
         * jvm内存结构
         * 栈内存：存放基本类型的变量和引用类型(对象)的引用,调用方法时会在栈开辟一块空间,每个线程包含一个栈区且数据是私有的别的栈不能访问
         *       栈是先进后出,比如main方法里调用add方法,main在栈的最底层上面是add,方法执行结束系统会自动释放内存,生命周期短运行速度快
         * 堆内存：存放new创建的数组和对象,jvm只有一个堆区,堆区被所有线程共享,当没有引用指向该对象时会变成垃圾,但是仍然占着内存,在随后某个
         *       不确定的时间被垃圾回收器释放掉,这也是java占内存的原因,生命周期不确定运行速度慢(动态分配内存大小)
         * 方法区：存放类、静态变量等持久不变的数据,jvm只有一个方法区,方法区被所有线程共享
         * 基本数据类型一定存在栈内存中吗？不是的,要看是局部变量还是成员变量
         *
         * 三大引用类型：数组、类、接口
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
         * 修饰符       类内部  同一个包  不同包的子类  任何地方
         * private     yes
         * default     yes    yes
         * protected   yes    yes      yes
         * public      yes    yes      yes         yes
         *
         * 1.变量
         * 格式：修饰符 数据类型 变量名 = 初始化值;
         * 成员变量和局部变量
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
         * 方法重载(overload)：在同一个类中,方法名相同,参数类型或参数个数不同,方法重载和修饰符以及返回值类型无关
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
         * this：代表当前对象,谁调用this所在方法就代表谁,可以调用变量、方法、构造器,当类中成员变量和局部变量重名时可以用this关键字区分
         * package：将相同结构或类型的的类放在同一个包中,便于管理
         * jdk常用包
         * java.lang - 包含java的核心类,String/Math/Integer/Random/System/Thread ...
         * java.util - 包含常用工具类,Arrays/Collections/Date/Properties/Random/UUID 接口的集合框架类 Collection/Map
         * java.sql - 包含JDBC数据库编程相关的类和接口,DriverManager/Connection/PreparedStatement/ResultSet ...
         * java.net - 包含网络编程相关的类和接口,Socket/URL/InetAddress/DatagramPacket ...
         * java.io - 包含多种输入输出功能的类,InputStream/OutputStream/FileReader/FileWriter ...
         * java.text - 包含格式化相关的类,Format/DateFormat/SimpleDateFormat ...
         * import：导入指定包的类和接口,位于package和class之间,如果是java.lang包或者当前包下的类和接口可以省略import
         */
    }
    
}


class Demo {
    int x = 3;
    public static void main(String[] args) {
        Demo d = new Demo();
        d.x = 9;
        show(d);
        System.out.println(d.x);  // 4
    }

    public static void show(Demo d){
        d.x = 4;
    }
}


class User {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
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
