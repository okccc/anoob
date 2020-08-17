package io03;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class ObjectStream {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        /**
         * ObjectOutputStream:对象序列化流,将java对象写入OutputStream,再关联文件可以实现对象持久化存储
         * ObjectInputStream:对象反序列化流,将序列化对的java对象重构
         * 
         * 构造函数:
         * ObjectOutputStream(OutputStream out)
         * ObjectInputStream(InputStream in)
         * 
         * 特点:序列化的对象必须实现Serializable接口
         */
        
        writeObj();
        readObj();
    }

    private static void writeObj() throws IOException {
        
        // 创建对象序列化流
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("E:// workspace/Java/src/io03/object.txt"));
        
        // 序列化
        oos.writeObject(new Person("grubby",30));
        
        // 关流
        oos.close();
    }
    
    private static void readObj() throws IOException, ClassNotFoundException {
        
        // 创建对象反序列化流
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream("E:// workspace/Java/src/io03/object.txt"));
        
        // 反序列化(重构对象)
        Person p = (Person)ois.readObject();
        System.out.println(p.getName()+"..."+p.getAge());
        
        // 关流
        ois.close();
    }
}
