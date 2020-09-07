package basic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexDemo {
    public static void main(String[] args) {
        /**
         * 正则表达式操作字符串：匹配、查找、替换
         *
         * * : 零次或多次,等同于{0,}
         * ? : 零次或一次,等同于{0,1}
         * + : 一次或多次,等同于{1,}
         * ^ : 字符串开头,如果在[]内表示取反
         * $ : 字符串结尾
         * . : 匹配除\n以外的任意单个字符
         * [] : 匹配内容
         * {} : 限定次数  {n}刚好n次  {n,}至少n次  {n,m}至少n次至多m次
         * () : 子表达式  组: ((A)(B(C))) \1表示匹配到的第一个子串,\2表示匹配到的第二个子串
         * \ : 转义下一个字符,在字符串里要写双斜杠\\
         *
         * \d : 匹配任意数字,等同于[0-9]
         * \D : 匹配任意非数字,等同于[^0-9]
         * \w : 匹配任意字符,等同于[a-zA-Z0-9_]
         * \W : 匹配任意非字符,等同于[^a-zA-Z0-9_]
         * \s : 匹配任意空白字符,等同于[\t\n\r\f]
         * \S : 匹配任意非空字符,等同于[^\t\n\r\f]
         * \b : 匹配任意边界,例如：er\b与never中er匹配,但与verb中er不匹配
         * \B : 匹配任意非边界
         *
         * 在线正则表达式: http://tool.oschina.net/regex/
         * 参考文档：http://www.runoob.com/python3/python3-reg-expressions.html
         */

        // 将正则封装成对象
        Pattern p1 = Pattern.compile("1[3,5,8]\\d{9}");
        Pattern p2 = Pattern.compile("([a-z]+) ([a-z]+)");
        // 匹配字符串获取匹配器
//        Matcher m = p1.matcher("32893219731923712");
        Matcher m = p2.matcher("hello java hello python");
        while (m.find()){
            // 获取匹配的子序列
            System.out.println(m.group());
            // 获取初始索引和结尾索引
            System.out.println(m.start() +"~"+ m.end());
        }
        // 屏蔽电话号码中间四位数
        String str = "13818427154";
        str = str.replaceAll("(\\d{3})\\d{4}(\\d{3})", "$1****$2");
        System.out.println(str);  // 138****7154

    }
}
