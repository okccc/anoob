package flume;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Author: okccc
 * Date: 2020/12/6 19:47
 * Desc:
 */
public class TypeInterceptor implements Interceptor {
    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {
        // Event: {headers:{} body: 61 61 61  aaa} headers默认是空,根据body中的日志类型填充headers
        // 获取body
        byte[] bytes = event.getBody();
        // 将数组转换成字符串
        String body = new String(bytes, StandardCharsets.UTF_8);
        // 获取header
        Map<String, String> headers = event.getHeaders();
        // 填充header
        if (body.contains("start")) {
            headers.put("topic", "start");
        } else {
            headers.put("topic", "event");
        }
        // 返回填充headers后的Event
        return event;
    }

    @Override
    public List<Event> intercept(List<Event> events) {
        // 创建存放添加拦截器后的event的列表
        List<Event> list = new ArrayList<>();
        // 遍历
        for (Event event : events) {
            // 给每一条event添加拦截器处理
            Event event_new = intercept(event);
            list.add(event_new);
        }
        // 返回新的event列表
        return list;
    }

    @Override
    public void close() {

    }

    // 按照agent配置文件中的interceptor类型,创建静态内部类生成拦截器对象
    public static class Builder implements Interceptor.Builder {

        @Override
        public Interceptor build() {
            return new TypeInterceptor();
        }

        @Override
        public void configure(Context context) {

        }
    }
}
