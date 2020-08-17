package multiThread;

// 验证同步函数的锁
public class SyncFuncDemo {
    public static void main(String[] args) {
        /* 
         * 同步函数的锁是固定的this,同步代码块的锁是任意对象,当锁对象是this时,同步代码块可以简写成同步函数
         * 所以一般情况下都使用同步代码块
         * 
         * 静态同步函数的锁是该函数所属的字节码文件对象,可以用getClass方法获取,也可以用当前    类名.class
         */
        
        // 创建子类对象
        Ticket t = new Ticket();
        // System.out.println("t:"+t);
        
        // 创建线程
        Thread t1 = new Thread(t);
        Thread t2 = new Thread(t);
        // 调用start方法,开启线程
        t1.start();
        // 注意：t.flag=false;是main线程在执行,创建Thread-0线程的时候,main线程仍然有执行权将flag赋值false,导致Thread-0和Thread-1线程读取的flag都是false,必须让main线程先停一下
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        t.flag = false;
        t2.start();
    }
}

class Ticket implements Runnable{
    private static int num = 100;
    Boolean flag = true;
    // Object obj = new Object();
    
    @Override
    public void run() {
        if(flag){
            // System.out.println("this:"+this);
            while(true){
                // 同步代码块
            	synchronized (this){
//                 synchronized (Ticket.class) {
                    // 要封装的代码
                    if(num>0){
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        System.out.println(Thread.currentThread().getName()+"..."+"同步代码块"+"..."+"正在售出第"+num--+"张票");
                    }
                }
            }
        }else{
            // System.out.println("this:"+this);
            while(true){
                // 非静态同步函数(对象调用)
                this.show();
                // 静态同步函数(类名直接调用)
//                 Ticket.show1();
            }
        }
    }
    
    // 分析:synchronized关键字本身并不带有锁对象,这里应该是函数带有锁对象,这个锁就是调用函数的对象this
    private synchronized void show() {
        if(num>0){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName()+"..."+"非静态同步函数"+"..."+"正在售出第"+num--+"张票");
        }
    
    }
    
    /*private static synchronized void show1() {
        if(num>0){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName()+"..."+"静态同步函数"+"..."+"正在售出第"+num--+"张票");
        }
        
    }*/
    
}