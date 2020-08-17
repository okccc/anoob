package orientedObject01;

// 三大引用类型：class、array、interface
public class ObjectDemo01 {
    
    public static void main(String[] args) {
        /*
         * 类与对象关系:类是对事物的描述
         *           对象是类的实例化,new出来的那些
         * 定义类就是定义类中的成员:成员变量（属性）
         *                    成员方法（行为）                                         
         */
        
        Car c = new Car();
        c.num = 3;
        c.color = "red";
        c.run();
        
        /*匿名对象：没有名字的对象
                 1、当对象对方法只调用一次的时候,可使用匿名对象
                 2、匿名对象可以作为实际参数传递
        */
        // new Car();
        new Car().run();
        show(c);
        show(new Car());
    }
    
    /*
     * 成员变量和局部变量区别:1、成员变量定义在类中,整个类都可以访问；
     *                     局部变量定义在语句,方法和代码块中,只在所属区域有效；
                        2、 成员变量存放在堆内存的对象中,有默认初始化值,随对象存在而存在；
                                                                   局部变量存放在栈内存的方法中,没有默认初始化值,随所属区域的执行而存在；
     */
    public static void show(Car c){
        c.num = 4;
        c.color = "white";
        c.run();
    }
    
}

class Car{
    int num;
    String color;
    void run(){
        System.out.println(num+"..."+color);
    }
}
