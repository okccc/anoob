package net;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IPDemo {
    public static void main(String[] args) throws UnknownHostException {
        /**
         * 网络模型: OSI参考模型                                                TCP/IP参考模型
         *         应用层 --->处理网络应用                          应用层--->HTTP/FTP    http:超文本传输协议 HyperText Transfer Protocol
         *         表示层 --->数据表示                                                                                          ftp:文件传输协议       File Transfer Protocol
         *         会话层 --->主机间通信               
         *         传输层 --->端到端的连接                          传输层--->TCP/UDP
         * (路由器 ) 网络层 --->寻址和最短路径                       网络层--->IP
         * (交换机 ) 链路层 --->介质访问                       
         *         物理层 --->二进制传输                             链路层
         * 
         * 网络通信三要素:
         * 
         * IP地址:InetAddress类没有构造函数,由静态方法返回该类对象:getLocalHost()、getByName(String host)
         *       getHostAddress():IP地址
         *       getHostName():主机名全称
         * 127.0.0.1:本地回环地址
         * 测试网卡:ping 127.0.0.1
         * 屏蔽网址:在host文件里将要屏蔽的网址都配成127.0.0.1
         *         
         * 端口号:因为IP地址与网络服务(http,ftp,smtp等)是一对多关系,只有IP地址是不够的,实际上是通过"IP+端口号"来区分不同服务
         *      有效端口:0~65533,其中0~1024是系统使用端口
         *      80端口: http服务
         *      21端口: ftp服务
         *         
         * 协议:通讯规则--->TCP/UDP
         *     UDP协议:不建立链接,不可靠,速度快,将数据封包传输(不超过64k)
         *     TCP协议:建立连接(3次握手)形成通道,可靠,效率低,传输较大数据
         *     
         * 网络结构:
         * C/S   client/server:客户端和服务端都要开发,成本高,不易维护,但是客户端可以分担一部分运算
         * B/S   browser/server:只开发服务端,成本低,易维护,客户端由浏览器替代
         */
        
        // 获取本机IP
        InetAddress ia = InetAddress.getLocalHost();
        System.out.println(ia.getHostName()+"..."+ia.getHostAddress());
        
        // 获取任意机器IP
        InetAddress ia2 = InetAddress.getByName("DN0858.qbad.com");
        System.out.println(ia2.getHostName()+"..."+ia2.getHostAddress());
        
    }
}
