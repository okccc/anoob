package multiThread;

import java.util.concurrent.locks.*;


public class ProConDemo2 {
    public static void main(String[] args) {
        /* 
         * jdk1.5以后将同步和锁封装成了对象
         * 
         * Lock接口:用来替代同步代码块和同步函数,将同步的隐式锁操作变成显式锁操作,而且一个锁上面可以挂多组监视器,更灵活且功能更强大
         * lock():获取锁
         * unlock():释放锁 ,是必须要执行的,通常定义在finally代码块中
         * 
         * Condition接口:用来替代Object类中的wait、notify、noifyAll方法,将这些监视器方法单独封装,变成Condition监视器对象,可以与任意锁组合
         * wait()      <===>  await();
         * notify()    <===>  signal();
         * notifyAll() <===>  signalAll();
         */
        
        // 创建资源对象
        Resources r = new Resources();
        // 创建子类对象,传入资源参数
        Producers p = new Producers(r);
        Consumers c = new Consumers(r);
        // 创建线程,传入子类对象参数
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
class Resources{
  private String name;
  private int num = 1;
  private Boolean flag = false;
  
  // 通过Lock接口的子类ReentrantLock类创建锁对象
  Lock lock = new ReentrantLock();
  
  // 用已有的lock对象创建两组监视器,分别监视生产者和消费者
  Condition c1 = lock.newCondition();
  Condition c2 = lock.newCondition();
  
  // 生产者任务代码
  public void set(String name) {
      // 获取锁
      lock.lock();
      try{
          while(flag){
              try {
                  // 生产者监视器
                  c1.await();
              } catch (InterruptedException e) {
                  e.printStackTrace();
              }
          }
          
          this.name = name + num;
          num++;
          System.out.println(Thread.currentThread().getName()+"....."+"Producers生产了"+"....."+this.name);
          
          flag = true;
          // 消费者监视器
          c2.signal();
      } finally {
          // 释放锁
          lock.unlock();
      }
      
  }
  
  // 消费者任务代码
  public void out() {
      // 开启锁
      lock.lock();
      try{
          while(!flag){
              try {
                  // 消费者监视器
                  c2.await();
              } catch (InterruptedException e) {
                  e.printStackTrace();
              }
          }
          
          System.out.println(Thread.currentThread().getName()+"....."+"Consumers消费了"+"....."+name);
          
          flag = false;
          // 生产者监视器
          c1.signal();
      } finally {
          // 释放锁
          lock.unlock();
      }
  }
  
}

// 生产者
class Producers implements Runnable{
  Resources r;
  Producers(Resources r){
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
class Consumers implements Runnable{
  Resources r;
  Consumers(Resources r){
      this.r = r;
  }
  
  @Override
  public void run() {
      while(true){
          r.out();
      }
  }
  
}
