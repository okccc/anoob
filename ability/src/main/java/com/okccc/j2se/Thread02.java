package com.okccc.j2se;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Thread02 {
    public static void main(String[] args) throws IOException {
        /*
         * 多线程间的通信
         * 等待唤醒机制
         * wait()：让线程阻塞,暂时存到线程池
         * notify()：唤醒线程池中任意一个线程
         * notifyAll()：唤醒线程池中所有线程
         * wait和notify都是Object类方法,这俩都是监视器(锁)的方法,而锁可以是任意对象,所以这些方法定义在Object类
         * wait和notify必须在synchronized中,wait是让线程释放锁,释放锁的前提是有锁(同步),notify是将锁交给正在wait的线程,也得有锁
         *
         * 单生产者单消费者模型没问题,因为除了生产者线程就是消费者线程
         * 多生产者多消费者模型有问题
         * 1.notify只会唤醒线程池中任意一个线程,有可能唤醒的是己方线程,应该改成notifyAll,但是这样效率很低,可以用condition监视器优化
         * 2.if只会判断一次,比如A线程wait释放锁之后又被唤醒了是不会再次判断的,继续往下走就有可能导致线程安全问题,应该使用while循环
         *
         * jdk1.5以后将同步和锁封装成了对象
         * Lock接口用来替代同步代码块/方法,将同步的隐式锁操作变成显式锁操作,而且一个锁上面可以挂多组监视器,更灵活且功能更强大
         * lock()获取锁 | unlock()释放锁,是必须要执行的,通常定义在finally代码块中
         * Condition接口用来替代Object类中的wait/notify/notifyAll,将这些监视器方法单独封装成Condition监视器对象,可以与任意锁组合
         * wait -> await | notify -> signal | notifyAll -> signalAll
         */

//        testWait();
        proCon();
//        testPiped();
    }

    private static void testWait() throws InterruptedException {
        // wait方法没有放在同步代码块/方法中会报错：Exception in thread "main" java.lang.IllegalMonitorStateException
        new Object().wait();
    }

    private static void proCon() {
        // 创建共享数据对象
        Data data = new Data();
        // 创建生产者和消费者对象,将共享数据作为构造参数传入
        Producer p = new Producer(data);
        Consumer c = new Consumer(data);
        // 单生产者单消费者
//        new Thread(p).start();
//        new Thread(c).start();
        // 多生产者多消费者
        new Thread(p).start();
        new Thread(p).start();
        new Thread(c).start();
        new Thread(c).start();
    }

    private static void testPiped() throws IOException {
        // 创建管道输入输出流
        PipedInputStream pis = new PipedInputStream();
        PipedOutputStream pos = new PipedOutputStream();
        // 连接pis和pos
        pis.connect(pos);
        // 开启多线程
        new Thread(new Input(pis)).start();
        new Thread(new Output(pos)).start();
    }
}

// 共享数据
class Data1 {
    int count = 0;

    // wait和notify要放在同步代码块/方法中,不然报错java.lang.IllegalMonitorStateException
    public synchronized void produce() {
        // count >= 10表示生产10个再消费,count >= 1就是生产1个就消费
        while (count >= 10) {
            try {
                // 数据满了就等待一下
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            // 控制一下生产速度
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(Thread.currentThread().getName() + "生产了一个数据,目前库存为：" + (++count));
        // 唤醒消费者去消费数据
        notifyAll();
    }

    public synchronized void consume() {
        while (count <= 0) {
            try {
                // 没有数据就等一下
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            // 控制一下消费速度
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(Thread.currentThread().getName() + "消费了一个数据,目前库存为：" + (--count));
        // 唤醒生产者去生产数据
        notifyAll();
    }
}

class Data {
    int count = 0;

    // 通过Lock接口的子类ReentrantLock创建锁对象
    Lock lock = new ReentrantLock();
    // 用已有的锁对象创建两组监视器,分别监视生产者和消费者
    Condition c1 = lock.newCondition();
    Condition c2 = lock.newCondition();

    public void produce() {
        // 获取锁
        lock.lock();
        // count >= 10表示生产10个再消费,count >= 1就是生产1个就消费
        while (count >= 1) {
            try {
                // 生产者监视器
                c1.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            // 控制一下生产速度
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(Thread.currentThread().getName() + "生产了一个数据,目前库存为：" + (++count));
        // 消费者监视器
        c2.signal();
        // 释放锁
        lock.unlock();
    }

    public void consume() {
        // 获取锁
        lock.lock();
        while (count <= 0) {
            try {
                // 消费者监视器
                c2.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            // 控制一下消费速度
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(Thread.currentThread().getName() + "消费了一个数据,目前库存为：" + (--count));
        // 生产者监视器
        c1.signal();
        // 释放锁
        lock.unlock();
    }
}

// 生产者
class Producer implements Runnable{
    Data data;
    public Producer(Data data) {
        this.data = data;
    }
    @Override
    public void run() {
        while (true) {
            // 生产数据
            data.produce();
        }
    }
}

// 消费者
class Consumer implements Runnable{
    Data data;
    public Consumer(Data data) {
        this.data = data;
    }
    @Override
    public void run() {
        while (true) {
            // 消费数据
            data.consume();
        }
    }
}

// 操作管道输入流的线程
class Input implements Runnable{
    // 将管道输入流作为参数传入
    private final PipedInputStream pis;
    public Input(PipedInputStream pis) {
        this.pis = pis;
    }
    @Override
    public void run() {
        byte[] arr = new byte[1024];
        try {
            while(pis.read(arr) != -1){
                System.out.println(new String(arr));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// 操作管道输出流的线程
class Output implements Runnable{
    // 将管道输出流作为参数传入
    private final PipedOutputStream pos;
    public Output(PipedOutputStream pos) {
        this.pos = pos;
    }
    @Override
    public void run() {
        while(true){
            try {
                pos.write("往管道输出流写数据\r\n".getBytes());
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
