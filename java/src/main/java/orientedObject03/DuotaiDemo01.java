package orientedObject03;

public class DuotaiDemo01 {
    public static void main(String[] args) {
        /* 
         * 多态:一个对象对应着不同的形态,即父类或者接口的引用指向其子类对象
         * 
         * 好处:提高代码扩展性和健壮性
         * 弊端:父类引用不能调用子类特有功能(其实是隐藏子类从而限制使用子类特有功能)
         * 案例:a、List<String> list = new ArrayList<String>();
         *     b、ArrayList<String> list = new ArrayList<String>();
         *     List集合通常用写法a而不是写法b,面向接口编程,这样list调用的都是List接口的方法,如果代码需要用到LinkedList类的方法,只要改成
         *     List<String> list = new LinkedList<String>();其他代码都不用动,如果代码里已经用了ArrayList类的方法,改动就很麻烦了
         * 
         * 前提:1、要有继承或实现关系
         *     2、要有方法重写
         *     3、父类引用指向子类对象(向上转型)
         */
        
        // 向上转型：将子类对象提升为父类类型,隐藏子类从而限制使用子类的特有功能
        Animal a = new Cat();// 一个对象,两种形态
        a.eat();
        // 向下转型：继续使用子类的特有功能
        Cat c = (Cat) a;
        c.catchMouse();
        
        // 对于转型,始终是特定的子类对象在做类型转换
//         Dog d = (Dog) a;
//         d.lookDoor();          // java.lang.ClassCastException类型转换异常
        
        method(new Cat());
        // method(new Dog());
        // method(new Pig());
    }
    
    static void method(Animal a){
        a.eat();
        // 使用多态时要注意代码的健壮性
        // instanceof用于判断对象具体类型,只在引用数据类型中用,可以在向下转型前提高代码健壮性
        if(a instanceof Cat){
            Cat c = (Cat) a;
            c.catchMouse();
        }else if(a instanceof Dog){
            Dog d = (Dog) a;
            d.lookDoor();
        }else{
            Pig p = (Pig) a;
            p.kengCi();
        }
    }
}

abstract class Animal{
    
    abstract void eat();
}

class Cat extends Animal{

    @Override
    void eat() {
        System.out.println("猫吃鱼");
    }
    
    void catchMouse(){
        System.out.println("猫还能抓老鼠");
    }
    
}

class Dog extends Animal{

    @Override
    void eat() {
        System.out.println("狗吃骨头");
    }
    
    void lookDoor(){
        System.out.println("狗还能看门");
    }
    
}

class Pig extends Animal{

    @Override
    void eat() {
        System.out.println("猪吃饲料");
    }
    
    void kengCi(){
        System.out.println("猪还能拱白菜");
    }
}