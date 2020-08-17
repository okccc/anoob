package io01;

import java.io.IOException;
import java.io.InputStream;

public class KeyboardInput {
    public static void main(String[] args) throws IOException {
        /**
         * 需求:将键盘输入的内容打印到控制台
         * 
         * 分析:因为键盘输入只读取一个字节,要拼成字符串再打印
         *     \r:按下回车符
         *     \n:换行
         *     
         * static InputStream System.in
         * static PrintStream System.out
         * static PrintStream System.err
         */
        
        //  获取键盘输入流
        InputStream in = System.in;
        
        //  创建字符串缓冲区对象
        StringBuilder sb = new StringBuilder();
        
        //  读取数据
        int ch = 0;
        while((ch = in.read()) != -1){
            //  \r表示按下回车键,不需要添加到sb
            if(ch=='\r'){
                continue;
            }else if(ch=='\n'){
                //  \n表示一行结束了,就打印下
                String temp = sb.toString();
                //  如果写入over就结束
                if("over".equals(temp)){
                    break;
                }
                //  打印到控制台
                System.out.println(temp);
                //  清空字符串缓冲区,下一行又是新的输入 
                sb.delete(0, sb.length());
            }else{
                //  没有\r\n特殊符号,就添加字符到sb
                sb.append((char)ch);
            }
        }
       
    }
}
