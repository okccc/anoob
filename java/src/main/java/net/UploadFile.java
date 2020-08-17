package net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class UploadFile {
    public static void main(String[] args) {
        /**
         * 上传文本文件到服务器
         */
    }
}

class UploadClient{
    public static void main(String[] args) throws IOException {
        
        // 创建Socket服务
        Socket s = new Socket("10.172.20.38", 10003);
        
        // 封装待上传文件
        File file = new File("e:// aaa.txt");
        System.out.println(file.exists());
        
        // 装饰读取文件的输入流
        BufferedReader br = new BufferedReader(new FileReader(file));
        
        // 装饰socket输出流
        PrintWriter out = new PrintWriter(s.getOutputStream(),true);  // true表示自动刷新
        
        // 读写数据
        String line = null;
        while((line=br.readLine()) != null){
            // PrintWriter的构造函数设置了autoFlush=true,println方法会自动刷新输出缓冲区
            out.println(line);
        }
        
        // 禁用此套接字输出流,将之前写入的数据发送
        s.shutdownOutput();
        
        // 装饰socket输入流
        BufferedReader br2 = new BufferedReader(new InputStreamReader(s.getInputStream()));
        
        System.out.println(br2.readLine());
        
        // 关闭资源
        br.close();
        s.close();
    }
}

class UploadServer{
    public static void main(String[] args) throws IOException {
        
        // 创建ServerSocket服务,绑定端口
        ServerSocket ss = new ServerSocket(10003);
        
        // 获取该套接字的连接
        Socket s = ss.accept();
        
        // 装饰socket输入流
        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        
        // 装饰写入文件的输出流
        BufferedWriter bw = new BufferedWriter(new FileWriter("d:// aaa.txt"));
        
        // 读写数据
        String line = null;
        while((line=br.readLine()) !=null){
            bw.write(line);
            bw.newLine();
            bw.flush();
        }
        
        // 装饰socket输出流
        PrintWriter out = new PrintWriter(s.getOutputStream(),true);
        out.println("上传成功！");
        
        // 关闭资源
        bw.close();
        ss.close();
        
    }
}