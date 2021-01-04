package spark.flume;

import org.apache.commons.lang.math.NumberUtils;

/**
 * Author: okccc
 * Desc:
 * Date: 2020/12/6 19:34
 */
public class LogUtils {
    public static boolean validateStart(String log) {
        // 校验日志是否为空
        if (log == null) {
            return false;
        }
        // 校验日志格式 {json}
        return !log.trim().startsWith("{") || !log.trim().endsWith("}");
    }

    public static boolean validateEvent(String log) {
        // 校验日志是否为空
        if (log == null) {
            return false;
        }
        // 1549696569054 | {"cm":{"ln":"-89.2","sv":"V2.0.4","os":"8.2.0"...},"ap":"weather","et":[]}
        String[] content = log.split("\\|");
        // 校验日志长度
        if (content.length != 2) {
            return false;
        }
        // 校验时间戳格式
        if (content[0].length() != 13 || !NumberUtils.isDigits(content[0])) {
            return false;
        }
        // 校验日志格式 {json}
        return !content[1].trim().startsWith("{") || !content[1].trim().endsWith("}");
    }
}
