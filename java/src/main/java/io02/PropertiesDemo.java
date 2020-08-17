package io02;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;

public class PropertiesDemo {
    public static void main(String[] args) throws IOException {
        /**
         * Map接口
         *   |--HashTable
         *      |--Properties
         *      
         * Properties:1、持久化的Map集合
         *            2、键和值都是String类型
         *            3、可以关联IO流使用    Map + IO = Properties
         * 
         * stringPropertyNames():
         * getProperties():
         * 
         * list(PrintStream out):
         * list(PrintWriter out):
         * 
         * load(InputStream in):
         * load(Reader r):
         * 
         * store(OutputStream out,String comments):
         * store(Writer w,String comments):
         */
        
        //  创建对象
        Properties prop = new Properties();
        //  存储元素
        prop.put("grubby", "orc");
        prop.put("moon", "ne");
        prop.put("ted", "ud");
        prop.put("sky", "human");
        prop.put("fly", "human");
        
//          method01(prop);
//          method02(prop);
        method03();
    }

    /**
     * 需求:修改已有的配置文件信息
     * 分析:配置文件都是kv对,先读到Properties中,用集合操作完,再写回配置文件
     * @throws IOException 
     */
    private static void method03() throws IOException {
        //  将配置文件封装成File对象
        File file = new File("E://  workspace/Java/src/io02/properties.txt");
        //  关联输入流
        FileReader fr = new FileReader(file);
        //  创建Properties属性集合
        Properties prop = new Properties();
        //  从输入流加载数据
        prop.load(fr);
        //  修改数据
        prop.setProperty("fly", "orc");
        //  关联输出流
        FileWriter fw = new FileWriter(file);
        //  往输出流存储数据
        prop.store(fw, "modify");
        //  关闭流
        fr.close();
        fw.close();
    }

    /**
     * 演示Properties和IO流结合使用
     * @throws IOException 
     */
    public static void method02(Properties prop) throws IOException {
        
//          //  1、将系统属性信息输出在控制台
//          prop = System.getProperties();
//          prop.list(System.out);
        
        //  2、将集合信息写入到文件中
        FileOutputStream fos = new FileOutputStream("E://  workspace/Java/src/io02/properties.txt");
        //  此处Comment是16进制,不要写中文
        prop.store(fos, "wars players!");
        fos.close();
        
        //  3、从文件中读取集合信息
        FileInputStream fis = new FileInputStream("E://  workspace/Java/src/io02/properties.txt");
        prop.load(fis);
        prop.list(System.out);
        fis.close();
    }

    /**
     * 遍历获取键值对
     */
    public static void method01(Properties prop) {
        //  修改元素
        prop.setProperty("fly", "orc");
        //  遍历集合,类似keySet
        Set<String> names = prop.stringPropertyNames();
        for (String name : names) {
            //  根据key获取value
            String value = prop.getProperty(name);
            System.out.println(name+"....."+value);
        }
    }
}
