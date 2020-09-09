package basic;

import java.io.*;

@SuppressWarnings("unused")
public class IODemo {
    public static void main(String[] args) throws IOException, ClassNotFoundException {

        /*
         * IO是相对于内存设备而言
         * 输入流：将外设数据读取到内存
         * 输出流：将内存数据写入到外设
         * 字符流：字节流读取字节数据后,先不直接操作而是查指定的编码表获取对应的文字,再对这个文字进行操作
         * 字符流 = 字节流 + 编码表
         * 字节流顶层父类：InputStream、OutputStream
         * 字符流顶层父类：Reader、Writer
         * 这些体系的子类特点：前缀表示功能,后缀是父类名
         *
         * 字节流
         * InputStream
         *     |--FileInputStream
         *     |--FilterInputStream
         *         |--BufferedInputStream
         *         |--DataInputStream
         *     |--SequenceInputStream
         *     |--ObjectInputStream
         *     |--PipedInputStream
         *     |--ByteInputStream
         * OutputStream
         *     |--FileOutputStream
         *     |--FilterOutputStream
         *         |--BufferedOutputStream
         *         |--DataOutputStream
         *     |--PrintStream
         *     |--ObjectOutputStream
         *     |--PipedOutputStream
         *     |--ByteOutputStream
         *
         * 字符流
         * Reader
         *     |--BufferedReader
         *         |--LineNumberReader
         *     |--InputStreamReader
         *         |--FileReader
         * Writer
         *     |--BufferedWriter
         *     |--OutputStreamWriter
         *         |--FileWriter
         *     |--PrintWriter
         *
         * 装饰器模式
         * 装饰类用来包装原有的类,可以在不改变原先类结构的情况下动态扩展其功能,比继承更加灵活
         * 场景：如果为了实现某个功能,对体系内所有类都添加子类会很臃肿,为何不把功能本身单独封装呢？谁要用就装饰谁
         * Writer
         *     |--FileWriter  // 操作文件
         *         |--BufferedFileWriter
         *     |--StringWriter  // 操作字符串
         *         |--BufferedStringWriter
         *     ...
         * 装饰类和被装饰类要属于同一个父类或接口,这样才能在已有功能上扩展
         * class BufferedWriter extends Writer{
         *      BufferedWriter(Writer w){
         *          ...
         *      }
         * }
         *
         * 序列化
         * 将内存中的对象转换成字节进行持久化存储或网络传输,延长生命周期
         * 序列化和反序列化的读写顺序要一致,因为数据类型可能不一样
         * serialVersionUID给序列化的类添加版本号,兼容新旧版本,比如新版本加了字段反序列化时找不到旧版本的类会报错
         * 对象序列化时默认序列化所有属性,transient关键字修饰的属性除外,生命周期仅存在于内存不会持久化到硬盘,通常用于卡号、密码等敏感信息
         * 如果父类实现了Serializable接口,子类默认也实现了序列化
         */

//        byteStream();
//        charStream();
//        tryIOException();
        objectStream();
    }

    public static void byteStream() throws IOException {
        // 创建字节流对象
        FileInputStream fis = new FileInputStream("java/input/avator.jpg");
        FileOutputStream fos = new FileOutputStream("java/output/avator.jpg", true);
        // 读取字节数组
        byte[] arr = new byte[1024];
        // read()方法可以读取字节或字节数组,读到文件末尾则返回-1
        while (fis.read(arr) != -1) {
            fos.write(arr);
        }
        // 关闭流
        fos.close();
        fis.close();

        // 使用缓冲字节流,查看源码发现其实就是实现了创建字节数组这一步骤
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream("java/input/avator.jpg"));
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("java/output/avator.jpg"));
        // 读写数据
        int ch;
        while ((ch = bis.read()) != -1){
            bos.write(ch);
        }
        bos.close();
        bis.close();
    }

    public static void charStream() throws IOException {
        // 创建字符流对象
        FileReader fr = new FileReader("java/input/aaa.txt");
        FileWriter fw = new FileWriter("java/output/aaa.txt", true);
        // 读取字符数组
        int len;
        char[] c = new char[1024];
        while ((len = fr.read(c)) != -1){
//            System.out.print(new String(c, 0, len));
            fw.write(c, 0, len);
        }
        // 关闭流
        fw.close();
        fr.close();

        // 使用缓冲字符流
        BufferedReader br = new BufferedReader(new FileReader("java/input/aaa.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter("java/output/aaa.txt", true));
        // 读写数据
        String line;
        while ((line = br.readLine()) != null){
            bw.write(line);
            bw.newLine();
//            bw.flush();
        }
        bw.close();
        br.close();
    }

    public static void tryIOException(){
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            // 创建字节流对象
            fis = new FileInputStream("java/input/avator.jpg");
            fos = new FileOutputStream("java/output/avator.jpg", true);
            // 读取字节数组
            byte[] arr = new byte[1024];
            // read()方法可以读取字节或字节数组,读到文件末尾则返回-1
            while (fis.read(arr) != -1) {
                fos.write(arr);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(fos != null){
                    fos.close();
                }
                if(fis != null){
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void objectStream() throws IOException, ClassNotFoundException {
        // 创建序列化流对象
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("java/input/person.dat"));
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream("java/input/person.dat"));
        // 序列化
        oos.writeInt(100);
        oos.writeDouble(12.5);
        oos.writeUTF("hello");
        oos.writeObject(new Person("grubby", 18, "荷兰"));
        // 反序列化
        System.out.println(ois.readInt());  // 100
        System.out.println(ois.readDouble());  // 12.5
        System.out.println(ois.readUTF());  // hello
        System.out.println(ois.readObject());  // grubby: 18  没有idcard说明transient关键字修饰的变量不会被序列化
        // 关闭流
        ois.close();
        oos.close();
    }

}

//  被装饰类
class Man{
    public void eat(){
        System.out.println("来碗大米饭!");
    }
}

//  装饰类
class DecoratorMan {
    private Man m;
    //  将已有对象作为装饰类的构造函数的参数传入
    DecoratorMan(Man m){
        this.m = m;
    }
    public void eat(){
        System.out.println("先来点水果！");
        m.eat();
        System.out.println("再来点甜品！");
    }
}
