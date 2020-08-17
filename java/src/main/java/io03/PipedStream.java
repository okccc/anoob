package io03;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class PipedStream {
    public static void main(String[] args) throws IOException {
        /**
         * 管道流要结合多线程使用,单线程会死锁
         * PipedInputStream:管道输入流
         * PipedOutputStream:管道输出流
         */
        
        // 创建管道输入输出流
        PipedInputStream pis = new PipedInputStream();
        PipedOutputStream pos = new PipedOutputStream();
        
        // 连接pis和pos
        pis.connect(pos);
        
        // 开启多线程
        new Thread(new Input(pis)).start();
        new Thread(new Output(pos)).start();
    }
}

// 操作管道输入流的线程
class Input implements Runnable{
    
    // 将管道输入流作为参数传入
    private PipedInputStream pis;

    public Input(PipedInputStream pis) {
        super();
        this.pis = pis;
    }

    @Override
    public void run() {
        // 读取缓冲区字节数组
        byte[] buf = new byte[1024];
        int len = 0;
        try {
            while((len=pis.read(buf)) != -1){
                System.out.println(new String(buf, 0, len));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }
    
}

// 操作管道输出流的线程
class Output implements Runnable{
    
    // 将管道输出流作为参数传入
    private PipedOutputStream pos;

    public Output(PipedOutputStream pos) {
        super();
        this.pos = pos;
    }

    @Override
    public void run() {
        while(true){
            try {
                pos.write("往管道输出流写数据\r\n".getBytes());
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } 
        }
    }
    
}