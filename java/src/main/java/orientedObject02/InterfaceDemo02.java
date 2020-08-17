package orientedObject02;

public class InterfaceDemo02 {
    public static void main(String[] args) {
        /*
         * 抽象类与接口对比：
         * 相同点：都是不断向上抽取而来
         * 不同点：1、 抽象类需要被继承,且只能单继承
         *          接口需要被实现,且可以多实现
         *       2、 抽象类可以定义抽象方法和非抽象方法,子类继承可以直接调用非抽象方法
         *          接口只能定义抽象方法,由子类去实现
         *       3、 抽象类的继承是is a关系,定义体系的共性内容
         *          接口的实现是like a关系,定义体系的额外功能
         */
    }
}

abstract class Animal01{
    // 吃饭
    abstract void eat();
    // 睡觉
    abstract void sleep();
}

interface Guide{
    // 导盲
    public abstract void guide();
}

// 狗具备动物的基本行为,还有导盲的额外功能
class Dog01 extends Animal01 implements Guide{

    @Override
    public void guide() {
        
    }

    @Override
    void eat() {
        
    }

    @Override
    void sleep() {
        
    }
    
}