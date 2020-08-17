package io01;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class CopyTextByBuffered {
    public static void main(String[] args) throws IOException {
        /**
         * 复制文本文件:FileWriter是OutputStreamWriter的子类,默认使用本地编码表utf-8
         *           如果读取的文件是gbk编码的,那就只能使用其父类OutputStreamWriter指定charset为gbk,不指定的话也是默认使用本地编码表utf-8
         */
        
//          copy01();
        copy02();
    }

    public static void copy01() throws IOException {
        //  输入流缓冲区
        BufferedReader br = new BufferedReader(new FileReader("E://  java/java基础/Code/day23e/IO.txt"));
        //  输出流缓冲区
        BufferedWriter bw = new BufferedWriter(new FileWriter("E://  workspace/Java/src/io01/IO.txt"));
        
        //  一次读取一行
        String line = null;
        while((line = br.readLine()) != null){
            bw.write(line);
            bw.newLine();
            bw.flush();
        }
        
        //  关闭流
        br.close();
        bw.close();
    }
    
    private static void copy02() throws IOException, FileNotFoundException {
        //  输入流缓冲区
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("E://  java/java基础/Code/day23e/IO.txt"), "gbk"));
        //  输出流缓冲区
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("E://  workspace/Java/src/io01/IO.txt")));
        
        //  一次读取一行
        String line = null;
        while((line = br.readLine()) != null){
            bw.write(line);
            bw.newLine();
            bw.flush();
        }
        
        //  关闭流
        br.close();
        bw.close();
    }
}
