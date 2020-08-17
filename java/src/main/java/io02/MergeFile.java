package io02;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class MergeFile {

    public static void main(String[] args) throws IOException {
        /**
         * 合并小文件:将多个同类型的小文件合并成一个大文件
         * 分析:先将小文件和输入流关联,然后用序列化流将这些输入流合并成一个流,再用输出流往目标文件里写
         * 
         * SequenceInputStream:可以将多个字节输入流按顺序合并成一个字节输入流
         * 
         * 构造函数:
         * SequenceInputStream(Enumeration<? extends InputStream> e)
         * 注意:构造函数参数是枚举类型,而集合里只有Vector才有枚举,但是效率低不常用,所以可通过集合框架工具类Collections获取
         */
        
        //  将目录封装成File对象
        File dir = new File("e://  test");
        
        //  指定合并后的文件位置
        File file = new File(dir, "merge.avi");
        
        mergeFile(dir, file);
    }

    private static void mergeFile(File dir, File file) throws IOException {
        //  获取目录下所有碎片文件
        File[] files = dir.listFiles(new SuffixFilter(".avi"));
        
        //  创建容器
        List<FileInputStream> list = new ArrayList<FileInputStream>();
        
        //  遍历数组
        for (int i = 0; i < files.length; i++) {
            //  将file对象添加到list集合
            list.add(new FileInputStream(files[i]));
        }
        
        //  用Collections工具类获取list集合的枚举
        Enumeration<FileInputStream> en = Collections.enumeration(list);
        
        //  将多个字节输入流合并成一个字节输入流
        SequenceInputStream sis = new SequenceInputStream(en);
        
        //  用输出流关联目标文件
        FileOutputStream fos = new FileOutputStream(file);
        
        //  读写数据
        byte[] buf = new byte[1024];
        int len = 0;
        while((len = sis.read(buf)) != -1){
            fos.write(buf, 0, len);
        }
        
        //  关流
        sis.close();
        fos.close();
    }
}
