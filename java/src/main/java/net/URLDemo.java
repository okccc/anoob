package net;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class URLDemo {
    public static void main(String[] args) throws IOException {
        /**
         * URL:统一资源定位符
         * URI:统一资源标识符
         * URL是URI的一种,URL一定是URI,URI不一定是URL
         * 
         * InputStream openStream():获取打开此URL的输入流
         */
        
        // 封装URL对象
        URL url = new URL("http:// 10.172.20.38:8080/examples/index.html");
        
        String path = url.getPath();
        String protocol = url.getProtocol();
        String host = url.getHost();
        String file = url.getFile();
        String query = url.getQuery();
        System.out.println("path:"+path);
        System.out.println("protocol:"+protocol);
        System.out.println("host:"+host);
        System.out.println("file:"+file);
        System.out.println("query:"+query);
        
//         URLConnection con = url.openConnection();
//         System.out.println(con);  // sun.net.www.protocol.http.HttpURLConnection:http:// 10.172.20.38:8080/examples/index.html
//         
//         InputStream in = con.getInputStream();
//         System.out.println(in);   // sun.net.www.protocol.http.HttpURLConnection$HttpInputStream@6693f61c
        
        // 获取打开该URL的输入流,等同于url.openConnection().getInputStream()
        InputStream in = url.openStream();
        
        // 读写数据
        byte[] buf = new byte[1024];
        int len = 0;
        while((len=in.read(buf)) != -1){
            System.out.println(new String(buf,0,len));
        }
        
    }
}
