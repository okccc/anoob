package io03;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class DataStream {
    public static void main(String[] args) throws IOException {
        /**
         * 数据流可以操作java基本数据类型
         * DataInputStream
         * DataOutputStream
         * 
         * 构造函数:
         * DataInputStream(InputStream in)
         * DataOutputStream(OutputStream out)
         */
        
        read();
//          write();
    }

    public static void write() throws IOException {
        DataOutputStream dos = new DataOutputStream(new FileOutputStream("E://  workspace/Java/src/io03/haha.txt"));
        dos.writeUTF("啊,你好,丢丢丢丢~");
        dos.close();
    }

    /**
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static void read() throws FileNotFoundException, IOException {
        DataInputStream dis = new DataInputStream(new FileInputStream("E://  workspace/Java/src/io03/haha.txt"));
        String string = dis.readUTF();
        System.out.println(string);
        dis.close();
    }
}
