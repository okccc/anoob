package com.okccc.source;

import org.apache.flink.streaming.api.functions.source.SourceFunction;

import java.util.Random;

/**
 * @Author: okccc
 * @Date: 2023/1/4 18:48
 * @Desc:
 */
public class NumberSource implements SourceFunction<Integer> {

    // 模拟数据
    private boolean running = true;
    private final Random random = new Random();

    @Override
    public void run(SourceContext<Integer> ctx) throws Exception {
        while (running) {
            ctx.collect(random.nextInt(10));
            Thread.sleep(1000L);
        }
    }

    @Override
    public void cancel() {
        running = false;
    }
}
