package io02;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

public class FileDemo01 {
    public static void main(String[] args) throws IOException {
        /**
         * File:文件和目录路径名的抽象表现形式
         * 
         * 构造函数:
         * File(String pathname)
         * File(File parent, String child)
         * File(String parent, String child)
         */
        
        //  将文件或文件夹封装成File对象
        File file = new File("E://  workspace/Java/src/io01/IO.txt");
        File dir = new File("e://  aaa/bbb/ccc");
        
        method01(file);
        method02(file);
        method03(dir);
        
    }

    //  1、获取功能
    private static void method01(File file) {
        //  文件名称
        String name = file.getName();
        //  文件路径
        String path1 = file.getAbsolutePath();
        String path2 = file.getPath();
        //  文件大小(如果文件不存在长度为0)
        long length = file.length();
        //  文件修改时间
        long time = file.lastModified();
        Date date = new Date(time);
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);
        String date1 = dateFormat.format(date);
        //  判断
        boolean b1 = file.exists();
        boolean b2 = file.isFile();
        boolean b3 = file.isDirectory();
        //  重命名
        File file2 = new File("E://  workspace/Java/src/io01/IO01.txt");
        boolean b4 = file.renameTo(file2);
        
        System.out.println(name);
        System.out.println(path1);
        System.out.println(path2);
        System.out.println(length);
        System.out.println(date1);
        System.out.println(b1+"..."+b2+"..."+b3+"..."+b4);
    }

    //  2、文件的创建和删除
    private static void method02(File file) throws IOException {
        //  返回值是boolean类型,文件存在就不创建,不存在就创建
        boolean b1 = file.createNewFile();
        System.out.println(b1);
        //  删除文件
        boolean b2 = file.delete();
        System.out.println(b2);
    }
    
    //  3、文件夹的创建和删除
    private static void method03(File dir) {
        //  创建单级目录
        boolean b1 = dir.mkdir();
        System.out.println(b1);
        //  创建多级目录
        boolean b2 = dir.mkdirs();
        System.out.println(b2);
        //  删除目录
        boolean b3 = dir.delete();
        System.out.println(b3);
    }
}
