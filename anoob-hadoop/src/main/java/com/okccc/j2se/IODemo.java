package com.okccc.j2se;

import com.okccc.bean.Person;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * @Author: okccc
 * @Date: 2020/9/8 10:03
 * @Desc: java IO流
 *
 * IO是相对于内存设备而言
 * 键盘 -> System.in/out | 硬盘 -> FileXxx | 内存 -> 数组 | 网络 -> socket
 * 输入流：将外设数据读取到内存
 * 输出流：将内存数据写入到外设
 * 字符流：字节流读取字节数据后,先不直接操作而是查指定的编码表获取对应的文字,再对这个文字进行操作,字符流 = 字节流 + 编码表
 * 转换流：当字节流中的数据都是字符时,转换成字符流处理更加高效和方便,如果操作文本时涉及具体编码表也必须使用转换流
 * 字节流顶层父类：InputStream、OutputStream
 * 字符流顶层父类：Reader、Writer
 * 这些体系的子类特点：前缀表示功能,后缀是父类名,构造函数可以传入String路径/File对象
 * InputStream
 *     |--FileInputStream
 *     |--BufferedInputStream
 * OutputStream
 *     |--FileOutputStream
 *     |--BufferedOutputStream
 *     |--PrintStream
 * Reader
 *     |--FileReader
 *     |--BufferedReader
 *     |--InputStreamReader  // InputStream -> Reader
 * Writer
 *     |--FileWriter
 *     |--BufferedWriter
 *     |--OutputStreamWriter  // OutputStream -> Writer
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
 * 网络传输是通过IO流的字节序列实现,序列化就是将内存中的java对象转换成字节序列进行网络传输或持久化到硬盘(文件&数据库)
 * 为什么对象没被序列化也能在网络中传输？因为声明变量时String/int/Double/Boolean等数据类型已经实现了java.io.Serializable接口
 *
 * serialVersionUID
 * 在序列化和反序列化过程中为了保证类的版本一致性,通常会显式指定 private static final long serialVersionUID = 1L;
 * 不然jvm会根据类的结构自动生成一个默认值,当类结构发生变化时serialVersionUID也会跟着变,反序列化时就会报错 InvalidClassException
 * 查看String/Number/LocalDate等类的源码发现都显式指定了serialVersionUID
 *
 * transient关键字修饰的变量不会被序列化,比如身份证和密码等敏感信息,其生命周期仅存在于内存不会持久化到硬盘
 *
 * static关键字修饰的变量不会被序列化,因为序列化的目的是保存和恢复对象的状态,而静态变量属于类不属于某个对象
 */
public class IODemo {

