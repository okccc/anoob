package net;

import java.io.*;
import java.net.*;

@SuppressWarnings("unused")
public class IONetDemo {
    public static void main(String[] args) throws Exception {
        /*
         * 网络模型
         * OSI七层模型                       TCP/IP模型
         * 应用层 -- 网络应用                 应用层 -- HTTP(HyperText Transfer Protocol)/FTP/DNS/SMTP协议
         * 表示层 -- 数据表示
         * 会话层 -- 主机间通信
         * 传输层 -- 端到端的连接             传输层 -- TCP(Transmission Control Protocol)/UDP(User Datagram Protocol)协议
         * 网络层 -- 寻址和最短路径(路由器)     网络层 -- IP(Internet Protocol)协议：ip地址
         * 链路层 -- 介质访问(交换机)          链路层 -- arp协议：mac地址
         * 物理层 -- 二进制传输(网卡,网线)
         *
         * 网络结构
         * CS结构(Client/Server)：客户端和服务端都要开发,成本高,不易维护,但是客户端可以分担一部分运算
         * BS结构(Browser/Server)：只开发服务端,成本低,易维护,客户端由浏览器替代
         *
         * URI(Uniform Resource Identifier)：统一资源标识符,what the resource is
         * URL(Uniform Resource Locator)：统一资源定位符,是URI的子集,what the resource is & how to get the resource
         *
         * 网络通信三要素
         * ip
         * 测试网卡：ping 127.0.0.1(本地回环地址)
         * 屏蔽网址：在host文件里将要屏蔽的网址都配成127.0.0.1
         *
         * 端口
         * 同一台机器可以部署http/ftp/smtp等多种网络服务,使用"ip+端口"确定唯一服务
         * 有效端口：0~65533,其中0~1024是系统使用端口 | 80端口：http服务 | 21端口：ftp服务
         *
         * 协议
         * UDP协议：不建立链接,不可靠,速度快,将数据封包传输(不超过64k)
         * TCP协议：建立连接(3次握手)形成通道,可靠,效率低,传输较大数据
         *
         * Socket是基于tcp/ip协议的网络编程,套接字是两台机器间网络通信的端点
         * ServerSocket 服务端套接字 | Socket 客户端套接字
         * IO流是读写本地文件,末尾有-1或null作为结束标记,Socket是读写网络数据,网络传输是连续的没有末尾,需要自定义结束标记
         *
         * UDP协议
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

//        testInet();
//        testUrl();
    }

    private static void testInet() throws Exception {
        // 获取本机的InetAddress对象
        InetAddress ia = InetAddress.getLocalHost();
        System.out.println(ia);  // DESKTOP-NSSIK3O/169.254.131.239
        System.out.println(ia.getHostName() +" - "+ ia.getHostAddress());  // DESKTOP-NSSIK3O - 169.254.131.239
        // 获取指定ip地址或主机名的InetAddress对象
        InetAddress ia2 = InetAddress.getByName("DN0858.qbad.com");
        System.out.println(ia2);  // DN0858.qbad.com/69.172.201.153
        System.out.println(ia2.getHostName() +" - "+ ia2.getHostAddress());  // DN0858.qbad.com - 69.172.201.153
    }

    private static void testUrl() throws IOException {
        // 创建url对象
        URL url = new URL("https://www.baidu.com/s?wd=lol");
        // 获取url详细信息
        System.out.println(url.getProtocol());  // https
        System.out.println(url.getHost());  // www.baidu.com
        System.out.println(url.getPort());  // -1表示没有设置端口
        System.out.println(url.getDefaultPort());  // 443
        System.out.println(url.getFile());  // /s?wd=lol
        System.out.println(url.getPath());  // /s
        System.out.println(url.getQuery());  // wd=lol
        System.out.println(url.getAuthority());  // // www.baidu.com
        System.out.println(url.getContent());  // sun.net.www.protocol.http.HttpURLConnection$HttpInputStream@612679d6
        System.out.println(url.getRef());  // null
        System.out.println(url.getUserInfo());  // null
        // 获取打开该url连接的输入流
        InputStream is = url.openStream();
        // 创建输出流
        FileOutputStream fos = new FileOutputStream("java/output/baidu",true);
        // 读写数据
        byte[] arr = new byte[1024];
        while(is.read(arr) != -1){
//            System.out.println(new String(arr));
            fos.write(arr);
        }
        is.close();
        fos.close();
    }

}

class TCPServer {
    public static void main(String[] args) throws IOException {
        // 创建服务端Socket服务,绑定端口
        ServerSocket ss = new ServerSocket(9999);
        // 监听Socket,该方法阻塞直到建立连接为止
        Socket s = ss.accept();
        // 检测是否有客户端连接
        String ip = s.getInetAddress().getHostAddress();
        String host = s.getInetAddress().getHostName();
        int port = s.getPort();
        System.out.println(ip +" - "+ host +" - "+ port +"...已连接");

        // Socket输入流
        InputStream is = s.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
        // Socket输出流
        OutputStream os = s.getOutputStream();
        PrintWriter pw = new PrintWriter(s.getOutputStream(),true);
        // 字节文件输出流
        File file = new File("java/output/avatar.jpg");
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
        // 字符文件输出流
        BufferedWriter bw = new BufferedWriter(new FileWriter("java/output/aaa.txt"));

        // 聊天信息
//        String line;
//        while ((line = br.readLine()) != null) {
//            // 结束标记
//            if ("over".equalsIgnoreCase(line)) {
//                break;
//            }
//            // 输出到控制台
//            System.out.println(line);
//            // 响应客户端, println() = write() + newLine() + flush()
//            pw.println(line.toUpperCase());
//        }

        // 接收二进制文件
        byte[] buf = new byte[1024];
        while (is.read(buf) != -1) {
            bos.write(buf);
            // 可以限制文件大小
            if (file.length() > 102400) {
                bos.close();
                pw.println("上传失败,文件大小超过限制" + file.delete());
                break;
            }
        }
        // 响应客户端
        pw.println("上传成功");

        // 接收文本文件
//        String line1;
//        while ((line1 = br.readLine()) != null) {
//            bw.write(line1);
//            bw.newLine();
//        }
//        // 响应客户端
//        pw.println("上传成功");

        // 关闭流和套接字,缓冲输出流的close方法自带flush功能,所以一定要关流,不然数据还在缓冲区没有刷出去
        bos.close();
        bw.close();
//        ss.close();
    }
}

class TCPClient {
    public static void main(String[] args) throws IOException {
        // 创建客户端Socket服务,指定ip和端口
        Socket s = new Socket(InetAddress.getLocalHost(), 9999);

        // 标准输入流
        BufferedReader br1 = new BufferedReader(new InputStreamReader(System.in));
        // 字节文件输入流
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream("java/input/avatar.jpg"));
        // 字符文件输入流
        BufferedReader br2 = new BufferedReader(new FileReader("java/input/aaa.txt"));
        // Socket输入流
        InputStream is = s.getInputStream();
        BufferedReader br3 = new BufferedReader(new InputStreamReader(s.getInputStream()));
        // Socket输出流
        OutputStream os = s.getOutputStream();
        PrintWriter pw = new PrintWriter(s.getOutputStream(), true);

        // 聊天信息
//        String line;
//        while ((line = br1.readLine()) != null) {
//            // 客户端先发送数据,PrintStream/PrintWriter类的 println() = write() + flush() + newLine()
//            pw.println(line);
//            // 结束标记
//            if ("over".equalsIgnoreCase(line)) {
//                break;
//            }
//            // 接收服务端返回数据
//            String res = br3.readLine();
//            System.out.println(res);
//        }

        // 上传二进制文件
        byte[] buf = new byte[1024];
        int len;
        while ((len = bis.read(buf)) != -1) {
            os.write(buf, 0, len);
        }
        // 禁用该Socket的输出流,将先前写入的数据发送出去
//        s.shutdownOutput();
        // 接收服务端响应
        System.out.println(br3.readLine());

        // 上传文本文件
//        String line1;
//        while ((line1 = br2.readLine()) != null) {
//            pw.println(line1);
//        }
//        // 禁用此套接字输出流,将之前写入的数据发送
//        s.shutdownOutput();
//        // 接收服务端响应
//        System.out.println(br3.readLine());

        // 关闭套接字
        s.close();
    }
}
