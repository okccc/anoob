package orientedObject02;

public class AbstractDemo01 {
    public static void main(String[] args) {
        /* 
         * 抽象类：模糊的,不具体的,没有足够多的信息来描述对象
         * 特点： 1、方法只有声明没有实现(方法体)的时候,该方法就是抽象方法,需要被abstract修饰,抽象方法必须定义在抽象类中
         *      2、抽象类不能被实例化,因为没有方法体,对象调用抽象方法没有意义
         *      3、抽象类的子类必须覆盖抽象类的所有抽象方法后才能实例化,不然该子类还是抽象类
         * 
         * 常见问题：
         * 1、抽象类有构造函数吗？
         *    有,可以给子类初始化
         *    
         * 2、抽象类本身一定是父类吗？
         *    是的,因为需要子类覆盖其抽象方法后对子类实例化
         * 
         * 3、抽象类可以不定义抽象方法吗？
         *    可以,不常用,AWT适配器对象就是这种类,目的就是不让该类创建对象
         *    abstract Demo{
         *        // 有方法体但是没内容
         *        void show1(){
         *        
         *        }
         *        void show2(){
         *        
         *        }
         *    }
         *    
         * 4、abstract不能和哪些关键字共存？
         *    private：抽象方法必须由子类重写才有意义,加private子类就访问不到了
         *      final：抽象方法必须由子类重写才有意义,加final后类不能被继承,方法不能被覆盖
         *     static：静态表示可以用类名直接调用方法,然而调用抽象方法没有意义,必须由子类重写后对子类实例化调用
         * 
         * 5、抽象类和一般类区别？
         *    相同点：两者都是描述事物的,都在内部定义了成员
         *    不同点：一般类不能定义抽象方法(有抽象方法的类一定是抽象类了),是可以被实例化的
         *          抽象类可以定义抽象方法也可以定义非抽象方法,是不能被实例化的
         */
    }
}

abstract class Person{
    // 语句结束两种方式：{}或者;
    // 抽象方法是没有方法体的
    abstract void study();
}

class Student extends Person{
    void study(){
        System.out.println("学生要学习");
    }
}

class Teacher extends Person{
    void study(){
        System.out.println("老师要学习");
    }
}
