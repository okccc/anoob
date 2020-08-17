package io02;

import java.io.File;

public class Recursive {
    public static void main(String[] args) {
        /**
         * 递归算法:函数直接或间接调用自身的算法
         * 注意:递归要有出口,不然只进栈不出栈就会死循环,由于递归比一般程序多占用很多内存资源,递归太深的话系统可能扛不住,慎用！
         */
        
        //  1、兔子问题
        System.out.println(fibonacci(24));
        //  2、1到100相加
        System.out.println(getSum(100));
        //  3、1到100阶乘
        System.out.println(getFactorial(15));
        
        //  4、深度遍历文件夹
        File dir = new File("E://  小甲鱼/Python基础班/Python基础班视频");
        listAll(dir,0);
        
        //  5、删除带内容的文件夹
//          removeAll(dir);
        
        //  6、删除空文件夹
        removeAllEmpty(dir);
    }
    
    public static void removeAllEmpty(File dir) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if(file.isDirectory()){
                if(file.length()==0){
                    file.delete();
                }else{
                    removeAllEmpty(file);
                }
            }
        }
    }

    /**
     * 分析:首先多级目录要深度遍历,然后先删每一层的文件,再删该层文件夹
     */
    public static void removeAll(File dir) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if(file.isDirectory()){
                removeAll(file);
            }else{
                file.delete();
            }
        }
        dir.delete();
    }

    /**
     * 
     */
    public static void listAll(File dir,int level) {
        
        File[] files = dir.listFiles();
        level++;
        
        for (int i = 0; i < files.length; i++) {
            //  如果是目录就继续遍历
            if(files[i].isDirectory()){
                listAll(files[i],level);
            }else{
                System.out.println(printSpace(level)+files[i].getName());
            }
        }
    }

    public static String printSpace(int level) {
        StringBuilder sb = new StringBuilder();
        sb.append("|--");
        for(int x=0;x<level;x++){
            sb.insert(0, "|--");
        }
        return sb.toString();
    }

    /**
     * 求阶乘
     * @param i
     * @return
     */
    public static long getFactorial(int i) {
        if(i==1){
            return 1;
        }else{
            return getFactorial(i-1) * i;
        }
    }

    /**
     * 求和
     * @param i
     * @return
     */
    public static int getSum(int i) {
        if(i==1){
            return 1;
        }else{
            return getSum(i-1) + i;
        }
    }

    /**
     * 菲波那切数列
     * @param i
     * @return
     */
    public static int fibonacci(int i) {
        if(i==1 | i==2){
            return 1;
        }else{
            return fibonacci(i-1) + fibonacci(i-2);
        }
    }
}
