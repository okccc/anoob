package bigdata.flume;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: okccc
 * Date: 2021/1/5 3:03 下午
 * Desc: 清洗nginx日志格式并做url解码
 */
public class ETLInterceptor implements Interceptor {
    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {
        if (event == null) {
            return null;
        }
        String line = new String(event.getBody(), StandardCharsets.UTF_8);
        if (line.length() > 0) {
            // 截取字符串
            String msg = line.split("\"")[3];
            try {
                // java.lang.IllegalArgumentException: URLDecoder: Incomplete trailing escape (%) pattern
                // url解码,%在url中是特殊字符,要先将单独出现的%替换成编码后的%25,再对整个字符串解码
                String msg_new = URLDecoder.decode(msg.replaceAll("%(?![0-9a-fA-F]{2})", "%25"), "utf-8");
                // 返回新的event
                event.setBody(msg_new.getBytes(StandardCharsets.UTF_8));
                return event;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public List<Event> intercept(List<Event> list) {
        // 拦截器处理完后的event列表
        List<Event> events = new ArrayList<>();
        for (Event event : list) {
            // 给每条event都用拦截器处理
            Event event_new = intercept(event);
            if (event_new != null) {
                events.add(event_new);
            }
        }
        return events;
    }

    @Override
    public void close() {

    }

    public static class Builder implements Interceptor.Builder {
        @Override
        public Interceptor build() {
            return new ETLInterceptor();
        }

        @Override
        public void configure(Context context) {

        }
    }


}