    private static void byteStream() throws IOException {
        // 创建字节流对象
        FileInputStream fis = new FileInputStream("j2se/input/avatar.jpg");
        FileOutputStream fos = new FileOutputStream("j2se/output/avatar.jpg");
        // 读取字节数组
        byte[] arr = new byte[1024];
        // read()方法可以读取字节或字节数组,读到文件末尾则返回-1
        while (fis.read(arr) != -1) {
            // 输出到控制台
//            System.out.println(new String(arr));
            // 输出到文件
            fos.write(arr);
        }
        // 关闭流
        fos.close();
        fis.close();

        // 缓冲流先将数据写进缓冲区,然后再从内存中flush
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream("j2se/input/avatar.jpg"));
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("j2se/output/avatar.jpg"));
        // 读写数据
        byte[] buf = new byte[1024];
        while (bis.read(buf) != -1) {
            bos.write(buf);
        }
        // BufferedOutputStream和BufferedWriter的close()方法会调用flush(),所以一定要关闭流,或者write()之后手动flush()
        bos.close();
        bis.close();
    }

    private static void charStream() throws IOException {
        // 创建字符流对象
        FileReader fr = new FileReader("j2se/input/aaa.txt");
        FileWriter fw = new FileWriter("j2se/output/aaa.txt", true);
        // 读取字符数组
        int len;
        char[] arr = new char[1024];
        while ((len = fr.read(arr)) != -1) {
            // 输出到控制台
            System.out.print(new String(arr, 0, len));
            // 输出到文件
            fw.write(arr, 0, len);
        }
        // 关闭流
        fw.close();
        fr.close();

        // 使用更加高效的缓冲字符流,查看源码发现其实就是创建了字符数组,并且可以一次读取一行
        BufferedReader br = new BufferedReader(new FileReader("j2se/input/aaa.txt"));
        BufferedWriter bw = new BufferedWriter(new FileWriter("j2se/output/aaa.txt", true));
        // 读写数据
        String line;
        while ((line = br.readLine()) != null) {
            bw.write(line);
            bw.newLine();
        }
        // 关闭流
        bw.close();
        br.close();
    }

    private static void tryIOException() {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            // 创建字节流对象
            fis = new FileInputStream("j2se/input/avatar.jpg");
            fos = new FileOutputStream("j2se/output/avatar.jpg", true);
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
                if (fos != null) {
                    fos.close();
                }
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void objectStream() throws IOException, ClassNotFoundException {
        // 创建序列化流对象
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("j2se/input/person.dat"));
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream("j2se/input/person.dat"));
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

    private static void transformStream() throws IOException {
        // 查看源码发现 System.out.println() 其实就是 PrintStream.println() | PrintStream out = System.out -> out.println()
        InputStream in = System.in;    // 标准输入流
        PrintStream out = System.out;  // 标准输出流
        PrintStream err = System.err;  // 标准错误流

        // 标准输入流：System.in读取字节效率不高,可以将其封装成缓冲字符流,中间需要使用转换流转换
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        // LineNumberReader是BufferedReader子类,特点是可以获取行号
        LineNumberReader lnr = new LineNumberReader(new InputStreamReader(System.in));
//        lnr.setLineNumber(3);
        // 文件输入流
        BufferedReader br1 = new BufferedReader(new InputStreamReader(new FileInputStream("j2se/input/aaa.txt")));
        // 标准输出流
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));
        // 文件输出流
        BufferedWriter bw1 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("j2se/output/aaa.txt",true)));

        // 读写数据
        String line;
        while ((line = br.readLine()) != null) {
            if ("over".equalsIgnoreCase(line)) {
                break;
            }
            // 使用System.out之前可以重定向标准输出
            System.setOut(new PrintStream(new FileOutputStream("j2se/output/aaa.txt", true)));
            // 输出到控制台,查看源码发现 println() = write() + newline() + flush() 所以当输出在控制台时等价于下面三行
            System.out.println(line);
            // 输出到控制台或文件
            bw.write(line);
//            bw.write(lnr.getLineNumber() +": "+ line);
            bw.newLine();
            bw.flush();
        }
    }

    private static void sequenceStream() throws IOException {
        // 合并小文件(字节流版本)
        File dir = new File("input");
        File merge_file = new File("j2se/output/merge.txt");
        // 序列流可以将多个字节输入流合并成一个字节输入流,SequenceInputStream(Enumeration<? extends InputStream> e)
        // 由于构造函数参数是枚举类型,而集合体系只有Vector才有枚举,但是效率低不常用,可以通过集合框架工具类Collections转换
        // 存放输入流对象的集合
        List<BufferedInputStream> list = new ArrayList<>();
        // 深度遍历目录将符合条件的文件与输入流关联并添加到集合
        digui(dir, list);
        // 返回集合的枚举
        Enumeration<BufferedInputStream> en = Collections.enumeration(list);
        // 将多个输入流合并成一个输入流
        SequenceInputStream sis = new SequenceInputStream(en);
        // 输出流
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(merge_file));
        // 读写数据
        byte[] arr = new byte[1024];
        while (sis.read(arr) != -1) {
            bos.write(arr);
        }
        // 关流
        sis.close();
        bos.close();
    }

    private static void digui(File dir, List<BufferedInputStream> list) throws FileNotFoundException {
        // 获取文件(夹)路径
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isFile()) {
                if (file.getName().endsWith(".txt")) {
                    list.add(new BufferedInputStream(new FileInputStream(file)));
                }
            } else {
                digui(file, list);
            }
        }
    }

    public static void main(String[] args) throws IOException {
//        byteStream();
        charStream();
//        tryIOException();
//        objectStream();
//        transformStream();
//        sequenceStream();
    }
}