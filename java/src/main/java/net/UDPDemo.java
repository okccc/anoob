package net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPDemo {

    public static void main(String[] args) throws IOException {
        /**
         * UDF协议:
         * 
         * DatagramSocket:发送和接收数据包的套接字
         * DatagramSocket():随机绑定本机任意可用端口
         * DatagramSocket(int port):绑定本机指定端口
         * 
         * send(DatagramPacket dp):发送数据包
         * receive(DatagramPacket dp):接收数据包
         * close():关闭套接字,释放端口
         * 
         * DatagramPacket:数据包
         * DatagramPacket(byte[] buf, int length)
         * DatagramPacket(byte[] buf, int length, InetAddress address, int port)
         * buf:发送的数据内容
         * length:发送的数据大小(字节)
         * address:发送的目的IP地址
         * port:发送的目的端口号
         * 
         * getAddress():获取远程主机IP地址
         * getPort():获取远程主机端口号
         * getData():获取接收的数据(字节数组)
         * getLength():获取字节数组长度
         */
    }
    
}
    
class UDPSend{
    public static void main(String[] args) throws IOException {
        
        // 1、创建Socekt服务
        DatagramSocket ds = new DatagramSocket(8888);
        
        // 2、与输入流结合,获取数据源
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line = null;
        
        while((line=br.readLine()) != null){
            // 将数据转换成字节数组
            byte[] buf = line.getBytes();
            
            // 3、创建数据包,封装数据
            DatagramPacket dp = new DatagramPacket(buf, buf.length, InetAddress.getByName("10.172.20.38"), 9999);
            
            // 4、发送数据包
            ds.send(dp);
            
            if("over".equals(line)){
                break;
            }
        }
        
        // 5、关闭资源
        ds.close();
        
    }
}
 
class UDPReceive{

    private static DatagramSocket ds = null;

    public static void main(String[] args) throws IOException {
        
        System.out.println("接收端已启动");
        
        // 1、创建 Socket服务,并监听指定端口
        ds = new DatagramSocket(9999);
        
        while(true){
            
            // 2、创建空数据包,准备接收数据
            byte[] buf = new byte[1024];
            DatagramPacket dp = new DatagramPacket(buf, buf.length);
            
            // 3、接收数据包
            ds.receive(dp);   // 阻塞式的
            
            // 4、解析数据包中的数据
            String ip = dp.getAddress().getHostAddress();
            String name = dp.getAddress().getHostName();
            int port = dp.getPort();
            String text = new String(dp.getData(),0,dp.getLength());
            
            System.out.println(ip+"..."+name+"..."+text+"..."+port);
            
            if("over".equals(text)){
                break;
            }
        }
        
    }
}