package net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class UploadTask implements Runnable {
    
    private static final long size = 1024*1024*3;
    
    private Socket s;

    public UploadTask(Socket s) {
        super();
        this.s = s;
    }

    @Override
    public void run() {
        
        try {
            // 获取客户端IP地址
            String ip = s.getInetAddress().getHostAddress();
            System.out.println(ip+"...connected!");
            
            // 获取socket输入流
            InputStream is = s.getInputStream();
            
            // 指定上传文件存放目录
            File dir = new File("d:// ");
            
            // 封装文件名
            File file = new File(dir, ip+".bmp");
            
            int count = 0;
            
            // 判断文件是否存在
            while(file.exists()){
                file = new File(dir, ip+"("+(++count)+").bmp"); 
            }
            
            // 获取字节输出流
            FileOutputStream fos = new FileOutputStream(file);
            
            // 读写数据
            byte[] buf = new byte[1024];
            int len = 0;
            while((len=is.read(buf)) != -1){
                fos.write(buf, 0, len);
                
                // 判断文件大小
                if(file.length() > size){
                    
                    // 此处要关闭输出流,不然文件还在被输出流操作,无法删除也打不开
                    fos.close();
                    
                    System.out.println(ip+"文件体积过大已删除!"+"..."+file.delete());
                    
                    // 退出程序 
                    return;
                }
            }
            
            // 获取socket输出流
            PrintWriter out = new PrintWriter(s.getOutputStream(),true);
            out.println("上传成功！");
            
            // 关流
            fos.close();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
