package io01;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;

public class TransformStream {
    public static void main(String[] args) throws IOException {
        /**
         * 转换流:
         * InputStreamReader:字节到字符,使用指定charset将字节解码为字符
         * OutputStreamWriter:字符到字节,使用指定charset将字符编码成字节
         * 
         * 构造函数:
         * InputStreamReader(InputStream in)
         * InputStreamReader(InputStream in, String charset)
         * 
         * OutputStreamWriter(OutputStream out)
         * OutputStreamWriter(OutputStream out, String charset)
         * 
         * 使用场景:
         * 1、源或者目标设备是字节流,但是操作的是文本数据
         * 2、操作文本时涉及具体编码表,必须使用转换流
         */
        
        console_console();
//          file_console();
    }

    //  需求一:将键盘录入数据显示在控制台
    public static void console_console() throws IOException {
        
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));
        
        String line = null;
        while((line = br.readLine()) != null){
            if("over".equals(line)){
                break;
            }
            bw.write(line);
            bw.newLine();
            bw.flush();
        }
    }
    
    //  需求二:将文件内容输出到控制台
    public static void file_console() throws IOException {
        
        //  获取输入流并关联文件
//          BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("E://  java/java基础/Code/day23e/IO.txt"),"gbk"));
        LineNumberReader br = new LineNumberReader(new InputStreamReader(new FileInputStream("E://  java/java基础/Code/day23e/IO.txt"),"gbk"));
        
        //  获取输出流并 关联控制台
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out,"utf-8"));
        
        //  读写数据
        String line =null;
        while((line = br.readLine()) != null){
            bw.write(br.getLineNumber()+":"+line);
            bw.newLine();
            bw.flush();
            //  如果是打印在控制台的话,上面三行等同于下一行
            //  System.out.println(line);
        }
        
        //  关流
        br.close();
    }
}
