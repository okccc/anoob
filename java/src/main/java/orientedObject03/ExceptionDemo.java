package orientedObject03;

public class ExceptionDemo {
	
    public static void main(String[] args) {
        
        /* 
         * 异常就是java通过面向对象的思想将问题封装成了对象
         * 
         * 编译时异常：
         * 运行时异常：
         * 
         * try{}catch(){}finally{}
         * 
         * throw和throws区别
         * 
         * 
         * final、finally、finalize区别
         * final：修饰符,用来修饰成员；修饰类,类不能被继承,修饰方法,方法不能被重写,修饰变量,变量变常量
         * finally：异常处理的一部分,用来释放资源；常见于IO流和数据库连接；正常都会执行,除非JVM退出
         * finalize：是Object类的一个方法,用于垃圾回收
         * 
         * 异常注意事项： 1、子类重写父类方法时,父类方法有异常,子类方法必须抛出相同的异常或父类异常的子类
         *           2、子类重写父类方法时,父类方法没有异常,子类方法也不能抛出异常,只能try/catch处理
         */
        
        Exception e = new Exception();
        e.getInt();
}
	
}

class Exception{
	
    public int getInt(){
        int a = 10;
        try {
            int b = a/0;
            System.out.println(b);
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
        return 0;
}
}

