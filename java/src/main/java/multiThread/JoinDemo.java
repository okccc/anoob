package multiThread;

public class JoinDemo {
    public static void main(String[] args) throws Exception {
        
        Join j = new Join();
        Thread t1 = new Thread(j);
        Thread t2 = new Thread(j);
        
        t1.start();
        t2.start();
        
        // join方法：如果t1调用了join方法,那么t1后面的线程会搁置 ,等t1和它前面的线程(抢资源)都运行完了,才会运行后面的线程
        t1.join();
        
        for(int x=1;x<=100;x++){
            System.out.println(Thread.currentThread().getName()+"....."+x);
        }
    }
}

class Join implements Runnable{

    @Override
    public void run() {
        for(int x=1;x<=100;x++){
            System.out.println(Thread.currentThread().getName()+"....."+x);
        }
    }
    
}


