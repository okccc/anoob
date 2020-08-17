package orientedObject02;

public class FinalDemo {

    public static void main(String[] args) {
        /* 
         * 继承弊端：覆盖可能会破坏封装性
         * final关键字：final是一个修饰符,可以修饰类、方法、变量
         *            final修饰的类不能被继承
         *            final修饰的方法不能被覆盖
         *            final修饰的变量是一个常量,通常加static修饰  ---->写法规范：常量所有字母大写,多个单词用_连接
         */
    }

}

class Final{
    public static final int NUMBER = 1;
    public static final double MY_PI = 3.14;
}
