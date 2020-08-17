package orientedObject02;

public class ExtendsDemo01 {
    public static void main(String[] args) {
        /*
         * 继承好处:1、提高代码复用性
         *        2、让类与类产生关系,是多态的前提
         *        
         * java支持单继承,不支持直接多继承,但是支持多重(层)继承
         *        单继承:一个子类只继承一个父类
         *        多继承:一个子类可以直接有多个父类(如果多个父类中有相同成员,会出现调用的不确定性,所以java不允许)
         *              在java中是通过"多实现"来体现的
         *              
         * 当要使用一个继承体系时:查看顶层类,了解该体系最基本的功能,创建体系中的最子类对象,完善功能
         * 使用:当类与类之间存在所属关系时,可以定义继承,子父类是is a关系
         */
        Zi01 z = new Zi01();
        z.speak();
        z.show();
        
        NewPhone np = new NewPhone();
        np.show();
        np.call();
    }
}

// 子父类中的成员变量和成员方法
class Fu01{
    int num = 5;
    
    public void show(){
        System.out.println("这是父类成员方法");
    }
}

class Zi01 extends Fu01{
    int num = 6;
    
    /*
     * 当本类中的成员变量和局部变量同名时,用this关键字区分；this代表一个本类对象的引用
     * 当子父类中成员变量同名时,用super关键字区分；super代表一个父类空间(父类是不需要创建对象的)
     */
    public void speak(){
        System.out.println(num);
        System.out.println(this.num+"..."+super.num);
    }
    
    /*
     * 当子父类中出现一模一样的函数时,会运行子类中的函数,称为覆盖操作,也叫方法重写；子类中没有的函数,才会再去父类中找
     * 
     * 函数两个特征:1、重载(overload)：同一个类中
     *           2、重写(overwrite)：子类覆盖父类
     *            
     * 覆盖注意事项:1、子类权限必须大于等于父类权限,才能覆盖父类方法
     *           2、静态只能覆盖静态,或被静态覆盖
     */
    public void show(){
        System.out.println("这是子类成员方法");
    }
}

// 啥时候使用覆盖操作呢？
class Phone{
    
    void call(){
        System.out.println("我能打电话");
    }
    
    void show(){
        System.out.println("显示number");
    }
}

class NewPhone extends Phone{
    // 当对一个类进行子类的扩展时,如果要保留父类原有功能,同时增加子类特有功能时,可以用方法重写
    void show(){
        System.out.println("显示name");
        System.out.println("显示picture");
        super.show();
    }
}