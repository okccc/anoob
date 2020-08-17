package multiThread;

public class BankDemo {
    public static void main(String[] args) {
        /*
         * 需求:有3个人同时去银行存钱,每人每次存1000,各存5次,如何反应银行钱的变化
         * 
         * 分析:1、3个人同时存钱说明要用到多线程
         *     2、各存5次说明要循环5次 
         *     3、每次存1000要写一个add方法
         */
        Bank b = new Bank();
        Thread t1 = new Thread(b);
        Thread t2 = new Thread(b);
        Thread t3 = new Thread(b);
        t1.start();
        t2.start();
        t3.start();
    }
}

class Bank implements Runnable{
    private int sum;
    private Object obj = new Object();

    @Override
    public void run() {
        synchronized (obj) {
            for(int x=1;x<=5;x++){
                sum += 1000;
                System.out.println(Thread.currentThread().getName()+"....."+"sum="+sum);
            }
        }
    }
    
}