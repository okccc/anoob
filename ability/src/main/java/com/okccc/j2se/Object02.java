package com.okccc.j2se;

import java.io.Serializable;

public class Object02 {
    public static void main(String[] args) {
        /*
         * 继承
         * 1.提高代码复用性
         * 2.让类与类产生关系,是多态的前提
         * java是单继承多实现,不能多继承(菱形问题：如果多个父类中有同名方法,会出现调用的不确定性)
         * 当要使用一个继承体系时:查看顶层类,了解该体系最基本的功能,创建体系中的最子类对象,完善功能
         * 方法重写：当子父类出现同名方法时会运行子类方法,子类权限必须大于父类才能重写,静态只能重写静态
         *
         * super
         * 当本类中成员变量和局部变量同名时,用this关键字区分,this代表一个本类对象的引用
         * 当子父类中成员变量同名时,用super关键字区分,super代表一个父类空间
         * 子类构造函数的第一行有个默认的隐式语句super()访问父类空参构造,也可以super(...)调用父类带参构造,super语句必须放在第一行,
         * 因为父类初始化要先执行,如果子类构造函数使用this调用本类其它构造函数那么super就不存在,因为this和super都必须放在第一行做初始化
         * 子类为什么要访问父类构造函数呢？
         * 因为子类继承父类,在使用父类内容之前,得先看看他是怎么初始化的吧
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
         * (静态代码块) > 创建对象 > 默认初始化 > 显示初始化 > 构造代码块初始化 > 构造函数初始化   (初始化都是先父类后子类)
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
         * 抽象类有构造函数吗？
         * 有,给子类初始化
         * 抽象类本身一定是父类吗？
         * 是的,因为需要子类重写其抽象方法后对子类实例化
         * 抽象类可以不定义抽象方法吗？
         * 可以,不常用,AWT适配器对象就是这种类,目的就是不让该类创建对象
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
         */
    }
}

class Father {
    static int num;
    static{
        System.out.println("父类静态代码块" + "..." + num);
    }
    {
        System.out.println("父类构造代码块" + "..." + num);
        show();
    }
    Father(){
        System.out.println("父类空参构造" + "..." + num);
    }
    Father(int num){
        System.out.println("父类带参构造" + "..." + num);
    }
    private void show() {
        System.out.println("父类show方法" + "..." + num);
    }
}

class Son extends Father {
    static int num = 10;
    public static void main(String[] args) {
        new Son(10);
    }
    static {
        System.out.println("子类静态代码块" + "..." + num);
    }
    {
        System.out.println("子类构造代码块" + "..." + num);
        show();
    }
    Son(){
        super();
//        super(10);
        System.out.println("子类空参构造" + "..." + num);
    }
    Son(int num){
        this();
        System.out.println("子类带参构造" + "..." + num);
    }
    private void show() {
        System.out.println("子类show方法" + "..." + num);
    }
}

/*
 * 员工案例
 * 程序员有姓名,工号,薪水,工作内容
 * 项目经理有姓名,工号,薪水,工作内容和奖金
 *
 * 1.程序员和项目经理存在共性内容(变量和方法),可以向上抽取成抽象类
 * 2.二者工作内容(方法)不一样,可以写成抽象方法由子类重写
 * 3.奖金是项目经理特有(变量),需要在子类中自定义
 */
abstract class Employee {
    // 父类公有变量
    private String name;
    private int id;
    private double salary;
    // 构造器
    public Employee(String name, int id, double salary) {
        this.name = name;
        this.id = id;
        this.salary = salary;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public double getSalary() {
        return salary;
    }
    public void setSalary(Double salary) {
        this.salary = salary;
    }
    // 抽象方法
    abstract void work();
}

class Programmer extends Employee {
    public static void main(String[] args) {
        Programmer p = new Programmer("grubby", 1, 8000);
        p.work();
    }
    // 调用父类构造器
    public Programmer(String name, int id, double salary) {
        super(name, id, salary);
    }
    @Override
    void work() {
        System.out.println("程序员干活");
    }
}

class Manager extends Employee {
    // 子类特有变量
    private double bonus;
    public static void main(String[] args) {
        Manager m = new Manager("moon", 2, 9000, 1000);
        m.work();
    }
    // 调用父类构造器
    public Manager(String name, int id, double salary, double bonus) {
        super(name, id, salary);
        this.bonus = bonus;
    }
    public double getBonus() {
        return bonus;
    }
    public void setBonus(Double bonus) {
        this.bonus = bonus;
    }
    @Override
    void work() {
        System.out.println("项目经理装逼");
    }
}

// 定义java模板类
@SuppressWarnings("unused")
class Person implements Serializable, Comparable<Person> {

    private static final long SERIAL_VERSION_UID = 5898267155926398171L;
    private String name;
    private int age;
    private transient String idcard;

    public Person() {
        super();
    }

    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public Person(String name, int age, String idcard) {
        this.name = name;
        this.age = age;
        this.idcard = idcard;
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

    public String getIdcard() {
        return idcard;
    }

    public void setIdcard(String idcard) {
        this.idcard = idcard;
    }

    @Override
    public String toString() {
        // 重写toString方法,返回该对象的字符串表现形式(通常会写的简单易懂)
//        return name + ": " + age + ": " + idcard;
        return "Person[" + "name: " + name + ", age: " + age + ", idcard: " + idcard + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Person){
            Person p = (Person)obj;
            return this.name.equals(p.name) && this.age==p.age;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + age * 99;
    }

    @Override
    public int compareTo(Person p) {
        // 按年龄排序
//       int temp = this.age - p.age;
//       return temp==0 ? this.name.compareTo(p.name) : temp;
        // 按名字排序
        int temp = this.name.compareTo(p.name);
        return temp==0 ? this.age - p.age : temp;
    }
}