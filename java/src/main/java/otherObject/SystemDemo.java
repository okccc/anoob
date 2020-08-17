package otherObject;

import java.util.Properties;
import java.util.Set;

public class SystemDemo {

    private static final String line_separator = System.getProperty("line.seperator");
	
    public static void main(String[] args) {
        /**
         * System:该类没有构造函数,无法创建对象,所以都是静态方法,类名直接调用
         * 
         * 当一个类没有构造函数时:
         * 1、都是静态方法(System,Math,Arrays,Collections)
         * 2、单例设计模式(Runtime)
         * 3、有静态方法返回该类对象(InetAddress)
         */
    	
    	// 1、获取当前时间毫秒值
    	long l1 = System.currentTimeMillis();
    	System.out.println(l1);
    	
    	// 2、获取系统属性信息,以键值对形式存储在Properties集合中
    	Properties prop = System.getProperties();
    	// 返回属性列表的key的集合
    	Set<String> propertyNames = prop.stringPropertyNames();
    	// 遍历key集合
    	for (String key : propertyNames) {
    		String value = prop.getProperty(key);
    		// 打印键值对
    		System.out.println(key+"....."+value);
		}
    	
    	
    	System.out.println("hello"+line_separator+"java");
    }
}
