package orientedObject01;

public class MainDemo {
    public static void main(String[] args) {
        /*
         * public：权限必须是最大的
         * static：JVM调用主函数是不需要对象的,直接用主函数所属类名调用即可
         * void：主函数没有具体返回值
         * main：jvm识别的固定名字
         * String[] args：主函数的参数列表,数组类型的参数,里面的元素都是字符串类型,args是arguments
         */
        System.out.println(args);           // [Ljava.lang.String;@3781efb9
        System.out.println(args.length);    // 0
        System.out.println(args[0]);        // java.lang.ArrayIndexOutOfBoundsException: 0
    }
}
