package net;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class MyTomcat {
    public static void main(String[] args) throws IOException {
        /**
         * 模拟一个Tomcat服务器
         * 
         * 浏览器发送的请求:
         * GET / HTTP/1.1
         * Host: 10.172.20.38:9090
         * User-Agent: Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:54.0) Gecko/20100101 Firefox/54.0
         * Accept: text/html,application/xhtml+xml,application/xml;q=0.9;q=0.8
         * Accept-Encoding: gzip, deflate
         * Accept-Language: zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3
         * Upgrade-Insecure-Requests: 1
         */
        
        // 创建ServerSocket服务,绑定端口
        @SuppressWarnings("resource")
        ServerSocket ss = new ServerSocket(9090);
        
        while(true){
            
            // 获取该套接字连接
            Socket s = ss.accept();
            
            String ip = s.getInetAddress().getHostAddress();
            
            System.out.println(ip+".....connected!");
            
            // 获取socket输入流
            InputStream is = s.getInputStream();
            
            // 读取数据
            byte[] buf = new byte[1024];
            
            int len = is.read(buf);
            
            String text = new String(buf,0,len);
            
            System.out.println(text);
            
            // 获取socket输出流
            PrintWriter out = new PrintWriter(s.getOutputStream(),true);
            
//             out.println("<font color='red' size='7'>欢迎光临</font>");
        out.println("hello!");
            
//             ss.close();
        }
    }
}
