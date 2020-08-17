package multiThread;

public class TicketsDemo {
    public static void main(String[] args) {
        /*
         * 卖票案例:多个窗口同时卖票,使用多线程技术
         * 
         * 线程安全问题:当一个线程在执行操作共享数据的任务代码时,其他线程也参与了运算,就会导致线程安全问题
         * 
         * 产生原因:1、有多个线程操作共享数据
         *        2、线程操作共享数据的任务代码有多条 
         *         
         * 解决方法:将操作共享数据的代码封装起来,当有线程在执行的时候,其他线程不可以介入,用同步代码块或者同步函数 
         * 
         * 同步代码块格式:
         * synchronized (锁对象) {
         *    需要被同步的代码;
         * }
         * 
         * 同步的前提:必须有多个线程执行任务代码并且使用的是同一个锁对象
         * 
         * 同步的好处:可以解决线程安全问题
         * 同步的弊端:同步外的线程都要判断同步锁,相对降低效率 
         */
    
        // 创建子类对象
        Tickets t = new Tickets();
        // 创建线程,将子类对象作为参数传入
        Thread t1 = new Thread(t);
        Thread t2 = new Thread(t);
        Thread t3 = new Thread(t);
        Thread t4 = new Thread(t);
        
//       线程可以设置优先级,优先级只代表概率,并不一定会立刻运行,还是要抢资源,默认是5
//       MIN_PRIORITY    1
//       NORM_PRIORITY   5
//       MAX_PRIORITY    10
        
//         t1.setPriority(Thread.MIN_PRIORITY);
//         t2.setPriority(Thread.MAX_PRIORITY);
//         t3.setPriority(1);
//         t4.setPriority(10);
        
//       sleep()和yield()区别:1、两个都是让线程暂停的方法
//                          2、sleep指定时间,yield不指定时间
//                          3、sleep能让不同优先级的线程有执行机会,yield只能让同级别的线程有执行机会
        
        // 调用start方法
        t1.start();
        t2.start();
        t3.start();
        t4.start();
    }

}

class Tickets implements Runnable{
	// 总共100张票
	private int num = 100;
	Object obj = new Object();

	@Override
	// 重写run方法,自定义任务代码
	public void run() {
	    while(true){
	        // 同步代码块
	        synchronized (obj) {
                    // 要封装的操作共享数据的任务代码
	            if(num>0){
	                try {
	                    // 线程运行太快,sleep一下,释放CPU执行权和执行资格,让别的线程也能执行
	                    Thread.sleep(50);
	                } catch (InterruptedException e) {
	                    e.printStackTrace();
	                }
// 	                Thread.yield();
	                System.out.println(Thread.currentThread().getName()+"当前正在售出第"+num--+"张票");// num--表示先运算再--
	            }
                }
            }
	   
	}
	
}
