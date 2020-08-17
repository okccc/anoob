package orientedObject02;

public class InterfaceDemo01 {
    public static void main(String[] args) {
        /* 
         * 接口:当一个抽象类中都是抽象方法时,这个类就可以用接口表示,关键字是interface
         *     接口中的成员都有固定的修饰符,都是公共的权限public
         *     1、全局常量
         *        public static final 
         *     2、抽象方法
         *        public abstract
         *        
         * 作用：接口的出现解决了java单继承的弊端
         * 
         * 接口好处:1、提高代码健壮性
         *        2、降低耦合度
         *        3、提高代码扩展性
         */
        DemoImpl d = new DemoImpl();
        d.show();
        int i = d.add(3, 5);
        System.out.println(i);
    }
}

interface A{
    // 全局常量
    public static final int NUM = 10;
    // 抽象方法
    public abstract void show();
    public abstract int add(int a, int b);
}

interface B{
    public abstract void show();
}

/*
 * java不支持多继承,因为一般方法有方法体,如果多个父类方法同名,会出现调用的不确定性
 * java支持多实现,因为接口都是抽象方法,没有方法体,必须由子类重写后创建子类对象再调用,所以不管接口中有多少同名方法都无所谓,都是要被子类覆盖的
 */
class DemoImpl implements A,B{

    @Override
    public void show() {
        // 这里写方法体
        System.out.println("...");
    }

    @Override
    public int add(int a, int b) {
        // 添加方法体
        return a+b;
    }
    
}

abstract class C{
    abstract void show();
}

// 一个类继承父类的同时还能实现多个接口
class D extends C implements A,B{

    @Override
    public int add(int a, int b) {
        return 0;
    }

    @Override
    public void show() {
        // 
    }

}

// 接口与接口之间是继承关系,且可以多继承,因为接口中都是抽象方法,不存在调用不确定性问题
interface E extends A,B{
    public abstract void speak();
}

class G implements E{

    @Override
    public void show() {
        
    }

    @Override
    public int add(int a, int b) {
        return 0;
    }

    @Override
    public void speak() {
        
    }
    
}