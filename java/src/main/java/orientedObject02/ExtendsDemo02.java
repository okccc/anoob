package orientedObject02;

public class ExtendsDemo02 {
    public static void main(String[] args) {
        /*
         * 子父类中的构造函数：
         * 1、在子类构造函数的第一行有个默认的隐式语句super()
         * 2、子类所有构造函数都会默认访问父类的空参构造,或者用super(...)指明调用父类的带参构造
         * 3、如果子类构造函数使用this调用本类其它构造函数时,那super就不存在了,因为this和super都必须放在第一行,两个只能
         *   有一个,但是子类的其它构造函数还是会访问到父类的构造函数的,如果访问不到就不是继承了
         *   
         * 注意：super语句必须放在第一行,因为父类初始化要先执行
         *           
         * 子类为什么要访问父类构造函数呢？
         * 因为子类继承父类,获取父类内容,在使用父类内容之前,得先看看他是怎么初始化的吧      
         */
        
//         new Z();
        new Z(6);
    }
}

class F{
    int num;
    
    F(){
        System.out.println("父类空参构造"+"..."+num);
    }
    
    F(int num){
        System.out.println("父类带参构造"+"..."+num);
    }
}

class Z extends F{
    int num;
    
    Z(){
        // super(); 
        super(10);
        System.out.println("子类空参构造"+"..."+num);
        // return;
    }
    
    Z(int num){
        // super();
        this();
        System.out.println("子类带参构造"+"..."+num);
        // return;
    }
}

class Demo /*extends Object*/{
    // Object是所有类的父类
    Demo(){
        // super();
        // return;
    }
}