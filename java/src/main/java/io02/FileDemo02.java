package io02;

import java.io.File;

public class FileDemo02 {
    public static void main(String[] args) {
        /**
         * 遍历文件夹:
         * 
         * list():返回String数组
         * list(FilenameFilter filter)
         * 
         * listFiles():返回File数组
         * listFiles(FilenameFilter filter):添加文件名过滤器
         * listFiles(FileFilter filter):添加文件过滤器
         * 
         * static listRoots():获取系统根目录
         * 
         * 注意:只能遍历文件夹,遍历文件NullPointerException
         */
        
        //  指定目录
        File dir = new File("e://  ");
        
//          listRoots();
//          list01(dir);
//          list02(dir);
//          listFiles01(dir);
        listFiles02(dir);
        
    }

    /**
     * @param dir
     */
    public static void listFiles02(File dir) {
        File[] files = dir.listFiles(new FileFilterByHidden());
        for (int i = 0; i < files.length; i++) {
            System.out.println(files[i]);
        }
    }

    /**
     * @param dir
     */
    public static void listFiles01(File dir) {
        //  获取文件和目录路径
        File[] files = dir.listFiles();
        for (int i = 0; i < files.length; i++) {
            System.out.println(files[i]);
        }
    }

    /**
     * @param dir
     */
    public static void list02(File dir) {
        //  指定过滤器遍历文件名称
        String[] list2 = dir.list(new SuffixFilter(".txt"));
        for (int i = 0; i < list2.length; i++) {
            System.out.println(list2[i]);
        }
    }

    /**
     * @param dir
     */
    public static void list01(File dir) {
        //  获取文件和目录名称
        String[] list = dir.list();
        for (int i = 0; i < list.length; i++) {
            System.out.println(list[i]);
        }
    }

    /**
     * 
     */
    public static void listRoots() {
        //  获取系统根目录
        File[] roots = File.listRoots();
        for (int i = 0; i < roots.length; i++) {
            System.out.println(roots[i]);
        }
    }
}
