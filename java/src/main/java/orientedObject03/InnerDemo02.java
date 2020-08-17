package orientedObject03;

public class InnerDemo02 {
    public static void main(String[] args) {
        /* 
         * 匿名内部类:是内部类的简写格式,其实就是一个匿名的子类对象
         *     前提:匿名内部类必须继承一个外部类或者实现一个接口
         *     格式:new 父类or接口(){子类内容}
         *     抽象类和接口不能实例化,{}里面内容是子类方法重写,所以匿名内部类创建的其实是子类对象,然后再调用子类重写后的方法
         *     
         * 用途:抽象类和接口的子类都可以用匿名内部类实现,特别是这个类很小或者只使用一次的时候,单独创建一个类很浪费,就可以用匿名内部类
         *     多线程就可以用匿名内部类实现,继承Thread类或者实现Runnable接口
         *     
         */
        new Outerr().method();
        new Outeer().method();
    }
}

// 抽象类
abstract class Person{
    abstract void show();
}

// 接口
interface Inter{
    public abstract void show1();
    public abstract void show2();
}

// 内部类继承父类
class Outerr{
    int num = 10;
    // 常规写法
//     void method(){
//         new Innerr().show();
//     }
//     
//     class Innerr extends Person{
// 
//         @Override
//         void show() {
//             System.out.println(num);
//         }
//         
//     }
    
    // 匿名内部类写法
    void method(){
        // 直接new 父类名
        new Person(){

            @Override
            void show() {
                System.out.println(num);
            }
            
        }.show();
    }
}

// 内部类实现接口
class Outeer{
    int num = 20;
    
    // 常规写法
//     void method(){
//         Inneer inneer = new Inneer();
//         inneer.show1();
//         inneer.show2();
//     }
//     
//     class Inneer implements Inter{
// 
//         @Override
//         public void show1() {
//             System.out.println(num);
//         }
// 
//         @Override
//         public void show2() {
//             System.out.println("...");
//         }
//         
//     }
    
    // 匿名内部类写法
    void method(){
        // 直接new 接口名
        Inter inter = new Inter(){

            @Override
            public void show1() {
                System.out.println(num);
            }

            @Override
            public void show2() {
                System.out.println("...");
            }
            
        };
        inter.show1();
        inter.show2();
    }
}