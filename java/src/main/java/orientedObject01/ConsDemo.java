package orientedObject01;

public class ConsDemo {
    public static void main(String[] args) {
        /*
         * 构造函数:创建对象时调用的函数,用来给对象初始化
         *    特点:1、函数名与类名相同
         *        2、没有返回值类型
         *        3、不用写return语句 
         *             
         * 创建的对象都必须要通过构造函数进行初始化
         * 一个类中如果没有定义构造函数,会有一个默认的空参构造；如果类中定义了构造函数,那么默认的空参构造就不存在了
         * 
         * 构造函数可以有多个,用于对不同的对象针对性的初始化,多个构造函数是以重载的形式体现的
         * 
         * 构造函数和一般函数区别:构造函数是在对象创建时就会调用的,给对象初始化,只需调用一次
         *                   一般函数是对象创建之后需要函数功能时才会调用,可以调用多次
         *                   
         * 注意点：1、一般函数不能调用构造函数
         *       2、构造函数前面加上void就变成了一般函数
         *       3、构造函数是有return语句的
        */
        
        // 创建对象时先是在堆内存默认初始化,然后是构造函数初始化
        Person p = new Person(); 
        p.speak();
        Person p1 = new Person("moon");
        p1.speak();
        Person p2 = new Person("fly",20);
        p2.speak();
    }

}

class Person{
    private String name;
    private int age;
    // 空参构造
    Person(){
        name = "grubby";
        age = 15;
    }
    // 带参构造
    Person(String n){
        name = n;
    }
    // 带参构造
    Person(String n,int a){
        name = n;
        age = a;
    }
    
    public void speak(){
        System.out.println(name+"....."+age);
    }
    
    
}
