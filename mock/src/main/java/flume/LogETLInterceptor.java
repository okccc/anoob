package flume;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: okccc
 * Desc:
 * Date: 2020/12/6 19:57
 */
public class LogETLInterceptor implements Interceptor {
    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {
        // 获取body
        byte[] bytes = event.getBody();
        // 转换成字符串
        String body = new String(bytes, StandardCharsets.UTF_8);
        // 校验日志
        if (body.contains("start")) {
            // 启动日志
            if (LogUtils.validateStart(body)) {
                return event;
            }
        } else {
            // 事件日志
            if (LogUtils.validateEvent(body)) {
                return event;
            }
        }
        // 校验不通过就返回null,过滤掉该条event
        return null;
    }

    @Override
    public List<Event> intercept(List<Event> events) {
        // 存放添加拦截器后的event的列表
        List<Event> list = new ArrayList<>();
        // 遍历
        for (Event event : events) {
            // 给每一条event添加拦截器处理
            Event event_new = intercept(event);
            if (event_new != null) {
                list.add(event_new);
            }
        }
        // 返回event列表
        return list;
    }

    @Override
    public void close() {

    }

    // 按照agent配置文件中的interceptor类型,创建静态内部类生成拦截器对象
    public static class Builder implements Interceptor.Builder {

        @Override
        public Interceptor build() {
            return new LogETLInterceptor();
        }

        @Override
        public void configure(Context context) {

        }
    }
}
