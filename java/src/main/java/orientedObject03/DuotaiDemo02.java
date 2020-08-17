package orientedObject03;

public class DuotaiDemo02 {
    public static void main(String[] args) {
        /* 
         * 多态中的成员特点：
         * 1、成员变量
         *    编译和运行都参考 = 左边
         * 2、成员方法（非静态方法）
         *    编译看 = 左边,运行看 = 右边     （因为非静态方法要由子类对象调用,子类会将父类的同名方法覆盖掉）
         * 3、静态方法
         *    编译和运行都看 = 左边
         */
        
        Fu f = new Zi();// 父类类型的引用变量指向子类对象 
        
        // 调用成员变量
        System.out.println(f.num);
        // 调用非静态方法
        f.show();
        // 调用静态方法
//         f.method();// 注意看黄线提示：静态方法就不需要创建对象了,可以直接由类名调用
        
        Fu.method();
        Zi.method();
        
    }
}

class Fu{
    // 成员变量
    int num = 5;
    // 成员方法(非静态方法)
    void show(){
        System.out.println("父类非静态方法");
    }
    // 静态方法
    static void method(){
        System.out.println("父类静态方法");
    }
}

class Zi extends Fu{
    int num = 6;
    
    void show(){
        System.out.println("子类非静态方法");
    }
    
    static void method(){
        System.out.println("子类静态方法");
    }
}