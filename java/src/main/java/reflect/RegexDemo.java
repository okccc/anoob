package reflect;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexDemo {
    public static void main(String[] args) {
        /**
         * 正则表达式 :用于操作字符串
         * 
         * *:零次或多次,等同于{0,}
         * ?:零次或一次,等同于{0,1}
         * +:一次或多次,等同于{1,}
         * ^:字符串开头,如果在[]内表示取反
         * $:字符串结尾
         * .:匹配除\n以外的任意单个字符
         * []:匹配内容
         * {}:限定次数
         * ():子表达式
         * \:转义下一个字符,在字符串里要写双斜杠\\
         * 
         * \d:数字,等同于[0-9]
         * \D:非数字,等同于[^0-9]
         * \w:字符,等同于[a-zA-Z0-9_]
         * \W:非字符,等同于[^a-zA-Z0-9_]
         * \b:边界,例如：er\b与never中er匹配,但与verb中er不匹配 
         * \B:非边界
         * 
         * {n}:刚好n次
         * {n,}:至少n次
         * {n,m}:至少n次至多m次
         * 
         * 组:((A)(B(C)))
         * 
         * 在线正则表达式:http:// tool.oschina.net/regex/
         * 
         * 
         * 1、匹配:matches(String regexx)
         * 2、替换:replaceAll(String regex,String replacement)
         * 3、切割:split(String regex)
         * 4、获取:Pattern.compile(regex).matcher(str)
         */
        
        matches();
        replaceAll();
        split();
        get();
        
    }

    // 匹配功能
    private static void matches() {
        
        // 匹配手机号码是否正确
        String str = "13818427154";
        String regex = "1[3,5,8]\\d{9}";
        boolean b = str.matches(regex);
        System.out.println(b);
    }
    
    // 替换功能
    private static void replaceAll() {
        
        // 屏蔽电话号码中间四位数
        String str = "13818427154";
        str = str.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
        System.out.println(str);
        
        // 叠字去重
        String str2 = "zhangsanttttxiaoqiangmmmmmmzhaoliu";
        str2 = str2.replaceAll("(.)\\1+", "$1");
        System.out.println(str2);
    }
    
    
    // 切割功能
    private static void split() {
        String str = "zhangsanttttxiaoqiangmmmmmmzhaoliu";
        String[] names = str.split("(.)\\1+");
        for(String name : names){
                System.out.println(name);
        }
    }
    
    // 获取功能
    private static void get() {
        
        // 获取长度为3的单词
        String str = "da jia hao ming tian bu shang ban";
        String regex = "\\b[a-z]{3}\\b";
        
        // 将正则封装成对象
        Pattern p = Pattern.compile(regex);
        
        // 匹配字符串获取匹配器
        Matcher m = p.matcher(str);
        
        // 调用匹配器find方法
        while(m.find()){
            // 获取匹配的子序列
            System.out.println(m.group());
            // 获取初始索引和结尾索引
            System.out.println(m.start()+"~"+m.end());
        }
    }
}
