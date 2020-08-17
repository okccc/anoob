package net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPChatDemo {
    public static void main(String[] args) throws IOException {
        /**
         * UDP协议模拟多人聊天程序:结合多线程技术
         */
        
        DatagramSocket ds1 = new DatagramSocket(5555);
        DatagramSocket ds2 = new DatagramSocket(6666);
        
        new Thread(new Send(ds1)).start();
        new Thread(new Rece(ds2)).start();
    }
}

// 发送端
class Send implements Runnable{
    
    // 将DatagramSocket作为参数传入
    private DatagramSocket ds;
    
    public Send(DatagramSocket ds) {
        super();
        this.ds = ds;
    }
    
    @Override
    public void run() {
        // 创建数据包
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line = null;
        try {
            while((line=br.readLine()) !=null){
                byte[] buf = line.getBytes();
                DatagramPacket dp = new DatagramPacket(buf, buf.length, InetAddress.getLocalHost(), 6666);
                ds.send(dp);
                if("over".equals(line)){
                    break;
                }
            }
            ds.close();
        } catch (IOException e) {
        }
    }

}

// 接收端
class Rece implements Runnable{
    
    private DatagramSocket ds;

    public Rece(DatagramSocket ds) {
        super();
        this.ds = ds;
    }

    @Override
    public void run() {
        while(true){
            byte[] buf = new byte[1024];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            try {
                ds.receive(dp);
                String text = new String(dp.getData(),0,dp.getLength());
                String address = dp.getAddress().getHostAddress();
                int port = dp.getPort();
                System.out.println(text+"..."+address+"..."+port);
                if("over".equals(text)){
                    break;
                }
            } catch (IOException e) {
            }
        }
    }

}
