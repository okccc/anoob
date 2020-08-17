package multiThread;

public class ProConDemo {
    public static void main(String[] args) {
        /*
         * 多生产者多消费者问题
         * 
         * 等待唤醒机制有缺陷:
         * 1、if只会判断一次,当有多生产者多消费者时,会出现线程安全问题,线程获取执行权后到底要不要执行呢？所以应该改用while循环
         * 2、notify()只会唤醒线程池中任意一个线程,当有多个生产者和消费者时,如果唤醒的是己方线程就麻烦了,while循环 + notify()可能会导致死锁
         *   可以考虑用notifyAll()唤醒所有线程,但是这样效率会很低,可以用condition监视器优化
         */
        
        // 创建资源对象
        Resource1 r = new Resource1();
        // 创建生产消费者,将资源作为参数传入
        Producer p = new Producer(r);
        Consumer c = new Consumer(r);
        // 创建线程,将子类对象作为参数传入
        Thread t1 = new Thread(p);
        Thread t2 = new Thread(p);
        Thread t3 = new Thread(c);
        Thread t4 = new Thread(c);
        // 开启线程
        t1.start();
        t2.start();
        t3.start();
        t4.start();
    }
}

// 封装资源
class Resource1{
    private String name;
    private int num = 1;
    private Boolean flag = false;
    
    // 生产者任务代码
    public synchronized void set(String name) {
        while(flag){
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            
            e.printStackTrace();
        }
        
        this.name = name + num;
        num++;
        System.out.println(Thread.currentThread().getName()+"....."+"Producer生产了"+"....."+this.name);
        
        flag = true;
        notifyAll();
    }
    
    // 消费者任务代码
    public synchronized void out() {
        while(!flag){
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            
            e.printStackTrace();
        }
        
        System.out.println(Thread.currentThread().getName()+"....."+"Consumer消费了"+"....."+name);
        
        flag = false;
        notifyAll();
    }
    
}

// 生产者
class Producer implements Runnable{
    Resource1 r;
    Producer(Resource1 r){
        this.r = r;
    }

    @Override
    public void run() {
        while(true){
            r.set("鸡腿");
        }
    }
    
}

// 消费者
class Consumer implements Runnable{
    Resource1 r;
    Consumer(Resource1 r){
        this.r = r;
    }
    
    @Override
    public void run() {
        while(true){
            r.out();
        }
    }
    
}
