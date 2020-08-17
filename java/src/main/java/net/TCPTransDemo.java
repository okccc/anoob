package net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPTransDemo {
    public static void main(String[] args) {
        /**
         * 需求:创建英文字母大写转换服务器
         *     1、客户端输入字母,发送给服务端
         *     2、服务端接收后打印在控制台,并转换成大写返回给客户端
         *     3、客户端输入over,转换结束
         *     
         * 分析:有客户端和服务端,TCP协议
         */
    }
}

class TransClient{
    public static void main(String[] args) throws IOException {
        
        // 创建Socket服务,指定要访问的ip和端口
        Socket s = new Socket("10.172.20.38",10002);
        
        // 装饰键盘录入
        BufferedReader br1 = new BufferedReader(new InputStreamReader(System.in));
        
        // 装饰Socket输出流
        PrintWriter out = new PrintWriter(s.getOutputStream(),true);
        
        // 装饰Socket输入流
        BufferedReader br2 = new BufferedReader(new InputStreamReader(s.getInputStream()));
        
        // 读写数据
        String line = null;
        while((line=br1.readLine()) != null){
            
            // 上面设置了autoFlush=true,println方法会自动刷新
            out.println(line);
            
            if("over".equals(line)){
                break;
            }
            
            // 接收服务端返回的数据
            String result = br2.readLine();
            System.out.println(result);
        }
        
        // 关闭套接字
        s.close();
        
    }
}

class TransServer{
    public static void main(String[] args) throws IOException {
        
        // 创建ServerSocket服务,绑定端口
        ServerSocket ss = new ServerSocket(10002);
        
        // 获取该套接字的连接
        Socket s = ss.accept();
        
        // 检测是否连接成功
        String name = s.getInetAddress().getHostName();
        System.out.println(name+"...connected!");
        
        // 装饰Socket输入流
        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        
        // 装饰控制台输出
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));
        
        // 装饰Socket输出流
        PrintWriter out = new PrintWriter(s.getOutputStream(),true);
        
        // 读写数据
        String line = null;
        while((line=br.readLine()) != null){
            // 打印在控制台
            bw.write(line);
            bw.newLine();
            bw.flush();
//             System.out.println(line);
            
            // 将字母转换成大写返回给客户端
            out.println(line.toUpperCase());
        }
        
        // 关闭套接字
        ss.close();
        
    }
}
