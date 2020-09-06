package basic;

public class Object03 {
    public static void main(String[] args) {
        /*
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
         * 内部类可以简写成匿名内部类,其实就是一个匿名的子类对象,匿名内部类必须继承一个外部类或者实现一个接口
         * 格式：直接new 父类or接口<T>(){子类内容}
         * 抽象类和接口不能实例化,{}里面内容是子类方法重写,所以匿名内部类创建的其实是子类对象,然后再调用子类重写后的方法
         * 用途：抽象类和接口的子类都可以用匿名内部类实现,尤其是类很小或只用一次的时候,单独创建类很浪费可以使用匿名内部类
         *      多线程实现Runnable接口就是用的匿名内部类
         *
         * 枚举
         * java中的特殊类,使用enum关键字代替class,通常表示一组常量,常量之间用逗号分隔,比如一年四季/一星期七天/东南西北...
         *
         * 注解
         * 用于修饰java中的数据(类/属性/方法/构造器...),不改变程序逻辑,但可以被编译器解析并做相应处理
         * @Override：只能修饰方法,检测该方法是否是有效重写,如果不是编译会报错
         * @Deprecated：表明被修饰的数据已过时,可以修饰CONSTRUCTOR, FIELD, LOCAL_VARIABLE, METHOD, PACKAGE, PARAMETER, TYPE
         * @SuppressWarnings("")：抑制代码中的编译警告,可以修饰TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE
         *
         * 异常
         * java通过面向对象的思想将问题封装成了对象,异常有两种处理方式
         * 1.捕获异常：try{执行可能产生异常的代码} catch(捕获异常){} finally{无论是否异常最终都会执行的代码}
         * 2.抛出异常(默认)：将异常抛给调用者处理,调用者可以捕获也可以继续往上抛,最终会抛给jvm,jvm默认在控制台打印错误堆栈日志
         *   开发MVC框架时一般Dao层往上抛给Service层,Service层再往上抛给View层,View是展示层直接面向用户不能再抛了得try catch
         * throw和throws区别？
         * throw是手动生成异常,后面跟异常对象,定义在方法体中(不常用)
         * throws是异常处理的第二种方式,后面跟异常类型,定义在方法声明中
         * 子类重写父类方法时,父类有异常子类必须抛出相同异常或其子类,父类没有异常子类也不能抛异常只能try catch
         */

        Animal a = new Cat();
        a.eat();  // cat eats fish
        show(new Cat());
        show(new Dog());

        Season s1 = Season.SPRING;
        Season s2 = Season.SUMMER;
        Season s3 = Season.AUTUMN;
        Season s4 = Season.WINTER;
        System.out.println(s1 +" "+ s2 +" "+ s3 +" "+ s4);
    }

    public static void show(Animal animal) {
        // 使用多态时要注意代码的健壮性,instanceof用于判断对象的具体类型
        if (animal instanceof Cat) {
            // 向下转型
            Cat c = (Cat) animal;
            c.play();
        } else {
            Dog d = (Dog) animal;
            d.look();
        }
    }
}

interface Animal {
    void eat();
}
class Cat implements Animal {
    @Override
    public void eat() {
        System.out.println("cat eats fish");
    }
    public void play() {
        System.out.println("cat also plays mouse");
    }
}
class Dog implements Animal {
    @Override
    public void eat() {
        System.out.println("dog eats meat");
    }
    public void look() {
        System.out.println("dog also looks door");
    }
}

// 抽象类
abstract class Abs {
    abstract void show();
}
// 接口
interface Inter {
    void show1();
    void show2();
}
// 内部类
class Outer{
    int num = 10;
    public static void main(String[] args) {
        Outer outer = new Outer();
        outer.inner01();
        outer.inner02();
        outer.method();
    }
    // 成员内部类
    private class Inner1{
        int num = 20;
        void show(){
            int num = 30;
            // this指当前对象
            System.out.println(num + ".." + this.num + ".." + Outer.this.num);
        }
    }
    // 静态内部类
    private static class Inner2{
        void speak(){
            System.out.println("静态内部类的非静态方法");
        }
        // 如果内部类中定义了静态成员,那么其所属的内部类也要是静态的,随外部类的加载而加载
        static void function(){
            System.out.println("静态内部类的静态方法");
        }
    }
    // 局部内部类
    void method(){
        class Inner3{
            void show(){
                System.out.println(num);
            }
        }
        new Inner3().show();
    }
    // 匿名内部类1：继承父类
    void inner01(){
        new Abs() {
            @Override
            void show() {
                System.out.println("111");
            }
        }.show();
    }
    // 匿名内部类2：实现接口
    void inner02(){
        Inter inter = new Inter() {
            @Override
            public void show1() {
                System.out.println(num);
            }
            @Override
            public void show2() {
                System.out.println(num);
            }
        };
        inter.show1();
        inter.show2();
    }
}

// 枚举
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

// 异常
class Excep {
    public static void main(String[] args) {
        System.out.println(getNum());
    }
    public static int getNum() {
        int a = 10;
        int b = 0;
        try{
            b = a/0;
        } catch (Exception e) {
            // 打印异常类名和信息  java.lang.ArithmeticException: / by zero
            e.printStackTrace();
        } finally {
            System.out.println("...");
        }
        return b;
    }
}
