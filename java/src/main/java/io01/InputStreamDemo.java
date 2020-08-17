package io01;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class InputStreamDemo {
    public static void main(String[] args) throws IOException {
        /**
         * IO流体系:
         * 
         * 字节流:
         * InputStream
         *      |--FileInputStream
         *      |--FilterInputStream
         *              |--BufferedInputStream
         *              |--DataInputStream
         *      |--SequenceInputStream
         *      |--ObjectInputStream
         *      |--PipedInputStream
         *      |--ByteInputStream
         * 
         * OutputStream
         *      |--FileOutputStream
         *      |--FilterOutputStream
         *              |--BufferedOutputStream
         *              |--DataOutputStream
         *      |--PrintStream
         *      |--ObjectOutputStream
         *      |--PipedOutputStream
         *      |--ByteOutputStream
         *      
         * 字符流:
         * Reader
         *      |--BufferedReader
         *              |--LineNumberReader
         *      |--InputStreamReader
         *              |--FileReader
         * Writer
         *      |--BufferedWriter
         *      |--OutputStreamWriter
         *              |--FileWriter
         *      |--PrintWriter
         *      
         */
        
        byte_read();
        byte_write();
    }

    private static void byte_read() throws IOException {
        //  创建一个读取字节流对象
        FileInputStream fis = new FileInputStream("byte.txt");
        
        //  读取一个字节
//          int ch = 0;
//          while((ch = fis.read()) != -1){
//              System.out.println((char)ch);
//          }
        
        //  读取缓冲区字节数组(建议使用)
        byte[] buf = new byte[1024];
        int len = 0;
        while((len = fis.read(buf)) != -1){
            System.out.println(new String(buf,0,len));
        }
        
        fis.close();
    }
    
    private static void byte_write() throws IOException {
        //  创建一个写入字节流对象
        FileOutputStream fos = new FileOutputStream("byte.txt");
        //  写入数据
        fos.write("hehe".getBytes());
        //  关流
        fos.close();
    }
}
