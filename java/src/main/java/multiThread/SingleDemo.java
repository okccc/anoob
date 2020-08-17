package multiThread;

public class SingleDemo {
    public static void main(String[] args) {
        /*
         * 单例模式:保证该类中只有一个对象
         * 
         * 思路:1、在本类创建一个对象
         *     2、将构造函数私有化,不让其它程序new对象
         *     3、对外提供get方法
         */
        
        Single s1 = Single.getInstance();
        Single s2 = Single.getInstance();
        System.out.println(s1==s2);
        
        Person t1 = Person.getInstance();
        Person t2 = Person.getInstance();
        System.out.println(t1==t2);
        t1.setNum(20);
        t2.setNum(30);
        System.out.println(t1.getNum()+"....."+t2.getNum());// 两个值相同说明是同一个对象
    }
}

class Single{
  // 饿汉式：类加载时就创建好对象
    public static Single s = new Single();
    
    private Single(){
            
    }
    
    public static Single getInstance(){
        return s;
    }
	
}

class Single2{
    // 懒汉式：类加载时先不创建对象,等到要用的时候再创建对象
    public static Single2 s = null;
    
    private Single2(){
            
    }
    
    // 这里如果用到多线程的话可能会有线程安全问题
    public static Single2 getInstance(){
        // 这里加if判断可以提高效率,Thread-0执行后已经有了对象了,后续线程就不用再去判断锁了
        if(s==null){
            synchronized (Single2.class) {
                // 封装要同步的代码块
                if(s==null){
                    s = new Single2();
                }
            }
        }
        return s;
    }
	
}

class Person{
    int num;
    public static Person t = new Person();
    
    private Person(){
            
    }
    
    public int getNum() {
            return num;
    }

    public void setNum(int num) {
            this.num = num;
    }

    public static Person getInstance(){
            return t;
    }
	
}