package com.okccc.j2se;

import org.apache.logging.log4j.LogManager;
import org.slf4j.LoggerFactory;

/**
 * @Author: okccc
 * @Date: 2023/1/9 16:12
 * @Desc: java日志体系
 *
 * 两种日志接口
 * jcl(2002)：apache commons logging
 * slf4j(2006)：simple logging facade for java 简单日志门面,提供统一的日志接口,只包含slf4j-api.jar,没有具体实现
 *
 * 四种日志实现
 * jul(2002)：jdk自带
 * log4j(2001)：没有实现slf4j接口,需添加适配器slf4j-log4j12.jar将其和slf4j接口绑定
 * logback(2006)：直接实现slf4j接口,是log4j的改良版,spring-boot默认配置就是slf4j + logback
 * log4j2(2012)：借鉴slf4j + logback所有特性并做了分离设计,log4j-api是日志接口,log4j-core是日志实现,异步IO性能最好
 * 可以在启动脚本中添加配置 -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
 *
 * java日志体系两大阵营
 * slf4j-api + logback
 * log4j2(log4j-api + log4j-core)
 */
public class LoggerDemo {

    private static void testLogback() {
        // slf4j-api + logback
        org.slf4j.Logger logger = LoggerFactory.getLogger(LoggerDemo.class);
        logger.error("error test");
        logger.warn("warn test");
        logger.info("info test");
        logger.debug("debug test");
    }

    private static void testLog4j2() {
        // log4j-api + log4j-core
        org.apache.logging.log4j.Logger logger = LogManager.getLogger(LoggerDemo.class);
        logger.error("error test");
        logger.warn("warn test");
        logger.info("info test");
        logger.debug("debug test");
    }

    public static void main(String[] args) {
        testLogback();
//        testLog4j2();
    }
}
