package reflect;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ReflectTest {
    public static void main(String[] args) throws IOException, Exception {
        /**
         * 案例:电脑开始只有主板,主板提供接口,后面加上声卡和网卡等功能
         */
        
        MainBoard mb = new MainBoard();
        mb.run();
        
        // 创建属性集合
        Properties prop = new Properties();
        
        // 封装File对象
        File file = new File("E:// workspace/Java/src/reflect/pci.properties");
        
        // 输入流关联file文件
        FileInputStream fis = new FileInputStream(file);
        
        // 属性集合加载输入流
        prop.load(fis);
        
        // 遍历集合
        for (int i = 0; i < prop.size(); i++) {
            // 获取类名
            String name = prop.getProperty("pci"+(i+1));
            // 通过反射加载
            Class clazz = Class.forName(name);
            // 获取该类实例
            PCI p = (PCI) clazz.newInstance();
            // 主板使用接口功能
            mb.usePci(p);
        }
        
    }
}