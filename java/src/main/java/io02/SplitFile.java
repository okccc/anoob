package io02;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

@SuppressWarnings("unused")
public class SplitFile {
    private static final int SIZE = 1048576;

    public static void main(String[] args) throws IOException {
        /**
         * 文件切割器:将文件按照指定大小切割成多个小文件
         */
        
        //  将文件封装成File对象
        File file = new File("e://  aaa.avi");
        
        //  指定存放切割后的文件目录
        File dir = new File("e://  test");
        if(!dir.exists()){
            dir.mkdirs();
        }
        
        splitFile(file,dir);
    }

    //  将文件按1M大小切割
    public static void splitFile(File file, File dir) throws IOException {
        //  关联字节输入流
        FileInputStream fis = new FileInputStream(file);
        
        //  定义1M大小的缓冲区
        byte[] buf = new byte[SIZE];
        
        //  输出流
        FileOutputStream fos = null;
        
        //  读写数据
        int len = 0;
        int count = 0;
        while((len = fis.read(buf)) != -1){
            //  循环生成文件,并关联输出流往多个文件中写数据
            fos = new FileOutputStream(new File(dir, getCount(++count)+".avi"));
            fos.write(buf, 0, len);
            fos.close();
        }
        
//          //  记录源文件及被切割的次数
//          Properties prop = new Properties();
//          fos = new FileOutputStream(new File(dir, "readme.txt"));
//          prop.setProperty("file", file.getAbsolutePath());
//          prop.setProperty("count", count+"");
//          prop.store(fos, "save info!");
        
        //  关流
        fis.close();
        fos.close();
    }

    private static String getCount(int i) {
        return i<10?("0"+i):(i+"");
    }
}
