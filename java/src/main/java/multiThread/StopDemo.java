package multiThread;

public class StopDemo {
    public static void main(String[] args) throws Exception {
        /*
         * 如何停止线程？
         * 方式一:多线程的任务代码中一般都有循环结构,可以定义一个标记来控制循环,结束任务
         * 方式二:当线程处于冻结状态时(sleep,wait,join),是无法读取flag标记的,也就无法结束任务,这时候就要用interrupt
         *      interrupt()方法可以将线程从冻结状态中强制恢复到运行状态,具备CPU执行权,继续读取flag
         */
        
        Stop s = new Stop();
        Thread t1 = new Thread(s);
        Thread t2 = new Thread(s);
        t1.start();
        // 守护线程(后台线程):如果前台线程都结束了,后台线程无论处于何种状态都会自动结束,如果正在运行的线程都是守护线程,那么JVM退出,该方法必须在线程启动前调用
        t2.setDaemon(true);
        t2.start();
        
        // main线程控制t1、t2线程状态的代码
        int num = 1;
        while(true){
            if(num==100){
                // 将线程从冻结状态强制拉回来
                t1.interrupt();
                // 假设这里t1解除冻结了,t2并没有,那么t2是无法读取flag的,这时候可以用守护线程让t2自动结束
                // t2.interrupt();
                
                // 控制flag状态
                s.setFlag();
                System.out.println("thread exits!");
                
                // 退出当前循环
                break;
            }else{
                System.out.println(Thread.currentThread().getName()+"....."+num++);
            }
        	
        }
        
    }
}

class Stop implements Runnable{
    private Boolean flag = true;

    @Override
    public void run() {
//         当线程处于运行状态时  	
//         while(flag){
//             System.out.println(Thread.currentThread().getName()+"....."+"thread is running!");
//         }
    	
    	// 当线程有可能处于冻结状态时
    	while(flag){
    	    synchronized (this) {
                try {
                        // Thread.sleep(10000000);
                        wait();
                } catch (InterruptedException e) {
                        // e.printStackTrace();
                        System.out.println(Thread.currentThread().getName()+"....."+e.getMessage());
                }
                
                System.out.println(Thread.currentThread().getName()+"....."+"thread is running!");
            }
    	}
    }
    
    // 提供控制判断标记的方法
    public void setFlag(){
        flag = false;
    }
        
    
}