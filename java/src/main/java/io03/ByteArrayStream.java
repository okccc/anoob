package io03;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class ByteArrayStream {
    public static void main(String[] args) {
        /**
         * 字节数组流操作的都是内存中的数据
         * ByteArrayInputStream
         * ByteArrayOutputStream
         * 
         * 构造函数:
         * ByteArrayInputStream(byte[] buf)
         * ByteArrayOutputStream()
         * 
         * 特点:字节数组流不需要关闭 
         */
     
        //  创建字节数组输入输出流
        ByteArrayInputStream bis = new ByteArrayInputStream("ahcbchwdbw".getBytes());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        //  读写数据
        int ch = 0;
        while((ch=bis.read()) != -1){
            bos.write(ch);
        }
        
        //  打印结果
        System.out.println(bos);
    }
}
