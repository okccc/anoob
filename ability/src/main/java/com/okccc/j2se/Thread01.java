package com.okccc.j2se;

@SuppressWarnings("unused")
public class Thread01 {
    public static void main(String[] args) {
        /*
         * 并行：cpu核数 > 任务数 -> 真的多任务
         * 并发：cpu核数 < 任务数 -> 假的多任务,因为一个cpu在同一时刻只能运行一个任务,只是占用cpu的程序切换速度足够快感觉不出来
         * 同步和异步：请求发出后是否需要等待返回结果才能继续往下走,是一种消息通知机制,异步可通过多线程/协程/celery实现
         * 阻塞和非阻塞：关注的是进程/线程在等待调用结果时的状态,阻塞是同步机制的结果,非阻塞是异步机制的结果
         * cpu密集型：各种循环、逻辑判断、计数等 -> 使用多进程充分利用多核cpu并行运算,一个进程会占用一个cpu
         * io密集型：网络传输、文件读写 -> 使用多线程在io阻塞时可以切换线程不浪费cpu资源,cpu速度>>io所以单cpu足够用多cpu切换反而开销大
         *
         * 多进程：提高cpu使用率,一个进程中至少有一个线程
         * 多线程：提高应用程序使用率,线程执行具有随机性(抢资源)
         * jvm启动时就至少启动了两个线程：执行main方法的线程、负责垃圾回收的线程
         *
         * 创建线程
         * 方式一：继承Thread类
         * 方式二：实现Runnable接口(优点：1.避免单继承局限性 2.更适合处理共享资源(卖票),将线程和任务代码及数据资源分离)
         * 线程同步：当多线程处理共享数据时会出现数据安全问题,线程1没结束线程2又参与了进来,需要使用同步代码块/方法将操作共享数据的代码上锁
         * synchronized (锁对象) {
         *     // 需要同步(上锁)的代码
         * }
         * public synchronized 返回类型 方法名(参数列表) {
         *     // 需要同步(上锁)的代码
         * }
         * 非静态方法锁对象是this,静态方法锁对象是类名.class
         * 释放锁：线程死亡和调用wait方法(上厕所没酝酿好出来等会再上)
         * 不释放锁：调用sleep和yield方法(上厕所在打电话)
         * 死锁：多线程相互之间需要对方释放锁,互不相让导致死锁
         *
         * 线程生命周期
         * 新建：创建线程但还没开启
         * 就绪：开启线程但还没抢到cpu执行权
         * 运行：抢到了cpu执行权
         * 消亡：线程执行结束 | 正常退出(break/return) | 异常退出(error/exception)
         * 阻塞：在运行期间调用了sleep/wait/join方法,当sleep结束或notify唤醒之后会切换到就绪状态
         *
         * run和start区别？
         * run仅仅是封装自定义线程要执行的任务代码,调用run方法时并没有启动该线程
         * start首先启动线程,然后再由jvm去调用该线程的run方法
         *
         * sleep和wait区别？
         * sleep是Thread类的方法,必须指定时间,不释放锁时间到了自然醒
         * wait是Object类的方法,不指定时间,释放锁等待notify来唤醒它
         *
         * 线程池：每启动一个新线程都涉及与OS交互,成本很高,可以使用线程池控制系统中并发线程的数量,避免因创建线程过多导致的系统性能下降
         *
         * java8新特性
         * 1.Lambda表达式
         * 只有一个抽象方法的接口叫函数式接口(@FunctionalInterface),当匿名内部类实现函数式接口时,可以用lambda表达式代替
         * 格式：(参数列表) -> {抽象方法的具体实现}
         * 2.Stream API
         * 计算数组和集合生成的元素序列
         */

//        createThread();
//        common();
        saleTicket();
//        stopThread();
    }

