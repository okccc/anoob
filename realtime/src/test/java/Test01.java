import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Test01 {

    @Test
    public void testStr() {
        // 获取36位长度的随机字符串
        System.out.println(UUID.randomUUID());
        // 截取字符串
        System.out.println("2021-10-02 09:56:13".substring(0, 10));
        System.out.println("2021-10-02 09:56:13".substring(0, 10).replace("-", ""));
        // 获取当前日期/时间
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println(sdf1.format(new Date(System.currentTimeMillis())));
        System.out.println(sdf2.format(new Date(System.currentTimeMillis())));
        // 切割字符串
        String str01 = "aaa";
        String[] arr = str01.split(",");
        System.out.println(arr[0]);  // aaa
        // 注意：split切割字符串 "," ":" "@" "#"不需要转义, "." "|" "$" "*"是需要转义的,多个分隔符可以用"|"隔开,但是该转义的还得转义
        System.out.println("tmp.orders".split("\\.")[1]);
        // 转换json字符串
        String str02 = "{\"a\":\"b\"}";
        try {
            System.out.println(JSON.parseObject(str02));
        } catch (JSONException e) {
            e.printStackTrace();
        }
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

    @Test
    public void testMath() {
        // 向上取整
        System.out.println(Math.ceil(12.33));  // 13.0
        // 向下取整
        System.out.println(Math.floor(12.33));  // 12.0
        // 四舍五入
        System.out.println(Math.round(12.33));  // 12
        // a的b次方
        System.out.println(Math.pow(2, 3));  // 8.0
        // 绝对值
        System.out.println(Math.abs(-12));  // 12
        // 0~1随机数
        System.out.println(Math.random());  // 0.33923048252421284
    }

    @Test
    public void testRandom() {
        Random random = new Random();
        // 小于指定值的任意整数
        System.out.println(random.nextInt(10));
        // 0~1之间的任意小数
        System.out.println(random.nextDouble());
        // 随机字符串
        System.out.println("str" + random.nextInt(10));
    }

    @Test
    public void testDate() throws ParseException {
        // 2021-06-23
        System.out.println(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd")));
        // 2021-06-23 21:13:20
        System.out.println(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println(System.currentTimeMillis());
        // 计算日期差值
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String str1 = "2020-09-07";
        String str2 = "2020-03-25";
        // 解析字符串
        Date date1 = sdf.parse(str1);
        Date date2 = sdf.parse(str2);
        System.out.println(date1 + " | " + date2);  // Mon Sep 07 00:00:00 CST 2020 | Wed Mar 25 00:00:00 CST 2020
        // 转换成毫秒值,只有毫秒值可以加减乘除
        long time1 = date1.getTime();
        long time2 = date2.getTime();
        System.out.println(time1 + " | " + time2);  // 1599408000000 | 1585065600000
        long time = Math.abs(time1 - time2);
        System.out.println(time/(24*3600*1000));
        // 将long类型的时间戳转换成日期
        System.out.println(sdf.format(new Date(time1)));
    }

    @Test
    public void testRuntime() throws IOException, InterruptedException {
        // 获取Runtime对象,操纵当前程序运行的环境
        Runtime runtime = Runtime.getRuntime();
        // exec方法返回进程
        Process proc = runtime.exec("jps");
        // 线程休眠
        Thread.sleep(3000);
        // 杀掉进程
        proc.destroy();
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
    public void testComparator() {
        ArrayList<Integer> list = new ArrayList<>();
        list.add(10);
        list.add(30);
        list.add(20);
        System.out.println(list);  // [10, 30, 20]
        list.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o1 - o2;
            }
        });
        System.out.println(list);  // [10, 20, 30]
    }

    @Test
    public void testSimpleDateFormat() {
        // 演示SimpleDateFormat线程安全问题
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // java.lang.NumberFormatException: empty String
                        // java.lang.NumberFormatException: multiple points
                        // java.lang.NumberFormatException: For input string: ""
                        // java.lang.NumberFormatException: For input string: "E.4220622"
                        System.out.println(sdf.parse("2022-09-20 12:30:26"));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

}
