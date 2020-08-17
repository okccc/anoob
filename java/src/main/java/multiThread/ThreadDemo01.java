package multiThread;

public class ThreadDemo01 {
    public static void main(String[] args) {
        /* 
         * 多进程：提高CPU使用率,一个进程中至少有一个线程
         * 多线程：提高应用程序使用率,线程执行具有随机性(抢资源)
         *     
         * JVM启动时就启动了多线程,至少有两个:1、执行main方法的线程
         *                             2、负责垃圾回收的线程
         *                           
         * 创建线程方式一:继承Thread类
         * 
         * 1、自定义一个类继承Thread类
         * 2、覆盖Thread类中的run方法
         * 3、创建子类对象
         * 4、调用start方法开启线程,执行run方法
         * 
         * run()和start()区别：
         * run():仅仅是封装自定义线程要执行的任务代码,调用run方法时并没有启动该线程
         * start():首先启动线程,然后再由JVM去调用该线程的run()方法
         */
        
        // 线程1
        Demo d1 = new Demo("grubby");
        d1.setName("线程1");
        // 线程2
        Demo d2 = new Demo("moon");
        d1.setName("线程2");
        
//         d1.run();
//         d2.run();
        
        d1.start();
        d2.start();
        
        // 执行main方法的线程
        for(int x=1;x<=10;x++){
            System.out.println("helloworld"+"....."+x+"....."+Thread.currentThread().getName());
        }
        
}
	
}

class Demo extends Thread{
    // 成员变量
    private String name;
    
    // 构造函数
    public Demo(String name) {
        // super();
        this.name = name;
    }
    
    // 覆盖run方法,自定义任务内容
    public void run(){
        for(int x=1;x<=10;x++){
            // 可以通过 thread的getName方法获取线程的名称,主线程的名字就叫main
            System.out.println(name+"....."+x+"....."+Thread.currentThread().getName());
        }
    }
	
}