    private static void createThread() {
        // 继承Thread类
        new Thread(){
            @Override
            public void run() {
                for (int i =1; i <= 100; i++) {
                    System.out.println(Thread.currentThread().getName() +"..."+ i);
                }
            }
        }.start();
        // 实现Runnable接口
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= 100; i++) {
                    System.out.println(Thread.currentThread().getName() + "..." + i);
                }
            }
        }).start();
    }

    private static void common() throws InterruptedException {
        // 线程常用方法
        Thread t = new Thread(new Tickets02());
        // 启动线程,jvm会去调用线程的run方法,查看源码发现调用的是系统底层(native)的start0方法,直接和OS交互
        t.start();
        // 获取当前线程
        System.out.println(Thread.currentThread().getName());  // main
        // 设置线程名称,不设置就是默认名称Thread-0/1/2
        t.setName("");
        // 设置线程优先级,在start()之前调用
        t.setPriority(Thread.MAX_PRIORITY);  // 10
        t.setPriority(Thread.MIN_PRIORITY);  // 1
        t.setPriority(Thread.NORM_PRIORITY);  // 5
        // 让线程休眠,指定时间,可以让不同级别线程有执行机会
        Thread.sleep(1000);
        // 让线程礼让,不指定时间,只能让同级别线程有执行机会
        Thread.yield();
        // 让线程等待,直到被notify唤醒
        t.wait();
        // 让线程插队,如果t调用了join方法,那么t后面的线程会搁置,等t和前面的线程(抢资源)都执行完,才会执行后面的线程
        t.join();
        // 中断线程的阻塞状态(sleep/wait/join),然后抛出异常InterruptedException
        t.interrupt();
        // 线程分类：user thread任务执行完就停止 daemon thread当用户线程都执行完,不管守护线程有没有执行完都会停止
        // 标记为守护线程,在start()之前调用,垃圾回收机制就是经典的守护线程,如果当前运行的线程都是守护线程那么jvm退出
        t.setDaemon(true);
    }

    private static void saleTicket() {
        // 3个窗口同时卖票案例,3个人同时去银行存钱同理
        // 售票方式一：创建了3个线程,每个线程都是一个tickets对象
//        new Tickets01().start();
//        new Tickets01().start();
//        new Tickets01().start();

        // 售票方式二：创建了3个线程,但是操作的是同一个tickets对象(推荐)
        Tickets02 t = new Tickets02();
        new Thread(t).start();
        new Thread(t).start();
        new Thread(t).start();

        Bank b = new Bank();
        new Thread(b).start();
        new Thread(b).start();
        new Thread(b).start();
    }

    private static void stopThread() {
        // 线程的停止
        // stop方法已弃用,interrupt方法只是中断线程的阻塞状态,并没有结束线程
        // 采用通知方式,多线程的任务代码中一般都有循环结构,可以定义flag标记来控制循环何时结束
        Stop s = new Stop();
        new Thread(s).start();
        for (int i = 1; i <= 100; i++) {
            System.out.println(Thread.currentThread().getName() + "..." + i);
            if (i == 5) {
                s.setFlag(false);
                break;
            }
        }
    }
}

// 继承Thread类
class Tickets01 extends Thread {
    // tickets是共享数据,要定义成静态,不然3个窗口会卖300张票,但是静态随着类的加载而加载生命周期过长
    static int tickets = 100;
    @Override
    public void run() {
        while (true) {
            if (tickets <= 0) {
                System.out.println("票已售完");
                break;
            }
            System.out.println(Thread.currentThread().getName() + " 卖了一张票,剩余票数为: " + (--tickets));
        }
    }
}

// 实现Runnable接口
class Tickets02 implements Runnable {
    int tickets = 100;
    @Override
    public void run() {
        while (true) {
            // 同步代码块,给操作共享数据的代码块上锁
            synchronized (this) {
                if (tickets <= 0) {
                    System.out.println("票已售完");
                    break;
                }
                try {
                    // 线程运行太快了暂停一下,释放cpu执行权和执行资格,让别的线程也能执行
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 不加锁的话可能会卖-1,-2张票,取款机取钱同理
                System.out.println(Thread.currentThread().getName() + " 卖了一张票,剩余票数为: " + (--tickets));
            }
        }
    }
}

class Bank implements Runnable {
    int sum = 0;
    @Override
    public void run() {
        synchronized (this) {
            for (int i=1; i<=5; i++) {
                sum += 1000;
                System.out.println(Thread.currentThread().getName() + "银行余额: " + sum);
            }
        }
    }
}

class Stop implements Runnable {
    boolean flag = true;
    public void setFlag(boolean flag) {
        this.flag = flag;
    }
    @Override
    public void run() {
        while (flag) {
            System.out.println(Thread.currentThread().getName() + "..." + "thread is running");
        }
    }
}