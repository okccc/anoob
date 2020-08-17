package net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPDemo {
    public static void main(String[] args) {
        /**
         * TCP协议:
         * 
         * 套接字是两台机器间通信的端点
         * Socket编程:基于TCP/IP协议的网络编程
         * 
         * Socket:客户端套接字
         * Socket(InetAddress address, int port)
         * 
         * InetAddress getInetAddress():
         * InetAddress getLocalAddress():
         * InputStream getInputStream():获取socket输入字节流
         * OutputStream getOutputStream():获取socket输出字节流
         * close():关闭套接字
         * 
         * ServerSocket:服务器套接字
         * ServerSocket(int port)
         * 
         * Socket accept():获取连接过来的套接字
         * close():关闭套接字
         * 
         * 注意:TCP协议在有服务器时用,建立连接且先启动服务器
         *     UDP协议因为不建立连接,无所谓先后启动顺序
         */
    }
}

class Client{
    public static void main(String[] args) throws IOException {
        
        // 1、创建客户端Socket服务,指定要访问的ip和端口
        Socket s = new Socket("10.172.20.38",10001);
        
        // 2、获取输出字节流
        OutputStream out = s.getOutputStream();
        
        // 3、写数据到服务器
        out.write("我来啦！".getBytes());
        
        // 4、关闭套接字
        s.close();
        
    }
}

class Server{
    public static void main(String[] args) throws IOException {
        
        // 1、创建服务端Socket服务,绑定端口
        ServerSocket ss = new ServerSocket(10001);
        
        // 2、获取该套接字的连接
        Socket s = ss.accept();  // 阻塞式的
        
        // 检测客户端是否已连接
        String ip = s.getInetAddress().getHostAddress();
        String name = s.getInetAddress().getHostName();
        int port = s.getPort();
        System.out.println(ip+"..."+name+"..."+port);
        
        // 3、获取输入流,读取客户端访问信息
        InputStream is = s.getInputStream();
        
        byte[] buf = new byte[1024];
//         int len = 0;
//         while((len = is.read(buf)) != 1){
//             System.out.println(new String(buf,0,len));
//         }
        int len = is.read(buf);
        System.out.println(new String(buf,0,len));
        
        // 4、获取输出流,向客户端反馈信息
        OutputStream os = s.getOutputStream();
        os.write("收到啦！".getBytes());
        
        // 5、关闭套接字
        ss.close();
        
    }
}
