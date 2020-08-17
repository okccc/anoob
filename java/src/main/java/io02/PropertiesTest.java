package io02;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class PropertiesTest {
    public static void main(String[] args) throws IOException {
        /**
         * 需求:定义功能,获取应用程序运行次数,超过5次就给出提示先去注册再使用,并终止程序
         * 
         * 分析:首先要有计数器count,运行一次就+1,但是计数器是一个变量,存储在内存中,每次程序运行参与运算,程序结束后消失,下次重启程序后又重新初始化,
         *     所以要延长计数器的生命周期,从内存持久化到磁盘,计数器值会变化,是个映射关系(Map),又要从磁盘读数据(IO),所以用Properties存储合适
         *     
         * 步骤:1、将配置文件封装成File对象,并与输入、输出流关联
         *     2、创建Properties属性集合,从输入流load()数据
         *     3、对计数器count做判断和次数增加,并store()结果
         */
        
        getAPPCount();
    }

    private static void getAPPCount() throws IOException {
        //  封装配置文件
        File file = new File("E://  workspace/Java/src/io02/count.properties");
        //  判断文件是否存在,不存在就创建
        if(!file.exists()){
            file.createNewFile();
        }
        //  关联输入流
        FileReader fr = new FileReader(file);
        //  创建属性集合
        Properties prop = new Properties();
        //  加载数据
        prop.load(fr);
        //  判断次数
        int count = 0;
        String value = prop.getProperty("time");
        if(value != null){
            count= Integer.parseInt(value);
            count++;
            if(count>5){
                throw new RuntimeException("免费次数已达上限,请注册后继续使用！");
            }
        }
        prop.setProperty("time", count+"");
        //  写回数据
        FileWriter fw = new FileWriter(file);
        prop.store(fw, "modify");
        //  关闭流
        fr.close();
        fw.close();
    }
}
