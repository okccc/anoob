package net;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class MyBrowser {
    public static void main(String[] args) throws UnknownHostException, IOException {
        /**
         * 模拟一个浏览器访问Tomcat
         * 
         * 浏览器发送请求:
         * 
         * 请求行:(请求方式、请求资源路径、http协议版本)
         * GET /examples/index.html HTTP/1.1
         * 
         * 请求头:(K:V)
         * Accept: ../..
         * Host: 10.172.20.38:8080
         * Connection: keep-alive
         * 
         * 请求体:
         *  
         * 服务器应答消息:
         * 
         * 应答行:(http协议版本、状态密码、状态描述信息)
         * HTTP/1.1 200 OK 
         *  
         * 应答属性信息:(K:V) 
         * Server: Apache-Coyote/1.1 
         * Accept-Ranges: bytes 
         * ETag: W/"1156-1498489732000" 
         * Last-Modified: Mon, 26 Jun 2017 15:08:52 GMT 
         * Content-Type: text/html
         * Content-Length: 1156 
         * Date: Tue, 15 Aug 2017 02:33:40 GMT 
         * 
         * 应答体:
         * <!--
          Licensed to the Apache Software Foundation (ASF) under one or more
          contributor license agreements.  See the NOTICE file distributed with
          this work for additional information regarding copyright ownership.
          The ASF licenses this file to You under the Apache License, Version 2.0
          (the "License"); you may not use this file except in compliance with
          the License.  You may obtain a copy of the License at
        
              http:// www.apache.org/licenses/LICENSE-2.0
        
          Unless required by applicable law or agreed to in writing, software
          distributed under the License is distributed on an "AS IS" BASIS,
          WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
          See the License for the specific language governing permissions and
          limitations under the License
        .
        -->
        <!DOCTYPE HTML><html lang="en"><head>
        <meta charset="UTF-8">
        <title>Apache Tomcat Examples</title>
        </head>
        <body>
        <p>
        <h3>Apache Tomcat Examples</H3>
        <p></p>
        <ul>
        <li><a href="servlets">Servlets examples</a></li>
        <li><a href="jsp">JSP Examples</a></li>
        <li><a href="websocket/index.xhtml">WebSocket Examples</a></li>
        </ul>
        </body></html>
         */
        
      // 创建Socket服务,指定ip和端口
      Socket s = new Socket("10.172.20.38", 8080);
      
      // 获取Socket输出流
      PrintWriter out = new PrintWriter(s.getOutputStream(),true);
      
      out.println("GET /examples/index.html HTTP/1.1");
      out.println("Accept: */*");
      out.println("Host: 10.172.20.38:8080");
      out.println("Connection: keep-alive");
      out.println();
      
      // 获取Socket输入流
      InputStream in = s.getInputStream();
      byte[] buf = new byte[1024];
      int len = 0;
      while((len=in.read(buf)) != -1){
          System.out.println(new String(buf,0,len));
      }
      
      // 关闭资源
      s.close();
        
    }

}
