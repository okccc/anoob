import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Test01 {
    @Test
    public void testStr() {
        System.out.println(UUID.randomUUID().toString());
    }

    @Test
    public void testDate() {
        // 2021-06-23
        System.out.println(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd")));
        // 2021-06-23 21:13:20
        System.out.println(DateTime.now().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.println(System.currentTimeMillis());
        // Long -> String
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        System.out.println(sdf.format(1634284702000L));
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
    public void testJSON() {
//        JSONObject jsonObject = new JSONObject();
//        jsonObject.put("id", "1001");
//        jsonObject.put("name", "grubby");
//        System.out.println(jsonObject.keySet());  // [name, id]
//        System.out.println(jsonObject.values());  // [grubby, 1001]
//        System.out.println(StringUtils.join(jsonObject.values(), ","));  // grubby,1001
//        System.out.println(jsonObject.entrySet());  // [name=grubby, id=1001]

        // json对象过滤键值对
        String value = "{\"data\":[{\"age\":\"19\",\"id\":\"001\",\"name\":\"aaa\"}],\"database\":\"maxwell\",\"table\":\"comment_info\",\"type\":\"INSERT\"}";
        JSONObject jsonObject = JSON.parseObject(value);
        // {"database":"maxwell","data":[{"age":"19","name":"aaa","id":"001"}],"type":"INSERT","table":"comment_info"}
        System.out.println(jsonObject);
        JSONObject data = jsonObject.getJSONArray("data").getJSONObject(0);
        List<String> columns = Arrays.asList("id,name".split(","));
        Set<String> keySet = data.keySet();
        keySet.removeIf(s -> !columns.contains(s));
        // {"database":"maxwell","data":[{"name":"aaa","id":"001"}],"type":"INSERT","table":"comment_info"}
        System.out.println(jsonObject);

        // 往json对象添加包含json的字符串会转译生成反斜杠
        JSONObject json01 = new JSONObject();
        JSONObject json02 = new JSONObject();
        JSONObject json03 = new JSONObject();
        json01.put("k1", "v1");
        json02.put("k1", json01);
        json03.put("k1", json01.toJSONString());
        System.out.println(json01);  // {"k1":"v1"}
        System.out.println(json02);  // {"k1":{"k1":"v1"}}
        System.out.println(json03);  // {"k1":"{\"k1\":\"v1\"}"}
    }

    @Test
    public void testSplit() {
        String str = "aaa";
        String[] arr = str.split(",");
        System.out.println(arr[0]);  // aaa
    }

}
