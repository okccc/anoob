package multiThread;

public class ResourceDemo {
    public static void main(String[] args) {
        /* 
         * 多线程间通信:多线程在处理同一个资源,但是任务不同
         *           例如煤矿:输入-->煤堆-->输出
         *           
         * 单生产者单消费者问题:
         * 
         * 等待唤醒机制:
         * 1、wait():让线程处于冻结状态,暂时存储到线程池中
         * 2、notify():唤醒线程池中任意一个线程
         * 3、notifyAll():唤醒线程池中所有线程
         * 
         * 注意事项:
         * 1、wait()和notify()都定义在synchronized中:因为它们是控制多线程状态的方法,可能存在线程安全问题,所以要有同步锁
         * 2、wait()和notify()都定义在Object类中:因为wait和notify都是监视器(锁)的方法,而锁可以是任意对象,所以这些方法都定义在Object根类中
         * 
         * sleep()和wait()区别？
         * 1、sleep是Thread类的方法,wait是Object类的方法
         * 2、sleep必须指定时间,wait可以指定时间也可以不指定
         * 3、sleep不释放锁(时间到了自动醒),wait释放锁(等待notify来救它)
         */
        
        // 创建资源
        Resource r = new Resource();
        // 创建子类对象,将资源作为参数传入
        Input in = new Input(r);
        Output out = new Output(r);
        // 创建线程,将子类对象作为参数传入
        Thread t1 = new Thread(in);
        Thread t2 = new Thread(out);
        // 开启线程
        t1.start();
        t2.start();
    }
}

// 封装资源
class Resource{
    private String name;
    private String race;
    private Boolean flag = false;
    
    // 封裝Input线程任务代码(因为此时锁对象是this,所以可以用同步函数代替)
    public synchronized void set(String name,String race){
        // 想要输入和输出线程结果一一对应而不是成片成片,采用等待唤醒机制
        if(flag){
            try {
                this.wait();// 此处this关键字可省略
            } catch (InterruptedException e) {
                e.printStackTrace();
            } 
        }
        
        this.name = name;
        this.race = race;
        System.out.println(Thread.currentThread().getName()+"....."+name+"....."+race);
        
        flag = true;
        this.notify();// 此处this关键字可省略
    }
    
    // 封装Output线程任务代码
    public synchronized void out(){
        if(!flag){
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            System.out.println(Thread.currentThread().getName()+"....."+name+"....."+race);
        }
        
        flag = false;
        notify();
    }
}

// 输入类
class Input implements Runnable{

    Resource r;
    
    public Input(Resource r) {
        super();
        this.r = r;
    }

    @Override
    public void run() {
        // System.out.println("r="+r);// 打印r地址值,看两个r是否是同一个值 (锁)
        int x = 0;
        while(true){
            if(x%2==0){
                r.set("grubby", "兽族");
            }else{
                r.set("moon", "精灵族");
            }
            x++;
        }
    }
    
}

// 输出类
class Output implements Runnable{

    Resource r;
    
    public Output(Resource r) {
        super();
        this.r = r;
    }
    
    @Override
    public void run() {
        // System.out.println("r="+r);// 打印r地址值,看两个r是否是同一个值(锁)
        while(true){
           r.out();
        }
    }
    
}