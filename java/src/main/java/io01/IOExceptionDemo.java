package io01;

import java.io.FileWriter;
import java.io.IOException;

public class IOExceptionDemo {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public static void main(String[] args) {
        /**
         * IO流的异常处理 
         */
        
        //  将fw定义在try外面,做全局变量,不然finally里面的fw无法识别 
        FileWriter fw = null;
        
        try {
//              FileWriter fw = new FileWriter("E://  workspace/Java/src/io01/test.txt", true);
            fw = new FileWriter("E://  workspace/Java/src/io01/test.txt", true);

            fw.write("haha" + LINE_SEPARATOR + "hehe"); //  LINE_SEPARATOR表示换行符

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //  先判断下fw是否为null值
            if(fw != null){
                try {
                    //  流最终都要关闭,所以放finally代码块中
                    fw.close();
                } catch (IOException e) {
                    throw new RuntimeException("关闭流失败！");
                }
            }
        }
    }
}
