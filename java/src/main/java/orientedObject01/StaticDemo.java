package orientedObject01;

public class StaticDemo {
    public static void main(String[] args) {
        /**
         * static关键字：
         * 1、static修饰的的变量和方法是共享数据,对象中存储的是特有数据
         * 2、static随着类的加载而加载,所以优先于对象而存在,所以静态成员可以被类名直接调用
         * 3、main方法是静态的
         *
         * 注意事项:
         * 1、静态只能访问静态,非静态访问一切(因为非静态成员只能被对象调用,然而静态成员优先于对象存在,先来的看不到后来的,后来的能看到先来的)
         * 2、静态方法中不可以使用this和super关键字(没有对象嘛,静态成员优先于对象存在)
         * 3、static只能修饰成员变量,不能修饰局部变量(static修饰的变量随着类加载,是先于方法存在的)
         *
         * 成员变量和静态变量区别:
         * 1、生命周期不同：成员变量随对象存在而存在,对象被回收就释放;静态变量随类存在而存在,类消失就消失
         * 2、调用方法不同：成员变量只能被对象调用;静态变量可以被对象调用,还能被类名直接调用(因为有时候不需要创建对象)
         * 3、存储位置不同：成员变量存放在堆内存的对象中(对象特有数据);局部变量存放在栈内存;静态变量存放在方法区(共享数据区)
         *
         * 1、静态代码块：随着类的加载而执行,且只执行一次,可以给类初始化
         * 2、构造代码块：类中的独立代码块,每次创建对象时都会调用,可以给所有对象初始化
         * 3、构造函数：给对应的对象针对性的初始化
         * 执行顺序：静态代码块 > 构造代码块 > 构造函数
         */

        User u = new User();
        u.name = "moon";
        u.show();
        // 静态变量可以直接被类名调用,避免创建对象
        System.out.println(User.country);
//        show();     // 编译不通过,因为show()是非静态,main方法是静态的,访问不到
        // 静态访问不到非静态,那就创建对象来调用非静态成员吧
        new StaticDemo().show();

        People p = new People("grubby");
        p.speak();
    }

    public void show(){
        System.out.println("not static func");
    }
}

class User{
    // 成员变量(实例变量)
    String name = "grubby";
    // 静态变量(类变量)
    static String country = "cn";
    // 成员方法
    public void show(){
        System.out.println(this.name + ":" + User.country); // this和User可以省略
    }
}

class People{
    private String name;
    // 静态代码块
    static{
        System.out.println("---静态代码块---");
    }
    // 构造代码块
    {
        System.out.println("---构造代码块---");
        // 每个人都有吃饭功能,放构造代码块里就好
        eat();
    }
    // 构造函数
    People(String name){
        this.name = name;
    }
    private void eat(){
        System.out.println("今天加鸡腿");
    }
    void speak(){
        System.out.println("name="+name);
    }
}