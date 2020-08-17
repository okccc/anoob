package io01;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BufferedWriterDemo {
    public static void main(String[] args) throws IOException {
        /**
         * 操作文本时,为了提高读写效率可以使用字符流缓冲区   
         * 
         * 构造函数:
         * BufferedReader(Reader in)
         * BufferedWriter(Writer out)
         * 
         * BufferedReader:readLine()方法
         * BufferedWriter:nextLine()方法
         */
        
        //  创建字符输出流对象
        FileWriter fw = new FileWriter("aaa.txt");
        //  创建字符输出流缓冲区对象,关联字符输出流对象
        BufferedWriter bw = new BufferedWriter(fw);
        //  写入数据
        for(int i=1;i<=5;i++){
            bw.write("haha"+"..."+i);
            //  写入一个行分隔符
            bw.newLine();
            //  刷新该流的缓冲
            bw.flush();
        }
        //  关闭缓冲区,其实就是关闭被缓冲的流对象
        bw.close();
    }
}

class BufferedReaderDemo {
    public static void main(String[] args) throws IOException {
        
        //  创建字符输入流对象
        FileReader fr = new FileReader("IO.txt");
        //  创建字符输入流缓冲区对象,关联字符输入流对象
        BufferedReader br = new BufferedReader(fr);
        //  输入数据
        String line = null;
        while((line = br.readLine()) != null){
            System.out.println(line);
        }
        //  关闭流
        br.close();
        
    }
}
