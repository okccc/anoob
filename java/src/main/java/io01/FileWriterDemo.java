package io01;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class FileWriterDemo {
    
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public static void main(String[] args) throws IOException {
        /**
         * IO是相对于内存设备而言
         * 输入流:将外设数据读取到内存
         * 输出流:将内存数据写入到外设
         * 
         * 字符流:字节流读取字节数据后,先不直接操作而是查指定的编码表,获取对应的文字,再对这个文字进行操作
         * 字符流 = 字节流+编码表
         * 
         * 字节流顶层父类:InputStream、OutputStream
         * 字符流顶层父类:Reader、Writer
         * 这些体系的子类特点:前缀表示功能,后缀是父类名
         * 
         * 构造函数:
         * FileReader(File file)
         * FileReader(String filename)
         * 
         * FileWriter(File file)
         * FileWriter(File file, Boolean append)
         * FileWriter(String filename)
         * FileWriter(String filename, Boolean append)
         */
        
        //  创建一个可以写入数据的对象,需要指定目标文件(没有就添加,有就覆盖),true表示可以追加写入
        FileWriter fw = new FileWriter("E://  workspace/Java/src/io01/IO.txt",true);
        
        //  写入数据(其实是放在临时缓冲区)
        fw.write("haha"+LINE_SEPARATOR+"hehe");  //  LINE_SEPARATOR表示换行符,用System类自动获取,这样就不用区分Windows/Linux
        
        //  刷新数据到文件中
//          fw.flush();
        
        //  关闭流(在关闭前会先调用flush方法)
        fw.close();
    }
}

class FileReaderDemo {
    public static void main(String[] args) throws IOException {
        /**
         * 注意:FileReader/FileWriter构造方法使用本机默认字符编码,如果要手动指定字符编码,要用其父类InputStreamReader/OutputStreamWriter
         */
        
        //  创建一个可以读取数据的对象
        FileReader fr = new FileReader("E://  workspace/Java/src/io01/test.txt");
        
        //  方式一:读取单个字节
        int i = 0;
        while((i=fr.read()) != -1){
            System.out.println(i+"..."+(char)i);
        }
        
        //  方式二:读取字符数组(建议使用)
        char[] arr = new char[1024];   //  字符数组长度是1024整数倍即可
        int len = 0;
        while((len=fr.read(arr)) != -1){
            System.out.println(new String(arr,0,len));
        }
        
        //  关闭流
        fr.close();
        
    }
}
