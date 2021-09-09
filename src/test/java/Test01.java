import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Test01 {
    @Test
    public void testDate() {
        // 2021-06-23
        System.out.println(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd")));
        // 2021-06-23 21:13:20
        System.out.println(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println(System.currentTimeMillis() - 3600*24*1000);
    }

    @Test
    public void testRandom() {
        Random random = new Random();
        // 小于指定值的任意整数
        System.out.println(random.nextInt(10));
        // 0~1之间的任意小数
        System.out.println(random.nextDouble());
    }

    @Test
    public void testFuture() throws ExecutionException, InterruptedException {
        // 创建线程池
        ExecutorService executor = Executors.newCachedThreadPool();
        // 提交要执行的任务,返回Future对象
        Future<?> future = executor.submit(new Runnable() {
            @Override
            public void run() {
                // 待执行任务
                for (int i = 0; i < 10; i++) {
                    System.out.println("i = " + i);
                }
            }
        });
        // 阻塞,等待上面任务执行结束,起到同步作用
        future.get();
        System.out.println("==================================");
        // 之前的任务已执行,且没有新的任务就关闭
        executor.shutdown();
    }

    @Test
    public void testDouble() {
        // java运算结果由被运算数的最高数据类型决定,比如两个int相除得到的结果也只能是int
        System.out.println(10 / 3);  // 3
        // 如果要返回小数的话需要将其中一个int向上转型为double,因为double能装下int但是int装不下double
        System.out.println((double)10 / 3);  // 3.3333333333333335
        System.out.println(10 / (double)3);  // 3.3333333333333335
        System.out.println(10d / 3);         // 3.3333333333333335
        System.out.println(10 / 3d);         // 3.3333333333333335
        // 只改变运算结果的类型是不行滴
        System.out.println((double)(10 / 3));  // 3.0
    }

}
