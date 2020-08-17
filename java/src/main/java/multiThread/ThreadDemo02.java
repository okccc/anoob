package multiThread;

public class ThreadDemo02 {
    public static void main(String[] args) {
        /* 
         * 创建线程方式二:实现Runnable接口
         * 
         * 1、自定义一个类实现Runnable接口
         * 2、覆盖Runnable接口中的run方法
         * 3、通过Thread类创建线程对象,并将实现了Runnable接口的子类对象作为Thread类的构造函数的参数传入
         *   (线程要执行的任务代码封装在子类对象的run方法中,在线程对象创建时就要明确要运行的任务)
         * 4、调用start方法开启线程,执行run方法
         * 
         * 优点:1、避免了java单继承的局限性(如果子类本身有父类就不能再继承Thread类了)
         *     2、适合多线程处理同一资源的情况(卖票),在子类中单独封装线程要运行的任务代码和相关数据资源,将线程和任务代码有效分离,体现了面向对象的思想
         */
        
        // 创建子类对象
        Demo2 d = new Demo2();
        // 线程1
        Thread t1 = new Thread(d);
        // 线程2
        Thread t2 = new Thread(d);
        
        t1.start();
        t2.start();
        
        // 执行main方法的线程
        for(int x=1;x<=10;x++){
            System.out.println(x+"....."+Thread.currentThread().getName());
        }
        
    }
}

class Demo2 implements Runnable{

    @Override
    public void run() {
        for(int x=1;x<=10;x++){
            System.out.println(x+"....."+Thread.currentThread().getName());
        }
    }
    
}

// 下面代码错在哪？
// class Test01 implements Runnable{
// 
//     // 这里的run方法只是普通方法,并没有对Runnable接口的run()重写,所以当前类仍然是抽象类
//     public void run(Thread t){
//         System.out.println("hehe");
//     }
// 
// }

// 快速生成线程的方式
class Test02{
    public static void main(String[] args) {
        // 线程一
        for(int x=1;x<=10;x++){
            System.out.println(Thread.currentThread().getName()+"....."+x);
        }
        
        // 线程二
        new Thread(){
            public void run(){
                for(int x=1;x<=10;x++){
                    System.out.println(Thread.currentThread().getName()+"....."+x);
                }
            }
        }.start();
        
        // 线程三
        Runnable r = new Runnable(){
            public void run(){
                for(int x=1;x<=10;x++){
                    System.out.println(Thread.currentThread().getName()+"....."+x);
                }
            }
        };
        Thread t = new Thread(r);
        t.start();
    }
}

class Test03{
    public static void main(String[] args) {
//         new Thread(new Runnable(){
//             public void run(){
//                 System.out.println("Runnable");
//             }
//         }){
//             public void run(){
//                 System.out.println("Thread");
//             }
//         }.start();
        
        new Thread(new Runnable() {
            
            @Override
            public void run() {
                System.out.println("以匿名内部类的形式创建线程");
            }
        }).start();
    }
}