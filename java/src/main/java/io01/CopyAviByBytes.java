package io01;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class CopyAviByBytes {
    public static void main(String[] args) throws IOException {
        /**
         * 复制AVI文件:只能使用字节流操作
         * 
         * 构造函数:
         * FileInputStream(File file)
         * FileInputStream(String name)
         * 
         * FileOutputStream(File file)
         * FileOutputStream(File file, Boolean append)
         * FileOutputStream(String name)
         * FileOutputStream(String name, Boolean append)
         */
        
//          copy01();
        copy02();
    }

    //  方法一:使用字节数组
    public static void copy01() throws IOException {
        
        //  创建字节流对象
        FileInputStream fis = new FileInputStream("e://  aaa.avi");
        FileOutputStream fos = new FileOutputStream("d://  bbb.avi");
        
        //  添加字节数组
        byte[] buf = new byte[1024];
        int len = 0;
        while((len = fis.read(buf)) != -1){
            fos.write(buf, 0, len);
        }
        
        //  关闭流
        fis.close();
        fos.close();
    }
    
    //  方法二:使用缓冲字节流
    public static void copy02() throws IOException {
        
        //  创建缓冲字节流对象,关联字节流
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream("e://  aaa.avi"));
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("d://  bbb.avi"));
        
        //  读取字节
        int ch = 0;
        while((ch = bis.read()) != -1){
            bos.write(ch);
        }
        
        //  关闭流
        bis.close();
        bos.close();
    }
}
