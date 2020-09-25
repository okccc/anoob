package j2se;

public class SingleDemo {
    public static void main(String[] args) {
        /*
         * 单例模式：保证类在内存中对象的唯一性
         * 1.在该类使用new创建一个本类对象
         * 2.私有化构造函数不允许其它程序创建对象
         * 3.对外提供静态get方法让其它程序可以获取该对象
         *
         * Arrays/Collections/Math/System等工具类将构造函数私有化,类中全部是静态方法,类名直接调用
         * Runtime类将构造函数私有化,对外提供getRuntime()方法获取单例对象,访问类中的非静态方法
         */

        Single01 s1 = Single01.getInstance();
        Single01 s2 = Single01.getInstance();
        System.out.println(s1==s2);  // 结果是true说明是同一个对象

        Single02 s3 = Single02.getInstance();
        Single02 s4 = Single02.getInstance();
        System.out.println(s3==s4);  // 结果是true说明是同一个对象

        SingleTest t1 = SingleTest.getInstance();
        SingleTest t2 = SingleTest.getInstance();
        t1.setNum(10);
        t2.setNum(20);
        System.out.println(t1.getNum() + " " + t2.getNum());  //  结果都是20,说明t1和t2是同一个对象
    }
}


class Single01 {
    // 饿汉式：类一加载,对象就已经创建好
    private static final Single01 s = new Single01();
    private Single01(){}
    static Single01 getInstance(){
        return s;
    }
}


class Single02 {
    // 懒汉式：类加载时没有对象,要用的时候才创建(延迟加载)
    private static Single02 s = null;
    private Single02(){}
    static Single02 getInstance(){
        // 外面套一层if可以提高效率,先判断对象是否存在,存在就直接返回,不用每次进来都判断锁
        if(s==null){
            // 同步代码块保证线程安全,由于该方法是静态方法不能使用this,可通过反射获取对象
            synchronized (Single02.class) {
                if(s==null){
                    s = new Single02();
                }
            }
        }
        return s;
    }
}


class SingleTest {
    private int num;
    private static final SingleTest t = new SingleTest();
    private SingleTest(){}
    static SingleTest getInstance(){
        return t;
    }
    void setNum(int num){
        this.num = num;
    }
    int getNum(){
        return num;
    }
}

