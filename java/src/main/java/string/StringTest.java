package string;

// String操作练习
public class StringTest {
    public static void main(String[] args) {
        /* 
         * 1、给定一个字符串数组。按照字典顺序进行从小到大的排序。
         * {"nba","abc","cba","zz","qq","haha"}
         * 
         * 2、一个子串在整串中出现的次数。
         * "nbaernbatynbauinbaopnba"
         * 
         * 3、两个字符串中最大相同的子串。
         * "qwerabcdtyuiop"
         * "xcabcdvbn"
         * 
         * 4、模拟一个trim功能一致的方法。去除字符串两端的空白 
         */
    	
    	String s1 = "java";                        // 引用变量s1指向字符串常量池的java
    	String s2 = "python";                      // 引用变量s2指向字符串常量池的python
    	System.out.println(s1+"....."+s2);
    	show(s1,s2);                               // show方法运行完后就弹栈了
    	System.out.println(s1+"....."+s2);         // 引用变量s1、s2仍然指向字符串常量池里的字符串
    	
    }

	private static void show(String haha, String hehe) {
	    hehe = hehe.replace("p", "s");
            haha = hehe;
            System.out.println(haha+"....."+hehe);
	}
   
}
