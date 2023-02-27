package com.okccc.source;

import com.okccc.bean.Event;
import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.Random;

/**
 * @Author: okccc
 * @Date: 2023/1/4 18:39
 * @Desc: 自定义数据源实现SourceFunction接口,数据源的泛型可以是Integer/Long/String/Double,也可以是POJO类
 */
public class UserActionSource implements SourceFunction<Event> {

    // 模拟数据
    private boolean running = true;
    private final String[] userArr = {"grubby", "moon", "sky", "fly", "ted"};
    private final String[] urlArr = {"./home", "./cart", "./fav", "./prod?id=1", "./prod?id=2"};
    private final Random random = new Random();

    @Override
    public void run(SourceContext<Event> ctx) throws Exception {
        while (running) {
            // 随机生成数据
            String user = userArr[random.nextInt(userArr.length)];
            String url = urlArr[random.nextInt(urlArr.length)];
            long timestamp = System.currentTimeMillis();
            // 收集数据往下游发送
            ctx.collect(Event.of(user, url, timestamp));
            Thread.sleep(1000L);
        }
    }

    @Override
    public void cancel() {
        running = false;
    }
}
