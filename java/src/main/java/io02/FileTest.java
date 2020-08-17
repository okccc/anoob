package io02;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileTest {

    public static void main(String[] args) throws IOException {
        /**
         * 需求:将某个目录下所有指定扩展名的文件的绝对路径都写到新的文件中
         * 
         * 分析:所有--->深度遍历
         *     指定扩展名--->FilenameFilter
         *     数据很多且数量不确定要先往容器存储--->ArrayList
         */
        
        //  将目录封装成File对象
        File dir = new File("e://  ");
        
        //  创建文件名过滤器
        FilenameFilter filter = new FilenameFilter(){
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".bmp");
            }
            
        };
        
        //  创建存储文件路径的容器
        List<File> list = new ArrayList<File>();
        
        //  深度遍历
        listAll(dir,filter,list);
        
        //  创建要写入数据的目标文件
        File destfile = new File(dir,"javalist.txt");
        
        //  写入数据
        writeToDestfile(list,destfile);
    }

    private static void listAll(File dir,FilenameFilter filter, List<File> list) throws IOException {
        
        //  遍历文件夹
        File[] files = dir.listFiles();
        
        //  遍历File数组
        for (File file : files) {
            if(file.isDirectory()){
                //  递归啦
                listAll(file,filter,list);
            }else{
                //  将符合过滤条件的文件名存储到List集合
                if(filter.accept(dir, file.getName())){
                    list.add(file);
                }
            }
        }
    }

    private static void writeToDestfile(List<File> list,File destfile) throws IOException {
        //  将目标文件关联字符流缓冲区
        BufferedWriter bw = new BufferedWriter(new FileWriter(destfile));
        //  遍历集合
        for (File file : list) {
            //  写入数据
            bw.write(file.getAbsolutePath());
            bw.newLine();
            bw.flush();
        }
        //  关闭流
        bw.close();
    }
    
}
