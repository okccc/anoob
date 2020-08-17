package io01;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class PrintStreamDemo {
    public static void main(String[] args) throws IOException {
        /**
         * PrintStream:字节打印流
         * PrintStream(File file)
         * PrintStream(OutputStream out, boolean autoFlush)
         * 
         * PrintWriter:字符打印流
         * PrintWriter(File file)
         * PrintWriter(OutputStream out, boolean autoFlush)   //  设置为true自动刷新
         * PrintWriter(Writer out, boolean autoFlush)
         * 
         * 特有方法:println(),其他字符输出流只有write()
         * 
         * 特点:1、可以打印各种数据类型的值还能换行 
         *     2、该类的方法不会抛出IOException
         */
        
        //  装饰键盘录入
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        
        //  装饰控制台输出
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out),true);  //  true表示刷新输出缓冲区
        
        //  读写数据
        String line = null;
        while((line = br.readLine()) != null){
            //  控制结束标记
            if("over".equals(line)){
                break;
            }
            
            //  println是PrintWriter类的特有方法,其他****Writer只有writer()方法
            //  println() = write() + newline() + flush()
            out.println(line);
        }
    }
}
