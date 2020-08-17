package orientedObject02;

// 子类实例化过程
public class ExtendsDemo03 {
    public static void main(String[] args) {
        /* 
         * 对象实例化过程：
         * Person p = new Person();
         * 1、JVM会读取指定路径下的Person.class文件并加载进内存,如果有父类,会先加载Person的父类
         * 2、在堆内存开辟空间,分配地址,默认初始化
         * 3、子类构造函数的super()会先调用父类空参构造函数初始化
         * 4、子类的显式初始化
         * 5、子类构造代码块初始化(如果父类有构造代码块也类似)
         * 6、子类构造函数初始化
         * 7、初始化完毕后将地址值赋给引用变量
         * 
         * 创建对象>>默认初始化>>显示初始化>>构造代码块初始化>>构造函数初始化       (初始化都是先父类后子类)
         * 如果有静态代码块,那么会先执行,因为静态优先于对象存在
         */
    	
        Zii z = new Zii();
        z.show();
        
//         父类静态代码块5
//         子类静态代码块...10
//         父类构造代码块5
//         子类show方法...10
//         子类构造代码块...10
//         子类show方法...10
//         子类show方法...20
    }
}

class Fuu{
    static int num = 5;
    
    static{
    	System.out.println("父类静态代码块"+num);
    }
    
    {
        System.out.println("父类构造代码块"+num);
    }
    
    Fuu(){
        // super();
        // 父类构造函数初始化
        show();
        // return;
    }
    
    void show(){
        System.out.println("父类show方法");
    }
}

class Zii extends Fuu{
    // 成员变量显式初始化
    static int num = 10;
    
    static{
    	System.out.println("子类静态代码块"+"..."+num);
    }
    
    {
        System.out.println("子类构造代码块"+"..."+num);
    }
    
    Zii(){
        // super();
        show();
        // 子类构造函数初始化 
        num = 20;
        // return;
    }
    
    void show(){
        System.out.println("子类show方法"+"..."+num);
    }
}
