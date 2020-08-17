package otherObject;

import java.io.IOException;

public class RuntimeDemo {
    public static void main(String[] args) throws IOException, InterruptedException {
        /**
         * Runtime:该类没有构造函数,无法创建对象,所以应该都是静态方法,但是看API发现居然还有非静态方法,
         *         说明Runtime类是单例模式,构造函数私有化了,并且对外提供静态的getRuntime()方法,返回Runtime实例对象
         */
        
        // 获取当前运行时
        Runtime r = Runtime.getRuntime();
        // exec方法返回进程
        Process proc = r.exec("notepad.exe");
        // 线程休眠
        Thread.sleep(3000);
        // 杀掉进程
        proc.destroy();
}
	
}
