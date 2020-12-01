import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TestFuture {
    @Test
    public void test() throws ExecutionException, InterruptedException {
        // 创建线程池
        ExecutorService executor = Executors.newCachedThreadPool();
        // 提交要执行的任务,返回Future对象
        Future<?> future = executor.submit(new Runnable() {
            @Override
            public void run() {
                // 待执行任务
                for (int i = 0; i < 1000; i++) {
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
}
