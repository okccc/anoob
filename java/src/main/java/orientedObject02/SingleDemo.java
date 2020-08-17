package orientedObject02;

public class SingleDemo {
    public static void main(String[] args) {
        /*
         * 单例模式：保证类在内存中对象的唯一性
         * 1、在该类使用new创建一个本类对象
         * 2、私有化构造函数不允许其它程序用new创建对象
         * 3、对外提供静态get方法让其它程序可以获取本类对象
         */

        Single s1 = Single.getInstance();
        Single s2 = Single.getInstance();
        System.out.println(s1==s2);  // 结果是true说明是同一个对象

        Test t1 = Test.getTest();
        Test t2 = Test.getTest();
        t1.setNum(10);
        t2.setNum(20);
        System.out.println(t1.getNum() + " " + t2.getNum());  //  两个结果都是20,说明t1和t2是同一个对象
    }
}

class Single{
    // 饿汉式：类一加载,对象就已经创建好
    private static Single s = new Single();
    private Single(){

    }
    static Single getInstance(){
        return s;
    }
}

class Single2{
    // 懒汉式：类加载时没有对象,要用的时候才创建（延迟加载）
    private static Single2 s = null;
    private Single2(){

    }
    static Single2 getInstance(){
        // 先判断对象是否存在,提高效率,不用每次进来都判断锁
        if(s==null){
            // 多线程操作会有安全问题,加同步锁
            synchronized (Single2.class) {
                //  封装要同步的代码块
                if(s==null){
                    s = new Single2();
                }
            }
        }
        return s;
    }
}

class Test{
    private int num;
    private static Test t = new Test();
    private Test(){

    }
    static Test getTest(){
        return t;
    }
    void setNum(int num){
        this.num = num;
    }
    int getNum(){
        return num;
    }
}

