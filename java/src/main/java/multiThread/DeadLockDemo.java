package multiThread;

public class DeadLockDemo {
    public static void main(String[] args) {
        /* 
         * 死锁：常见情景之一：同步嵌套
         */
        
        Test a = new Test(true);
        Test b = new Test(false);
        Thread t1 = new Thread(a);
        Thread t2 = new Thread(b);
        t1.start();
        t2.start();
    }
}


class Test implements Runnable{
    private Boolean flag;
    
    private static final Test lock1 = new Test();
    private static final Test lock2 = new Test();
    
    Test(){
        
    }
    
    Test(Boolean flag){
        this.flag = flag;
    }
    
    
    @Override
    public void run() {
        
        System.out.println("lock1="+lock1);
        System.out.println("lock2="+lock2);
        
        if(flag){
            while(true){
                synchronized (lock1) {
                    System.out.println(Thread.currentThread().getName()+"....."+"lock1");
                    synchronized (lock2) {
                        System.out.println(Thread.currentThread().getName()+"....."+"lock2");
                    }
                }
            }
        }else{
            while(true){
                synchronized (lock2) {
                    System.out.println(Thread.currentThread().getName()+"....."+"lock2");
                    synchronized (lock1) {
                        System.out.println(Thread.currentThread().getName()+"....."+"lock1");
                    }
                }
            }
        }
    }
    
}
