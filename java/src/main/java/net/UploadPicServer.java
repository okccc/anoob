package net;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class UploadPicServer {
    public static void main(String[] args) throws IOException {
        /**
         * 多线程操作服务器:可能有多个客户端向服务器上传文件,服务器始终开启
         */
        
        // 创建ServerSocket服务
        @SuppressWarnings("resource")
        ServerSocket ss = new ServerSocket(10004);
        
        while(true){
            
            // 获取该套接字连接
            Socket s = ss.accept();
            
            // 开启多线程
            new Thread(new UploadTask(s)).start();
            
//             ss.close();
        }
    }
}

class UploadPicClient{
    public static void main(String[] args) throws IOException {
        
        // 创建Socket服务
        Socket s = new Socket("10.172.20.38",10004);
        
        // 获取字节输入流
        FileInputStream fis = new FileInputStream(new File("e:// 多线程状态图.bmp"));
        
        // 获取socket输出流
        OutputStream out = s.getOutputStream();
        
        // 读写数据
        byte[] buf = new byte[1024];
        int len = 0;
        while((len=fis.read(buf)) != -1){
            out.write(buf, 0, len);
        }
        
        // 上传结束
        s.shutdownOutput();
        
        // 获取socket输入流
        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        System.out.println(br.readLine());
        
        // 关闭资源
        fis.close();
        s.close();
    }
}
